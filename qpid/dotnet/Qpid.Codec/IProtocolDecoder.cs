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
using System;
using Apache.Qpid.Buffer;

namespace Apache.Qpid.Codec
{
    public interface IProtocolDecoder : IDisposable
    {
        /// <summary>
        /// Decodes binary or protocol-specific content into higher-level message objects.
        /// MINA invokes {@link #decode(IoSession, ByteBuffer, ProtocolDecoderOutput)}
        /// method with read data, and then the decoder implementation puts decoded
        /// messages into {@link ProtocolDecoderOutput}.
        /// </summary>
        /// <param name="input"></param>
        /// <param name="output"></param>
        /// <exception cref="Exception">if the read data violated protocol specification</exception>
        void Decode(ByteBuffer input, IProtocolDecoderOutput output);
    }
}


