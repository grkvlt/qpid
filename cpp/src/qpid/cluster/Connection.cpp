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
#include "Connection.h"
#include "DumpClient.h"
#include "Cluster.h"

#include "qpid/broker/SessionState.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/TxPublish.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ConnectionCloseBody.h"
#include "qpid/framing/ConnectionCloseOkBody.h"
#include "qpid/log/Statement.h"

#include <boost/current_function.hpp>

// FIXME aconway 2008-11-03:
// 
// Disproportionate amount of code here is dedicated to receiving a
// brain-dump when joining a cluster and building initial
// state. Should be separated out into its own classes.
//


namespace qpid {
namespace cluster {

using namespace framing;

NoOpConnectionOutputHandler Connection::discardHandler;

// Shadow connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, ConnectionId myId)
    : cluster(c), self(myId), catchUp(false), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId)
{
    QPID_LOG(debug, cluster << " new connection: " << *this);
}

// Local connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, MemberId myId, bool isCatchUp)
    : cluster(c), self(myId, this), catchUp(isCatchUp), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId)
{
    QPID_LOG(debug, cluster << " new connection: " << *this);
}

Connection::~Connection() {
    QPID_LOG(debug, cluster << " deleted connection: " << *this);
}

bool Connection::doOutput() {
    return output.doOutput();
}

// Delivery of doOutput allows us to run the real connection doOutput()
// which stocks up the write buffers with data.
//
void Connection::deliverDoOutput(uint32_t requested) {
    assert(!catchUp);
    output.deliverDoOutput(requested);
}

// Received from a directly connected client.
void Connection::received(framing::AMQFrame& f) {
    QPID_LOG(trace, cluster << " RECV " << *this << ": " << f);
    if (isLocal()) {
        currentChannel = f.getChannel();
        if (!framing::invoke(*this, *f.getBody()).wasHandled())
            connection.received(f);
    }
    else {             // Shadow or dumped ex catch-up connection.
        if (f.getMethod() && f.getMethod()->isA<ConnectionCloseBody>()) {
            if (isShadow()) {
                QPID_LOG(debug, cluster << " inserting connection " << *this);
                cluster.insert(boost::intrusive_ptr<Connection>(this));
            }
            AMQFrame ok(in_place<ConnectionCloseOkBody>());
            connection.getOutput().send(ok);
            output.setOutputHandler(discardHandler);
            catchUp = false;
        }
        else
            QPID_LOG(warning, cluster << " ignoring unexpected frame " << *this << ": " << f);
    }
}

bool Connection::checkUnsupported(const AMQBody& body) {
    std::string message;
    if (body.getMethod()) {
        switch (body.getMethod()->amqpClassId()) {
          case TX_CLASS_ID: message = "TX transactions are not currently supported by cluster."; break;
          case DTX_CLASS_ID: message = "DTX transactions are not currently supported by cluster."; break;
        }
    }
    else if (body.type() == HEADER_BODY) {
        const DeliveryProperties* dp = static_cast<const AMQHeaderBody&>(body).get<DeliveryProperties>();
        if (dp && dp->getTtl()) message = "Message TTL is not currently supported by cluster.";
    }
    if (!message.empty())
        connection.close(execution::ERROR_CODE_INTERNAL_ERROR, message, 0, 0);
    return !message.empty();
}

// Delivered from cluster.
void Connection::delivered(framing::AMQFrame& f) {
    QPID_LOG(trace, cluster << "DLVR " << *this << ": " << f);
    assert(!catchUp);
    currentChannel = f.getChannel(); 
    if (!framing::invoke(*this, *f.getBody()).wasHandled() // Connection contol.
        && !checkUnsupported(*f.getBody())) // Unsupported operation.
    {
        connection.received(f); // Pass to broker connection.
    }
}

void Connection::closed() {
    try {
        if (catchUp) {
            QPID_LOG(critical, cluster << " catch-up connection closed prematurely " << *this);
            cluster.leave();
        }
        else if (isDumped()) {
            QPID_LOG(debug, cluster << " closed dump connection " << *this);
            connection.closed();
        }
        else if (isLocal()) {
            QPID_LOG(debug, cluster << " local close of replicated connection " << *this);
            // This was a local replicated connection. Multicast a deliver
            // closed and process any outstanding frames from the cluster
            // until self-delivery of deliver-close.
            output.setOutputHandler(discardHandler);
            cluster.mcastControl(ClusterConnectionDeliverCloseBody(), self, ++mcastSeq);
        }
    }
    catch (const std::exception& e) {
        QPID_LOG(error, cluster << " error closing connection " << *this << ": " << e.what());
    }
}

void Connection::deliverClose () {
    assert(!catchUp);
    connection.closed();
    cluster.erase(self);
}

// Decode data from local clients.
size_t Connection::decode(const char* buffer, size_t size) {
    if (catchUp) {  // Handle catch-up locally.
        Buffer buf(const_cast<char*>(buffer), size);
        while (localDecoder.decode(buf))
            received(localDecoder.frame);
    }
    else {                      // Multicast local connections.
        assert(isLocal());
        cluster.mcastBuffer(buffer, size, self, ++mcastSeq);
    }
    return size;
}

void Connection::deliverBuffer(Buffer& buf) {
    assert(!catchUp);
    ++deliverSeq;
    while (mcastDecoder.decode(buf))
        delivered(mcastDecoder.frame);
}

broker::SessionState& Connection::sessionState() {
    return *connection.getChannel(currentChannel).getSession();
}

broker::SemanticState& Connection::semanticState() {
    return sessionState().getSemanticState();
}

void Connection::consumerState(const string& name, bool blocked, bool notifyEnabled) {
    broker::SemanticState::ConsumerImpl& c = semanticState().find(name);
    c.setBlocked(blocked);
    if (notifyEnabled) c.enableNotify(); else c.disableNotify();
}

void Connection::sessionState(
    const SequenceNumber& replayStart,
    const SequenceNumber& sendCommandPoint,
    const SequenceSet& sentIncomplete,
    const SequenceNumber& expected,
    const SequenceNumber& received,
    const SequenceSet& unknownCompleted,
    const SequenceSet& receivedIncomplete)
{
    sessionState().setState(
        replayStart,
        sendCommandPoint,
        sentIncomplete,
        expected,
        received,
        unknownCompleted,
        receivedIncomplete);
    QPID_LOG(debug, cluster << " received session state dump for " << sessionState().getId());
}
    
void Connection::shadowReady(uint64_t memberId, uint64_t connectionId) {
    ConnectionId shadow = ConnectionId(memberId, connectionId);
    QPID_LOG(debug, cluster << " catch-up connection " << *this << " becomes shadow " << shadow);
    self = shadow;
}

void Connection::membership(const FieldTable& newbies, const FieldTable& members) {
    QPID_LOG(debug, cluster << " incoming dump complete on connection " << *this);
    cluster.dumpInDone(ClusterMap(newbies, members));
    self.second = 0;        // Mark this as completed dump connection.
}

bool Connection::isLocal() const {
    return self.first == cluster.getId() && self.second == this;
}

bool Connection::isShadow() const {
    return self.first != cluster.getId();
}

bool Connection::isDumped() const {
    return self.first == cluster.getId() && self.second == 0;
}

broker::QueuedMessage Connection::getDumpMessage() {
    // Get a message from the DUMP queue.
    broker::Queue::shared_ptr dumpQueue = cluster.getBroker().getQueues().find(DumpClient::DUMP);
    if (!dumpQueue) throw Exception(QPID_MSG(cluster << " missing dump queue"));
    broker::QueuedMessage m = dumpQueue->get();
    if (!m.payload) throw Exception(QPID_MSG(cluster << " empty dump queue"));
    return m;
}

void Connection::deliveryRecord(const string& qname,
                                const SequenceNumber& position,
                                const string& tag,
                                const SequenceNumber& id,
                                bool acquired,
                                bool accepted,
                                bool cancelled,
                                bool completed,
                                bool ended,
                                bool windowing)
{
    broker::Queue::shared_ptr queue = cluster.getBroker().getQueues().find(qname);
    if (!queue) throw Exception(QPID_MSG(cluster << " bad deliveryRecord queue " << qname));
    broker::QueuedMessage m;
    if (!ended) {               // Has a message
        if (acquired) {          // Message at front of dump queue
            broker::Queue::shared_ptr dumpQueue = cluster.getBroker().getQueues().find(DumpClient::DUMP);
            m = dumpQueue->get();
        }
        else                    // Message at original position in original queue
            m = queue->find(position);
        if (!m.payload)
            throw Exception(QPID_MSG("deliveryRecord no dump message"));
    }

    broker::DeliveryRecord dr(m, queue, tag, acquired, accepted, windowing);
    dr.setId(id);
    if (cancelled) dr.cancel(dr.getTag());
    if (completed) dr.complete();
    if (ended) dr.setEnded();   // Exsitance of message

    semanticState().record(dr);
}

void Connection::queuePosition(const string& qname, const SequenceNumber& position) {
    shared_ptr<broker::Queue> q = cluster.getBroker().getQueues().find(qname);
    if (!q) throw InvalidArgumentException(QPID_MSG("Invalid queue name " << qname));
    q->setPosition(position);
}

std::ostream& operator<<(std::ostream& o, const Connection& c) {
    const char* type="unknown";
    if (c.isLocal()) type = "local";
    else if (c.isShadow()) type = "shadow";
    else if (c.isDumped()) type = "dumped";
    return o << c.getId() << "(" << type << (c.isCatchUp() ? ",catchup" : "") << ")";
}



}} // namespace qpid::cluster

