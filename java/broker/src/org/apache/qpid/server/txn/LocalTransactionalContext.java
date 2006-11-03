/**
 * User: Robert Greig
 * Date: 01-Nov-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.ack.TxAck;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.RequiredDeliveryException;

import java.util.List;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class LocalTransactionalContext implements TransactionalContext
{
    private final TxnBuffer _txnBuffer;

    /**
     * We keep hold of the ack operation so that we can consolidate acks, i.e. multiple acks within a txn are
     * consolidated into a single operation
     */
    private TxAck _ackOp;

    private List<RequiredDeliveryException> _returnMessages;

    public LocalTransactionalContext(TxnBuffer txnBuffer, List<RequiredDeliveryException> returnMessages)
    {
        _txnBuffer = txnBuffer;
        _returnMessages = returnMessages;
    }

    public void rollback() throws AMQException
    {
        _txnBuffer.rollback();
    }

    public void deliver(AMQMessage message, AMQQueue queue) throws AMQException
    {
        // don't create a transaction unless needed
        if (message.isPersistent())
        {
            _txnBuffer.containsPersistentChanges();
        }

        // A publication will result in the enlisting of several
        // TxnOps. The first is an op that will store the message.
        // Following that (and ordering is important), an op will
        // be added for every queue onto which the message is
        // enqueued. Finally a cleanup op will be added to decrement
        // the reference associated with the routing.
        _txnBuffer.enlist(new StoreMessageOperation(message));
        _txnBuffer.enlist(new DeliverMessageOperation(message, queue));
        _txnBuffer.enlist(new CleanupMessageOperation(message, _returnMessages));
    }

    private void checkAck(long deliveryTag, UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException
    {
        if (!unacknowledgedMessageMap.contains(deliveryTag))
        {
            throw new AMQException("Ack with delivery tag " + deliveryTag + " not known for channel");
        }
    }

    public void acknowledgeMessage(long deliveryTag, long lastDeliveryTag, boolean multiple,
                                   UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException
    {
        //check that the tag exists to give early failure
        if (!multiple || deliveryTag > 0)
        {
            checkAck(deliveryTag, unacknowledgedMessageMap);
        }
        //we use a single txn op for all acks and update this op
        //as new acks come in. If this is the first ack in the txn
        //we will need to create and enlist the op.
        if (_ackOp == null)
        {
            _ackOp = new TxAck(unacknowledgedMessageMap);
            _txnBuffer.enlist(_ackOp);
        }
        // update the op to include this ack request
        if (multiple && deliveryTag == 0)
        {
            // if have signalled to ack all, that refers only
            // to all at this time
            _ackOp.update(lastDeliveryTag, multiple);
        }
        else
        {
            _ackOp.update(deliveryTag, multiple);
        }
    }

    public void commit() throws AMQException
    {
        if (_ackOp != null)
        {
            _ackOp.consolidate();
            if (_ackOp.checkPersistent())
            {
                _txnBuffer.containsPersistentChanges();
            }
            //already enlisted, after commit will reset regardless of outcome
            _ackOp = null;
        }

        _txnBuffer.commit();
        //TODO: may need to return 'immediate' messages at this point
    }
}
