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
package org.apache.james.protocols.lmtp;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.core.DataCmdHandler;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.MailCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.RcptCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.SMTPCommandDispatcherLineHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;

public class LMTPProtocolHandlerChain extends SMTPProtocolHandlerChain{

    public LMTPProtocolHandlerChain() throws WiringException {
        super();
    }

    @Override
    protected List<Object> initDefaultHandlers() {
        List<Object> defaultHandlers = new ArrayList<Object>();
        defaultHandlers.add(new SMTPCommandDispatcherLineHandler());
        defaultHandlers.add(new ExpnCmdHandler());
        defaultHandlers.add(new LhloCmdHandler());
        defaultHandlers.add(new HelpCmdHandler());
        defaultHandlers.add(new MailCmdHandler());
        defaultHandlers.add(new NoopCmdHandler());
        defaultHandlers.add(new QuitCmdHandler());
        defaultHandlers.add(new RcptCmdHandler());
        defaultHandlers.add(new RsetCmdHandler());
        defaultHandlers.add(new VrfyCmdHandler());
        defaultHandlers.add(new DataCmdHandler());
        defaultHandlers.add(new MailSizeEsmtpExtension());
        defaultHandlers.add(new WelcomeMessageHandler());
        defaultHandlers.add(new ReceivedDataLineFilter());
        defaultHandlers.add(new LMTPDataLineMessageHookHandler());
        return defaultHandlers;
    }

}