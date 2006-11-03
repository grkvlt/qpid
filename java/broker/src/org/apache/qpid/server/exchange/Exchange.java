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
package org.apache.qpid.server.exchange;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;

public interface Exchange
{
    String getName();

    void initialise(String name, boolean durable, int ticket, boolean autoDelete) throws AMQException;

    boolean isDurable();

    /**
     * @return true if the exchange will be deleted after all queues have been detached
     */
    boolean isAutoDelete();

    int getTicket();

    void close() throws AMQException;

    void registerQueue(String routingKey, AMQQueue queue, FieldTable args) throws AMQException;

    void deregisterQueue(String routingKey, AMQQueue queue) throws AMQException;

    void route(AMQMessage message) throws AMQException;
}
