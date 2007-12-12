#ifndef QPID_INLINEALLOCATOR_H
#define QPID_INLINEALLOCATOR_H

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

#include <memory>

namespace qpid {

/**
 * An allocator that has inline storage for up to Max objects
 * of type BaseAllocator::value_type.
 */
template <class BaseAllocator, size_t Max> 
class InlineAllocator : public BaseAllocator {
  public:
    typedef typename BaseAllocator::pointer pointer;
    typedef typename BaseAllocator::size_type size_type;
    typedef typename BaseAllocator::value_type value_type;

    InlineAllocator() : allocated(false) {}
    
    pointer allocate(size_type n) {
        if (n <= Max && !allocated) {
            allocated=true;
            return store;
        }
        else 
            return BaseAllocator::allocate(n, 0);
    }

    void deallocate(pointer p, size_type n) {
        if (p == store) allocated=false;
        else BaseAllocator::deallocate(p, n);
    }

    template<typename T1>
    struct rebind {
        typedef typename BaseAllocator::template rebind<T1>::other BaseOther;
        typedef InlineAllocator<BaseOther, Max> other;
    };

  private:
    value_type store[Max];
    bool allocated;
};

} // namespace qpid

#endif  /*!QPID_INLINEALLOCATOR_H*/
