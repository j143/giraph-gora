/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.giraph.comm.messages;

import org.apache.giraph.factories.MessageValueFactory;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

/**
 * Factory for message stores
 *
 * @param <I> Vertex id
 * @param <M> Message data
 * @param <MS> Message store
 */
public interface MessageStoreFactory<I extends WritableComparable,
    M extends Writable, MS> {
  /**
   * Creates new message store.
   *
   * Note: Combiner class in Configuration can be changed,
   * this method should return MessageStore which uses current combiner
   *
   *
   * @param messageValueFactory Message class held in the store
   * @return New message store
   */
  MS newStore(MessageValueFactory<M> messageValueFactory);
}
