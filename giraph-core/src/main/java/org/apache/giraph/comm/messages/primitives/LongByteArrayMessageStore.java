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

package org.apache.giraph.comm.messages.primitives;

import org.apache.giraph.bsp.CentralizedServiceWorker;
import org.apache.giraph.comm.messages.MessageStore;
import org.apache.giraph.comm.messages.MessagesIterable;
import org.apache.giraph.conf.ImmutableClassesGiraphConfiguration;
import org.apache.giraph.factories.MessageValueFactory;
import org.apache.giraph.partition.Partition;
import org.apache.giraph.utils.ByteArrayVertexIdMessages;
import org.apache.giraph.utils.EmptyIterable;
import org.apache.giraph.utils.ExtendedDataOutput;
import org.apache.giraph.utils.WritableUtils;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;

import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/**
 * Special message store to be used when ids are LongWritable and no combiner
 * is used.
 * Uses fastutil primitive maps in order to decrease number of objects and
 * get better performance.
 *
 * @param <M> Message type
 */
public class LongByteArrayMessageStore<M extends Writable>
    implements MessageStore<LongWritable, M> {
  /** Message value factory */
  protected final MessageValueFactory<M> messageValueFactory;
  /** Map from partition id to map from vertex id to message */
  private final
  Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ExtendedDataOutput>> map;
  /** Service worker */
  private final CentralizedServiceWorker<LongWritable, ?, ?> service;
  /** Giraph configuration */
  private final ImmutableClassesGiraphConfiguration<LongWritable, ?, ?> config;

  /**
   * Constructor
   *
   * @param messageValueFactory Factory for creating message values
   * @param service      Service worker
   * @param config       Hadoop configuration
   */
  public LongByteArrayMessageStore(
      MessageValueFactory<M> messageValueFactory,
      CentralizedServiceWorker<LongWritable, ?, ?> service,
      ImmutableClassesGiraphConfiguration<LongWritable, ?, ?> config) {
    this.messageValueFactory = messageValueFactory;
    this.service = service;
    this.config = config;

    map =
        new Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ExtendedDataOutput>>();
    for (int partitionId : service.getPartitionStore().getPartitionIds()) {
      Partition<LongWritable, ?, ?> partition =
          service.getPartitionStore().getPartition(partitionId);
      Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap =
          new Long2ObjectOpenHashMap<ExtendedDataOutput>(
              (int) partition.getVertexCount());
      map.put(partitionId, partitionMap);
    }
  }

  /**
   * Get map which holds messages for partition which vertex belongs to.
   *
   * @param vertexId Id of the vertex
   * @return Map which holds messages for partition which vertex belongs to.
   */
  private Long2ObjectOpenHashMap<ExtendedDataOutput> getPartitionMap(
      LongWritable vertexId) {
    return map.get(service.getPartitionId(vertexId));
  }

  /**
   * Get the extended data output for a vertex id, creating if necessary.
   *
   * @param partitionMap Partition map to look in
   * @param vertexId Id of the vertex
   * @return Extended data output for this vertex id (created if necessary)
   */
  private ExtendedDataOutput getExtendedDataOutput(
      Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap,
      long vertexId) {
    ExtendedDataOutput extendedDataOutput = partitionMap.get(vertexId);
    if (extendedDataOutput == null) {
      extendedDataOutput = config.createExtendedDataOutput();
      partitionMap.put(vertexId, extendedDataOutput);
    }
    return extendedDataOutput;
  }

  @Override
  public void addPartitionMessages(int partitionId,
      ByteArrayVertexIdMessages<LongWritable, M> messages) throws
      IOException {
    Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap =
        map.get(partitionId);
    synchronized (partitionMap) {
      ByteArrayVertexIdMessages<LongWritable, M>.VertexIdMessageBytesIterator
          vertexIdMessageBytesIterator =
          messages.getVertexIdMessageBytesIterator();
      // Try to copy the message buffer over rather than
      // doing a deserialization of a message just to know its size.  This
      // should be more efficient for complex objects where serialization is
      // expensive.  If this type of iterator is not available, fall back to
      // deserializing/serializing the messages
      if (vertexIdMessageBytesIterator != null) {
        while (vertexIdMessageBytesIterator.hasNext()) {
          vertexIdMessageBytesIterator.next();
          vertexIdMessageBytesIterator.writeCurrentMessageBytes(
              getExtendedDataOutput(partitionMap,
                  vertexIdMessageBytesIterator.getCurrentVertexId().get()));
        }
      } else {
        ByteArrayVertexIdMessages<LongWritable, M>.VertexIdMessageIterator
            iterator = messages.getVertexIdMessageIterator();
        while (iterator.hasNext()) {
          iterator.next();
          iterator.getCurrentMessage().write(
              getExtendedDataOutput(partitionMap,
                  iterator.getCurrentVertexId().get()));
        }
      }
    }
  }

  @Override
  public void clearPartition(int partitionId) throws IOException {
    map.get(partitionId).clear();
  }

  @Override
  public boolean hasMessagesForVertex(LongWritable vertexId) {
    return getPartitionMap(vertexId).containsKey(vertexId.get());
  }

  @Override
  public Iterable<M> getVertexMessages(
      LongWritable vertexId) throws IOException {
    ExtendedDataOutput extendedDataOutput =
        getPartitionMap(vertexId).get(vertexId.get());
    if (extendedDataOutput == null) {
      return EmptyIterable.get();
    } else {
      return new MessagesIterable<M>(config, messageValueFactory,
          extendedDataOutput.getByteArray(), 0, extendedDataOutput.getPos());
    }
  }

  @Override
  public void clearVertexMessages(LongWritable vertexId) throws IOException {
    getPartitionMap(vertexId).remove(vertexId.get());
  }

  @Override
  public void clearAll() throws IOException {
    map.clear();
  }

  @Override
  public Iterable<LongWritable> getPartitionDestinationVertices(
      int partitionId) {
    Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap =
        map.get(partitionId);
    List<LongWritable> vertices =
        Lists.newArrayListWithCapacity(partitionMap.size());
    LongIterator iterator = partitionMap.keySet().iterator();
    while (iterator.hasNext()) {
      vertices.add(new LongWritable(iterator.nextLong()));
    }
    return vertices;
  }

  @Override
  public void writePartition(DataOutput out,
      int partitionId) throws IOException {
    Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap =
        map.get(partitionId);
    out.writeInt(partitionMap.size());
    ObjectIterator<Long2ObjectMap.Entry<ExtendedDataOutput>> iterator =
        partitionMap.long2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Long2ObjectMap.Entry<ExtendedDataOutput> entry = iterator.next();
      out.writeLong(entry.getLongKey());
      WritableUtils.writeExtendedDataOutput(entry.getValue(), out);
    }
  }

  @Override
  public void readFieldsForPartition(DataInput in,
      int partitionId) throws IOException {
    int size = in.readInt();
    Long2ObjectOpenHashMap<ExtendedDataOutput> partitionMap =
        new Long2ObjectOpenHashMap<ExtendedDataOutput>(size);
    while (size-- > 0) {
      long vertexId = in.readLong();
      partitionMap.put(vertexId,
          WritableUtils.readExtendedDataOutput(in, config));
    }
    synchronized (map) {
      map.put(partitionId, partitionMap);
    }
  }
}
