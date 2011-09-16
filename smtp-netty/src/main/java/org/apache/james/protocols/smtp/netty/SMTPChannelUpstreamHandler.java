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
package org.apache.james.protocols.smtp.netty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.impl.AbstractChannelUpstreamHandler;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.slf4j.Logger;

/**
 * {@link ChannelUpstreamHandler} which is used by the SMTPServer
 */
@Sharable
public class SMTPChannelUpstreamHandler extends AbstractChannelUpstreamHandler {
    protected final Logger logger;
    protected final SMTPConfiguration conf;
    protected final SSLContext context;
    protected String[] enabledCipherSuites;

    public SMTPChannelUpstreamHandler(ProtocolHandlerChain chain, SMTPConfiguration conf, Logger logger) {
        this(chain, conf, logger, null, null);
    }

    public SMTPChannelUpstreamHandler(ProtocolHandlerChain chain, SMTPConfiguration conf, Logger logger, SSLContext context, String[] enabledCipherSuites) {
        super(chain);
        this.conf = conf;
        this.logger = logger;
        this.context = context;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    @Override
    protected ProtocolSession createSession(ChannelHandlerContext ctx) throws Exception {
        if (context != null) {
            SSLEngine engine = context.createSSLEngine();
            if (enabledCipherSuites != null && enabledCipherSuites.length > 0) {
                engine.setEnabledCipherSuites(enabledCipherSuites);
            }
            return new SMTPNettySession(conf, logger, ctx.getChannel(), engine);
        } else {
            return new SMTPNettySession(conf, logger, ctx.getChannel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        if (e.getCause() instanceof TooLongFrameException) {
            ctx.getChannel().write(new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Line length exceeded. See RFC 2821 #4.5.3.1."));
        } else {
            if (channel.isConnected()) {
                ctx.getChannel().write(new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unable to process request")).addListener(ChannelFutureListener.CLOSE);
            }
            SMTPSession smtpSession = (SMTPSession) ctx.getAttachment();
            if (smtpSession != null) {
                smtpSession.getLogger().debug("Unable to process request", e.getCause());
            } else {
                logger.debug("Unable to process request", e.getCause());
            }
            cleanup(ctx);            
        }
    }

}