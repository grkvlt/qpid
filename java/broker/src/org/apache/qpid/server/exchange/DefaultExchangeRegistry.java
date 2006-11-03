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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.protocol.ExchangeInitialiser;
import org.apache.qpid.server.queue.AMQMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultExchangeRegistry implements ExchangeRegistry
{
    private static final Logger _log = Logger.getLogger(DefaultExchangeRegistry.class);

    /**
     * Maps from exchange name to exchange instance
     */
    private ConcurrentMap<String, Exchange> _exchangeMap = new ConcurrentHashMap<String, Exchange>();

    public DefaultExchangeRegistry(ExchangeFactory exchangeFactory)
    {
        //create 'standard' exchanges:
        try
        {
            new ExchangeInitialiser().initialise(exchangeFactory, this);
        }
        catch(AMQException e)
        {
            _log.error("Failed to initialise exchanges: ", e);
        }
    }

    public void registerExchange(Exchange exchange)
    {
        _exchangeMap.put(exchange.getName(), exchange);
    }

    public void unregisterExchange(String name, boolean inUse) throws AMQException
    {
        // TODO: check inUse argument
        Exchange e = _exchangeMap.remove(name);
        if (e != null)
        {
            e.close();
        }
        else
        {
            throw new AMQException("Unknown exchange " + name);
        }
    }

    public Exchange getExchange(String name)
    {
        return _exchangeMap.get(name);
    }

    /**
     * Routes content through exchanges, delivering it to 1 or more queues.
     * @param payload
     * @throws AMQException if something goes wrong delivering data
     */
    public void routeContent(AMQMessage payload) throws AMQException
    {
        final String exchange = payload.getPublishBody().exchange;
        final Exchange exch = _exchangeMap.get(exchange);
        // there is a small window of opportunity for the exchange to be deleted in between
        // the BasicPublish being received (where the exchange is validated) and the final
        // content body being received (which triggers this method)
        // TODO: check where the exchange is validated
        if (exch == null)
        {
            throw new AMQException("Exchange '" + exchange + "' does not exist");
        }
        exch.route(payload);
    }
}
