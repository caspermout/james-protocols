/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.protocols.smtp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.StringTokenizer;

import org.apache.james.protocols.api.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.mailet.MailAddress;

/**
 * Handles RCPT command
 */
public class RcptCmdHandler extends AbstractHookableCmdHandler<RcptHook> implements
        CommandHandler<SMTPSession> {

    public static final String CURRENT_RECIPIENT = "CURRENT_RECIPIENT"; // Current recipient

   
    
    /**
     * Handler method called upon receipt of a RCPT command. Reads recipient.
     * Does some connection validation.
     * 
     * 
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    @SuppressWarnings("unchecked")
    protected SMTPResponse doCoreCmd(SMTPSession session, String command,
            String parameters) {
        Collection<MailAddress> rcptColl = (Collection<MailAddress>) session.getState().get(
                SMTPSession.RCPT_LIST);
        if (rcptColl == null) {
            rcptColl = new ArrayList<MailAddress>();
        }
        MailAddress recipientAddress = (MailAddress) session.getState().get(
                CURRENT_RECIPIENT);
        rcptColl.add(recipientAddress);
        session.getState().put(SMTPSession.RCPT_LIST, rcptColl);
        StringBuilder response = new StringBuilder();
        response
                .append(
                        DSNStatus.getStatus(DSNStatus.SUCCESS,
                                DSNStatus.ADDRESS_VALID))
                .append(" Recipient <").append(recipientAddress).append("> OK");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, response);

    }

    /**
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    protected SMTPResponse doFilterChecks(SMTPSession session, String command,
            String argument) {
        String recipient = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            recipient = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (!session.getState().containsKey(SMTPSession.SENDER)) {
            return new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus
                    .getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER)
                    + " Need MAIL before RCPT");
        } else if (argument == null
                || !argument.toUpperCase(Locale.US).equals("TO")
                || recipient == null) {
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
                    DSNStatus.getStatus(DSNStatus.PERMANENT,
                            DSNStatus.DELIVERY_SYNTAX)
                            + " Usage: RCPT TO:<recipient>");
        }

        recipient = recipient.trim();
        int lastChar = recipient.lastIndexOf('>');
        // Check to see if any options are present and, if so, whether they
        // are correctly formatted
        // (separated from the closing angle bracket by a ' ').
        String rcptOptionString = null;
        if ((lastChar > 0) && (recipient.length() > lastChar + 2)
                && (recipient.charAt(lastChar + 1) == ' ')) {
            rcptOptionString = recipient.substring(lastChar + 2);

            // Remove the options from the recipient
            recipient = recipient.substring(0, lastChar + 1);
        }
        if (session.useAddressBracketsEnforcement()
                && (!recipient.startsWith("<") || !recipient.endsWith(">"))) {
            if (session.getLogger().isInfoEnabled()) {
                StringBuilder errorBuffer = new StringBuilder(192).append(
                        "Error parsing recipient address: ").append(
                        "Address did not start and end with < >").append(
                        getContext(session, null, recipient));
                session.getLogger().info(errorBuffer.toString());
            }
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
                    DSNStatus.getStatus(DSNStatus.PERMANENT,
                            DSNStatus.DELIVERY_SYNTAX)
                            + " Syntax error in parameters or arguments");
        }
        MailAddress recipientAddress = null;
        // Remove < and >
        if (session.useAddressBracketsEnforcement()
                || (recipient.startsWith("<") && recipient.endsWith(">"))) {
            recipient = recipient.substring(1, recipient.length() - 1);
        }

        if (recipient.indexOf("@") < 0) {
            // set the default domain
            recipient = recipient
                    + "@"
                    + getDefaultDomain();
        }

        try {
            recipientAddress = new MailAddress(recipient);
        } catch (Exception pe) {
            if (session.getLogger().isInfoEnabled()) {
                StringBuilder errorBuffer = new StringBuilder(192).append(
                        "Error parsing recipient address: ").append(
                        getContext(session, recipientAddress, recipient))
                        .append(pe.getMessage());
                session.getLogger().info(errorBuffer.toString());
            }
            /*
             * from RFC2822; 553 Requested action not taken: mailbox name
             * not allowed (e.g., mailbox syntax incorrect)
             */
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_MAILBOX,
                    DSNStatus.getStatus(DSNStatus.PERMANENT,
                            DSNStatus.ADDRESS_SYNTAX)
                            + " Syntax error in recipient address");
        }

        if (rcptOptionString != null) {

            StringTokenizer optionTokenizer = new StringTokenizer(
                    rcptOptionString, " ");
            while (optionTokenizer.hasMoreElements()) {
                String rcptOption = optionTokenizer.nextToken();
                int equalIndex = rcptOption.indexOf('=');
                String rcptOptionName = rcptOption;
                String rcptOptionValue = "";
                if (equalIndex > 0) {
                    rcptOptionName = rcptOption.substring(0, equalIndex)
                            .toUpperCase(Locale.US);
                    rcptOptionValue = rcptOption.substring(equalIndex + 1);
                }
                // Unexpected option attached to the RCPT command
                if (session.getLogger().isDebugEnabled()) {
                    StringBuilder debugBuffer = new StringBuilder(128)
                            .append(
                                    "RCPT command had unrecognized/unexpected option ")
                            .append(rcptOptionName).append(" with value ")
                            .append(rcptOptionValue).append(
                                    getContext(session, recipientAddress,
                                            recipient));
                    session.getLogger().debug(debugBuffer.toString());
                }

                return new SMTPResponse(
                        SMTPRetCode.PARAMETER_NOT_IMPLEMENTED,
                        "Unrecognized or unsupported option: "
                                + rcptOptionName);
            }
            optionTokenizer = null;
        }

        session.getState().put(CURRENT_RECIPIENT,recipientAddress);

        return null;
    }

    private String getContext(SMTPSession session,
            MailAddress recipientAddress, String recipient) {
        StringBuilder sb = new StringBuilder(128);
        if (null != recipientAddress) {
            sb
                    .append(" [to:"
                            + (recipientAddress).toInternetAddress()
                                    .getAddress() + "]");
        } else if (null != recipient) {
            sb.append(" [to:" + recipient + "]");
        }
        if (null != session.getState().get(SMTPSession.SENDER)) {
            sb
                    .append(" [from:"
                            + ((MailAddress) session.getState().get(
                                    SMTPSession.SENDER)).toInternetAddress()
                                    .getAddress() + "]");
        }
        return sb.toString();
    }

    /**
     * @see org.apache.james.smtpserver.protocol.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        Collection<String> implCommands = new ArrayList<String>();
        implCommands.add("RCPT");

        return implCommands;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#getHookInterface()
     */
    protected Class<RcptHook> getHookInterface() {
        return RcptHook.class;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#callHook(java.lang.Object,
     *      org.apache.james.protocols.smtp.SMTPSession, java.lang.String)
     */
    protected HookResult callHook(RcptHook rawHook, SMTPSession session,
            String parameters) {
        return rawHook.doRcpt(session,
                (MailAddress) session.getState().get(SMTPSession.SENDER),
                (MailAddress) session.getState().get(CURRENT_RECIPIENT));
    }

    protected String getDefaultDomain() {
    	return "localhost";
    }
}
