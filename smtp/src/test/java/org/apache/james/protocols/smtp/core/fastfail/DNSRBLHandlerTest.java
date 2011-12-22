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


package org.apache.james.protocols.smtp.core.fastfail;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import junit.framework.TestCase;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.BaseFakeDNSService;
import org.apache.james.protocols.smtp.BaseFakeSMTPSession;
import org.apache.james.protocols.smtp.DNSService;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.fastfail.DNSRBLHandler;

public class DNSRBLHandlerTest extends TestCase {

    private DNSService mockedDnsServer;

    private SMTPSession mockedSMTPSession;

    private String remoteIp = "127.0.0.2";

    private boolean relaying = false;   
    
    public static final String RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.blocklisted";
    
    public static final String RBL_DETAIL_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.detail";

    protected void setUp() throws Exception {
        super.setUp();
        setupMockedDnsServer();
        setRelayingAllowed(false);
    }

    /**
     * Set the remoteIp
     * 
     * @param remoteIp The remoteIP to set
     */
    private void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    /**
     * Set relayingAllowed
     * 
     * @param relaying true or false
     */
    private void setRelayingAllowed(boolean relaying) {
        this.relaying = relaying;
    }

    /**
     * Setup the mocked dnsserver
     *
     */
    private void setupMockedDnsServer() {
        mockedDnsServer  = new BaseFakeDNSService() {

            public Collection<String> findTXTRecords(String hostname) {
                List<String> res = new ArrayList<String>();
                if (hostname == null) {
                    return res;
                }
                ;
                if ("2.0.0.127.bl.spamcop.net.".equals(hostname)) {
                    res.add("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
                }
                return res;
            }

            public InetAddress getByName(String host)
                    throws UnknownHostException {
                if ("2.0.0.127.bl.spamcop.net.".equals(host)) {
                    return InetAddress.getByName("127.0.0.1");
                } else if ("3.0.0.127.bl.spamcop.net.".equals(host)) {
                    return InetAddress.getByName("127.0.0.1");
                } else if ("1.0.168.192.bl.spamcop.net.".equals(host)) {
                    throw new UnknownHostException(host);
                }
                throw new UnsupportedOperationException("getByName("+host+") not implemented in DNSRBLHandlerTest mock");
            }
        };
        
    }

    /**
     * Setup mocked smtpsession
     */
    private void setupMockedSMTPSession(final MailAddress rcpt) {
        mockedSMTPSession = new BaseFakeSMTPSession() {
            HashMap<String,Object> sessionState = new HashMap<String,Object>();
            HashMap<String,Object> connectionState = new HashMap<String,Object>();
            
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(getRemoteIPAddress(), 10000);
            }

            public String getRemoteIPAddress() {
                return remoteIp;
            }

            public Map<String,Object> getState() {
                return sessionState;
            }

            public boolean isRelayingAllowed() {
                return relaying;
            }

            public boolean isAuthSupported() {
                return false;
            }

            public int getRcptCount() {
                return 0;
            }
            
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionState.remove(key);
                    } else {
                        return connectionState.put(key, value);
                    }
                } else {
                    if (value == null) {
                        return sessionState.remove(key);
                    } else {
                        return sessionState.put(key, value);
                    }
                }
            }

            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sessionState.get(key);
                }
            }

        };
    }

    // ip is blacklisted and has txt details
    public void testBlackListedTextPresent() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();
       
        setupMockedSMTPSession(new MailAddress("any@domain"));
        rbl.setDNSService(mockedDnsServer);

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertEquals("Details","Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2",
               mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNotNull("Blocked",mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }

    // ip is blacklisted and has txt details but we don'T want to retrieve the txt record
    public void testGetNoDetail() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();
        setupMockedSMTPSession(new MailAddress("any@domain"));
        rbl.setDNSService(mockedDnsServer);

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(false);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertNull("No details",mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNotNull("Blocked",mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }

    // ip is allowed to relay
    public void testRelayAllowed() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();
        setRelayingAllowed(true);
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setDNSService(mockedDnsServer);

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertNull("No details", mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNull("Not blocked", mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }

    // ip not on blacklist
    public void testNotBlackListed() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();

        setRemoteIp("192.168.0.1");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setDNSService(mockedDnsServer);

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertNull("No details", mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNull("Not blocked", mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }

    // ip on blacklist without txt details
    public void testBlackListedNoTxt() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();

        setRemoteIp("127.0.0.3");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setDNSService(mockedDnsServer);

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertNull(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNotNull("Blocked", mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }

    // ip on whitelist
    public void testWhiteListed() throws MailAddressException {
        DNSRBLHandler rbl = new DNSRBLHandler();

        setRemoteIp("127.0.0.2");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setDNSService(mockedDnsServer);

        rbl.setWhitelist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, null, new MailAddress("test@localhost"));
        assertNull(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection));
        assertNull("Not blocked", mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, State.Connection));
    }
   

}
