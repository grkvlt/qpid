/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.txn;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.NoConsumersException;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Apache Software Foundation
 */
public class NonTransactionalContext implements TransactionalContext
{
    private static final Logger _log = Logger.getLogger(NonTransactionalContext.class);

    /**
     * Channel is useful for logging
     */
    private AMQChannel _channel;

    /**
     * Where to put undeliverable messages
     */
    private List<RequiredDeliveryException> _returnMessages;

    public NonTransactionalContext(AMQChannel channel, List<RequiredDeliveryException> returnMessages)
    {
        _channel = channel;
        _returnMessages = returnMessages;
    }

    public void commit() throws AMQException
    {
        // Does not apply to this context
    }

    public void rollback() throws AMQException
    {
        // Does not apply to this context
    }

    public void deliver(AMQMessage message, AMQQueue queue) throws AMQException
    {
        try
        {
            queue.process(message);
            //following check implements the functionality
            //required by the 'immediate' flag:
            message.checkDeliveredToConsumer();
        }
        catch (NoConsumersException e)
        {
            _returnMessages.add(e);
        }
        finally
        {
            message.decrementReference();
        }
    }

    public void acknowledgeMessage(final long deliveryTag, long lastDeliveryTag,
                                   boolean multiple, final UnacknowledgedMessageMap unacknowledgedMessageMap)
            throws AMQException
    {
        if (multiple)
        {
            if (deliveryTag == 0)
            {

                //Spec 2.1.6.11 ... If the multiple field is 1, and the delivery tag is zero,
                // tells the server to acknowledge all outstanding mesages.
                _log.info("Multiple ack on delivery tag 0. ACKing all messages. Current count:" +
                        unacknowledgedMessageMap.size());
                unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
                {
                    public boolean callback(UnacknowledgedMessage message) throws AMQException
                    {
                        message.discard();
                        return false;
                    }

                    public void visitComplete()
                    {
                        unacknowledgedMessageMap.clear();
                    }
                });
            }
            else
            {
                if (!unacknowledgedMessageMap.contains(deliveryTag))
                {
                    throw new AMQException("Multiple ack on delivery tag " + deliveryTag + " not known for channel");
                }

                LinkedList<UnacknowledgedMessage> acked = new LinkedList<UnacknowledgedMessage>();
                unacknowledgedMessageMap.drainTo(acked, deliveryTag);
                for (UnacknowledgedMessage msg : acked)
                {
                    msg.discard();
                }
            }
        }
        else
        {
            UnacknowledgedMessage msg;
            msg = unacknowledgedMessageMap.remove(deliveryTag);

            if (msg == null)
            {
                _log.info("Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                          _channel.getChannelId());
                throw new AMQException("Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                                       _channel.getChannelId());
            }
            msg.discard();
            if (_log.isDebugEnabled())
            {
                _log.debug("Received non-multiple ack for messaging with delivery tag " + deliveryTag);
            }
        }
    }
}
