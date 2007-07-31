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
using Apache.Qpid.Buffer;

namespace Apache.Qpid.Framing
{
    public class AMQMethodBodyFactory : IBodyFactory
    {
        private static readonly AMQMethodBodyFactory _instance = new AMQMethodBodyFactory();

        public static AMQMethodBodyFactory GetInstance()
        {
            return _instance;
        }

        /// <summary>
        /// Creates the body.
        /// </summary>
        /// <param name="inbuf">The ByteBuffer containing data from the network</param>
        /// <returns></returns>
        /// <exception>AMQFrameDecodingException</exception>
        public IBody CreateBody(ByteBuffer inbuf)
        {
           return MethodBodyDecoderRegistry.Get(inbuf.GetUInt16(), inbuf.GetUInt16());
        }
    }
}
