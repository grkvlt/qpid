/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.security.access.config;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.security.Result;

@SuppressWarnings("unchecked")
public class PrincipalPermissions
{
    public enum Permission
    {
        CONSUME,
        PUBLISH,
        CREATEQUEUE,
        CREATEEXCHANGE,
        ACCESS,
        BIND,
        UNBIND,
        DELETE,
        PURGE
    }
    
    private static final Logger _logger = Logger.getLogger(PrincipalPermissions.class);
    
    private static final Object CONSUME_QUEUES_KEY = new Object();
    private static final Object CONSUME_TEMPORARY_KEY = new Object();
    private static final Object CONSUME_OWN_QUEUES_ONLY_KEY = new Object();

    private static final Object CREATE_QUEUES_KEY = new Object();
    private static final Object CREATE_EXCHANGES_KEY = new Object();


    private static final Object CREATE_QUEUE_TEMPORARY_KEY = new Object();
    private static final Object CREATE_QUEUE_QUEUES_KEY = new Object();
    private static final Object CREATE_QUEUE_EXCHANGES_KEY = new Object();

    private static final Object CREATE_QUEUE_EXCHANGES_ROUTINGKEYS_KEY = new Object();

    private static final int PUBLISH_EXCHANGES_KEY = 0;

    private Map _permissions;
    private boolean _fullVHostAccess = false;

    private String _user;


    public PrincipalPermissions(String user)
    {
        _user = user;
        _permissions = new ConcurrentHashMap();
    }

    /**
     *
     * @param permission the type of permission to check
     *
     * @param parameters vararg depending on what permission was passed in
     *  ACCESS: none
     *  BIND: none
     *  CONSUME: AMQShortString queueName, Boolean temporary, Boolean ownQueueOnly
     *  CREATEQUEUE:  Boolean temporary, AMQShortString queueName, AMQShortString exchangeName, AMQShortString routingKey
     *  CREATEEXCHANGE: AMQShortString exchangeName, AMQShortString Class
     *  DELETE: none
     *  PUBLISH: Exchange exchange, AMQShortString routingKey
     *  PURGE: none
     *  UNBIND: none
     */
    public void grant(Permission permission, Object... parameters)
    {
        switch (permission)
        {
            case ACCESS:// Parameters : None
                grantAccess(permission);
                break;
            case CONSUME: // Parameters : AMQShortString queueName, Boolean Temporary, Boolean ownQueueOnly
                grantConsume(permission, parameters);
                break;
            case CREATEQUEUE:  // Parameters : Boolean temporary, AMQShortString queueName
                // , AMQShortString exchangeName , AMQShortString routingKey
                grantCreateQueue(permission, parameters);
                break;
            case CREATEEXCHANGE:
                // Parameters AMQShortString exchangeName , AMQShortString Class
                grantCreateExchange(permission, parameters);
                break;
            case PUBLISH: // Parameters : Exchange exchange, AMQShortString routingKey
                grantPublish(permission, parameters);
                break;
            /* The other cases just fall through to no-op */
            case DELETE:
            case BIND: // All the details are currently included in the create setup.
            case PURGE:
            case UNBIND:
                break;
        }

    }

    private void grantAccess(Permission permission)
    {
        _fullVHostAccess = true;
    }

	private void grantPublish(Permission permission, Object... parameters) {
		Map publishRights = (Map) _permissions.get(permission);

		if (publishRights == null)
		{
		    publishRights = new ConcurrentHashMap();
		    _permissions.put(permission, publishRights);
		}

		if (parameters == null || parameters.length == 0)
		{
		    //If we have no parameters then allow publish to all destinations
		    // this is signified by having a null value for publish_exchanges
		}
		else
		{
		    Map publish_exchanges = (Map) publishRights.get(PUBLISH_EXCHANGES_KEY);

		    if (publish_exchanges == null)
		    {
		        publish_exchanges = new ConcurrentHashMap();
		        publishRights.put(PUBLISH_EXCHANGES_KEY, publish_exchanges);
		    }


		    HashSet routingKeys = (HashSet) publish_exchanges.get(parameters[0]);

		    // Check to see if we have a routing key
		    if (parameters.length == 2)
		    {
		        if (routingKeys == null)
		        {
		            routingKeys = new HashSet<AMQShortString>();
		        }
		        //Add routing key to permitted publish destinations
		        routingKeys.add(parameters[1]);
		    }

		    // Add the updated routingkey list or null if all values allowed
		    publish_exchanges.put(parameters[0], routingKeys);
		}
	}

	private void grantCreateExchange(Permission permission, Object... parameters) {
		Map rights = (Map) _permissions.get(permission);
		if (rights == null)
		{
		    rights = new ConcurrentHashMap();
		    _permissions.put(permission, rights);
		}

		Map create_exchanges = (Map) rights.get(CREATE_EXCHANGES_KEY);
		if (create_exchanges == null)
		{
		    create_exchanges = new ConcurrentHashMap();
		    rights.put(CREATE_EXCHANGES_KEY, create_exchanges);
		}

		//Should perhaps error if parameters[0] is null;
		AMQShortString name = parameters.length > 0 ? (AMQShortString) parameters[0] : null;
		AMQShortString className = parameters.length > 1 ? (AMQShortString) parameters[1] : new AMQShortString("direct");

		//Store the exchangeName / class mapping if the mapping is null
		rights.put(name, className);
	}

	private void grantCreateQueue(Permission permission, Object... parameters)
    {
        Map createRights = (Map) _permissions.get(permission);

        if (createRights == null)
        {
            createRights = new ConcurrentHashMap();
            _permissions.put(permission, createRights);
        }

        //The existence of the empty map mean permission to all.
        if (parameters.length == 0)
        {
            return;
        }

        // Get the queues map
        Map create_queues = (Map) createRights.get(CREATE_QUEUES_KEY);

        //Initialiase the queue permissions if not already done
        if (create_queues == null)
        {
            create_queues = new ConcurrentHashMap();
            //initialise temp queue permission to false and overwrite below if true
            create_queues.put(CREATE_QUEUE_TEMPORARY_KEY, false);
            createRights.put(CREATE_QUEUES_KEY, create_queues);
        }

         //Create empty list of queues
        Map create_queues_queues = (Map) create_queues.get(CREATE_QUEUE_QUEUES_KEY);

        if (create_queues_queues == null)
        {
            create_queues_queues = new ConcurrentHashMap();
            create_queues.put(CREATE_QUEUE_QUEUES_KEY, create_queues_queues);
        }

        // If we are initialising and granting CREATE rights to all temporary queues, then that's all we do
        Boolean temporary = false;
        if (parameters.length == 1)
        {
            temporary = (Boolean) parameters[0];
            create_queues.put(CREATE_QUEUE_TEMPORARY_KEY, temporary);
            return;
        }

        //From here we can be permissioning a variety of things, with varying parameters
        AMQShortString queueName = parameters.length > 1 ? (AMQShortString) parameters[1] : null;
        AMQShortString exchangeName = parameters.length > 2 ? (AMQShortString) parameters[2] : null;
        //Set the routingkey to the specified value or the queueName if present
        AMQShortString routingKey = (parameters.length > 3 && null != parameters[3]) ? (AMQShortString) parameters[3] : queueName;
        // if we have a queueName then we need to store any associated exchange / rk bindings
        if (queueName != null)
        {
            Map queue = (Map) create_queues_queues.get(queueName);
            if (queue == null)
            {
                queue = new ConcurrentHashMap();
                create_queues_queues.put(queueName, queue);
            }

            if (exchangeName != null)
            {
                queue.put(exchangeName, routingKey);
            }

            //If no exchange is specified then the presence of the queueName in the map says any exchange is ok
        }

        // Store the exchange that we are being granted rights to. This will be used as part of binding

        //Lookup the list of exchanges
        Map create_queues_exchanges = (Map) create_queues.get(CREATE_QUEUE_EXCHANGES_KEY);

        if (create_queues_exchanges == null)
        {
            create_queues_exchanges = new ConcurrentHashMap();
            create_queues.put(CREATE_QUEUE_EXCHANGES_KEY, create_queues_exchanges);
        }

        //if we have an exchange
        if (exchangeName != null)
        {
            //Retrieve the list of permitted exchanges.
            Map exchanges = (Map) create_queues_exchanges.get(exchangeName);

            if (exchanges == null)
            {
                exchanges = new ConcurrentHashMap();
                create_queues_exchanges.put(exchangeName, exchanges);
            }

            //Store the binding details of queue/rk for this exchange.
            if (queueName != null)
            {
                //Retrieve the list of permitted routingKeys.
                Map rKeys = (Map) exchanges.get(exchangeName);

                if (rKeys == null)
                {
                    rKeys = new ConcurrentHashMap();
                    exchanges.put(CREATE_QUEUE_EXCHANGES_ROUTINGKEYS_KEY, rKeys);
                }

                rKeys.put(queueName, routingKey);
            }
        }
    }

    /**
     * Grant consume permissions
     */
    private void grantConsume(Permission permission, Object... parameters)
    {
        Map consumeRights = (Map) _permissions.get(permission);

        if (consumeRights == null)
        {
           consumeRights = new ConcurrentHashMap();
           _permissions.put(permission, consumeRights);

           //initialise own and temporary rights to false to be overwritten below if set
           consumeRights.put(CONSUME_TEMPORARY_KEY, false);
           consumeRights.put(CONSUME_OWN_QUEUES_ONLY_KEY, false);
        }


        //if we only have one param then we're permissioning temporary queues and topics
        if (parameters.length == 1)
        {
           Boolean temporary = (Boolean) parameters[0];

           if (temporary)
           {
               consumeRights.put(CONSUME_TEMPORARY_KEY, true);
           }
        }

        //if we have 2 parameters - should be a contract for this, but for now we'll handle it as is
        if (parameters.length == 2)
        {
           AMQShortString queueName = (AMQShortString) parameters[0];
           Boolean ownQueueOnly = (Boolean) parameters[1];

           if (ownQueueOnly)
           {
               consumeRights.put(CONSUME_OWN_QUEUES_ONLY_KEY, true);
           }

           LinkedList queues = (LinkedList) consumeRights.get(CONSUME_QUEUES_KEY);
           if (queues == null)
           {
               queues = new LinkedList();
               consumeRights.put(CONSUME_QUEUES_KEY, queues);
           }

           if (queueName != null)
           {
               queues.add(queueName);
           }
        }
    }

    /**
     *
     * @param permission the type of permission to check
     *
     * @param parameters vararg depending on what permission was passed in
     *  ACCESS: none
     *  BIND: QueueBindBody bindmethod, Exchange exchange, AMQQueue queue, AMQShortString routingKey
     *  CONSUME: AMQQueue queue
     *  CREATEQUEUE:  Boolean autodelete, AMQShortString name
     *  CREATEEXCHANGE: AMQShortString exchangeName
     *  DELETE: none
     *  PUBLISH: Exchange exchange, AMQShortString routingKey
     *  PURGE: none
     *  UNBIND: none
     */
    public Result authorise(Permission permission, String... parameters)
    {

        switch (permission)
        {
            case ACCESS://No Parameters
                return Result.ALLOWED; // The existence of this user-specific PP infers some level of access is authorised
            case BIND: // Parameters : QueueBindMethod , exhangeName , queueName, routingKey
                return authoriseBind(parameters);
            case CREATEQUEUE:// Parameters : autoDelete, queueName
                return authoriseCreateQueue(permission, parameters);
            case CREATEEXCHANGE: //Parameters: exchangeName
                return authoriseCreateExchange(permission, parameters);
            case CONSUME: // Parameters :  queueName, autoDelete, owner
                return authoriseConsume(permission, parameters);
            case PUBLISH: // Parameters : exchangeName, routingKey
                return authorisePublish(permission, parameters);
            /* Fall through */
            case DELETE:
            case PURGE:
            case UNBIND:
            default:
                if(_fullVHostAccess)
                {
                    //user has been granted full access to the vhost
                    return Result.ALLOWED;
                }
                else
                {
                    //SimpleXML ACL does not implement these permissions and should abstain
                    return Result.ABSTAIN;
                }
        }

    }

	private Result authoriseConsume(Permission permission, String... parameters)
	{
	    if(_fullVHostAccess)
	    {
	        //user has been granted full access to the vhost
	        return Result.ALLOWED;
	    }

        if (parameters.length == 3)
        {
            AMQShortString queueName = new AMQShortString(parameters[0]);
            Boolean autoDelete = Boolean.valueOf(parameters[1]);
            AMQShortString owner = new AMQShortString(parameters[2]);
            Map queuePermissions = (Map) _permissions.get(permission);

            _logger.error("auth consume on " + StringUtils.join(parameters, ", "));
            
            if (queuePermissions == null)
            {
                //we have a problem - we've never granted this type of permission .....
                return Result.DENIED;
            }

            List queues = (List) queuePermissions.get(CONSUME_QUEUES_KEY);

            Boolean temporaryQueues = (Boolean) queuePermissions.get(CONSUME_TEMPORARY_KEY);
            Boolean ownQueuesOnly = (Boolean) queuePermissions.get(CONSUME_OWN_QUEUES_ONLY_KEY);


            // If user is allowed to consume from temporary queues and this is a temp queue then allow it.
            if (temporaryQueues && autoDelete)
            {
                // This will allow consumption from any temporary queue including ones not owned by this user.
                // Of course the exclusivity will not be broken.
                {

                    // if not limited to ownQueuesOnly then ok else check queue Owner.
                    return (!ownQueuesOnly || owner.equals(_user)) ? Result.ALLOWED : Result.DENIED;
                }
            }
            //if this is a temporary queue and the user does not have permissions for temporary queues then deny
            else if (!temporaryQueues && autoDelete)
            {
                return Result.DENIED;
            }

            // if queues are white listed then ensure it is ok
            if (queues != null)
            {
                // if no queues are listed then ALL are ok othereise it must be specified.
                if (ownQueuesOnly)
                {
                    if (owner.equals(_user))
                    {
                        return (queues.size() == 0 || queues.contains(queueName)) ? Result.ALLOWED : Result.DENIED;
                    }
                    else
                    {
                        return Result.DENIED;
                    }
                }

                // If we are
                return (queues.size() == 0 || queues.contains(queueName)) ? Result.ALLOWED : Result.DENIED;
            }
        }

        // Can't authenticate without the right parameters
        return Result.DENIED;
	}

	private Result authorisePublish(Permission permission, String... parameters)
	{
	    if(_fullVHostAccess)
	    {
	        //user has been granted full access to the vhost
	        return Result.ALLOWED;
	    }

	    Map publishRights = (Map) _permissions.get(permission);

		if (publishRights == null)
		{
		    return Result.DENIED;
		}

		Map exchanges = (Map) publishRights.get(PUBLISH_EXCHANGES_KEY);

		// Having no exchanges listed gives full publish rights to all exchanges
		if (exchanges == null)
		{
		    return Result.ALLOWED;
		}
		// Otherwise exchange must be listed in the white list

		// If the map doesn't have the exchange then it isn't allowed
		AMQShortString exchangeName = new AMQShortString(parameters[0]);
		if (!exchanges.containsKey(exchangeName))
		{
		    return Result.DENIED;
		}
		else
		{
		    // Get valid routing keys
		    HashSet routingKeys = (HashSet) exchanges.get(exchangeName);

		    // Having no routingKeys in the map then all are allowed.
		    if (routingKeys == null)
		    {
		        return Result.ALLOWED;
		    }
		    else
		    {
		        // We have routingKeys so a match must be found to allowed binding
		        Iterator keys = routingKeys.iterator();

		        AMQShortString publishRKey = new AMQShortString(parameters[1]);

		        boolean matched = false;
		        while (keys.hasNext() && !matched)
		        {
		            AMQShortString rkey = (AMQShortString) keys.next();

		            if (rkey.endsWith("*"))
		            {
		                matched = publishRKey.startsWith(rkey.subSequence(0, rkey.length() - 1));
		            }
		            else
		            {
		                matched = publishRKey.equals(rkey);
		            }
		        }
		        return (matched) ? Result.ALLOWED : Result.DENIED;
		    }
		}
	}

	private Result authoriseCreateExchange(Permission permission, String... parameters)
	{
        if(_fullVHostAccess)
        {
            //user has been granted full access to the vhost
            return Result.ALLOWED;
        }

		Map rights = (Map) _permissions.get(permission);

		AMQShortString exchangeName = new AMQShortString(parameters[0]);

		// If the exchange list is doesn't exist then all is allowed else
		// check the valid exchanges
		if (rights == null || rights.containsKey(exchangeName))
		{
		    return Result.ALLOWED;
		}
		else
		{
		    return Result.DENIED;
		}
	}

	private Result authoriseCreateQueue(Permission permission, String... parameters)
	{
        if(_fullVHostAccess)
        {
            //user has been granted full access to the vhost
            return Result.ALLOWED;
        }

        Map createRights = (Map) _permissions.get(permission);

		// If there are no create rights then deny request
		if (createRights == null)
		{
		    return Result.DENIED;
		}

		//Look up the Queue Creation Rights
		Map create_queues = (Map) createRights.get(CREATE_QUEUES_KEY);

		//Lookup the list of queues allowed to be created
		Map create_queues_queues = (Map) create_queues.get(CREATE_QUEUE_QUEUES_KEY);


        Boolean autoDelete = Boolean.valueOf(parameters[0]);
		AMQShortString queueName = new AMQShortString(parameters[1]);

		if (autoDelete)// we have a temporary queue
		{
		    return ((Boolean) create_queues.get(CREATE_QUEUE_TEMPORARY_KEY)) ? Result.ALLOWED : Result.DENIED;
		}
		else
		{
		    // If there is a white list then check
		    if (create_queues_queues == null || create_queues_queues.containsKey(queueName))
		    {
		        return Result.ALLOWED;
		    }
		    else
		    {
		        return Result.DENIED;
		    }

		}
	}

	private Result authoriseBind(String... parameters)
	{
        if(_fullVHostAccess)
        {
            //user has been granted full access to the vhost
            return Result.ALLOWED;
        }

        AMQShortString exchangeName = new AMQShortString(parameters[1]);
        AMQShortString bind_queueName = new AMQShortString(parameters[2]);
		AMQShortString routingKey = new AMQShortString(parameters[3]);

		//Get all Create Rights for this user
		Map bindCreateRights = (Map) _permissions.get(Permission.CREATEQUEUE);

		//Lookup the list of queues
		Map bind_create_queues_queues = (Map) bindCreateRights.get(CREATE_QUEUE_QUEUES_KEY);

		// Check and see if we have a queue white list to check
		if (bind_create_queues_queues != null)
		{
		    //There a white list for queues
		    Map exchangeDetails = (Map) bind_create_queues_queues.get(bind_queueName);

		    if (exchangeDetails == null) //Then all queue can be bound to all exchanges.
		    {
		        return Result.ALLOWED;
		    }

		    // Check to see if we have a white list of routingkeys to check
		    Map rkeys = (Map) exchangeDetails.get(exchangeName);

		    // if keys is null then any rkey is allowed on this exchange
		    if (rkeys == null)
		    {
		        // There is no routingkey white list
		        return Result.ALLOWED;
		    }
		    else
		    {
		        // We have routingKeys so a match must be found to allowed binding
		        Iterator keys = rkeys.keySet().iterator();

		        boolean matched = false;
		        while (keys.hasNext() && !matched)
		        {
		            AMQShortString rkey = (AMQShortString) keys.next();
		            if (rkey.endsWith("*"))
		            {
		                matched = routingKey.startsWith(rkey.subSequence(0, rkey.length() - 1).toString());
		            }
		            else
		            {
		                matched = routingKey.equals(rkey);
		            }
		        }


		        return (matched) ? Result.ALLOWED : Result.DENIED;
		    }


		}
		else
		{
		    //no white list so all allowed.
		    return Result.ALLOWED;
		}
	}
}
