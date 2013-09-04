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
package org.apache.giraph.io.gora;

import java.io.IOException;
import java.util.Set;

import org.apache.avro.util.Utf8;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.gora.generated.GVertex;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * Implementation of a specific reader for a generated data bean.
 */
public class GoraGVertexVertexInputFormat
  extends GoraVertexInputFormat<LongWritable, DoubleWritable,
          FloatWritable> {

  /**
   * DEfault constructor
   */
  public GoraGVertexVertexInputFormat() {
    //this.initialize();
    super.initialize(String.class, GVertex.class,
        org.apache.gora.hbase.store.HBaseStore.class);
    setStartKey("1");
    setEndKey("101");
  }

  /**
   * Initializes specific vertex reader.
   */
  public void initialize() {
    setKeyClass(String.class);
    setPersistentClass(GVertex.class);
    setStartKey("1");
    setEndKey("101");
    //super.initialize(GoraUtils.HBASE_STORE);
  }

  /**
   * Creates specific vertex reader to be used inside Hadoop.
   * @param split split to be read.
   * @param context JobContext to be used.
   * @return GoraVertexReader Vertex reader to be used by Hadoop.
   */
  @Override
  public GoraVertexReader createVertexReader(
      InputSplit split, TaskAttemptContext context) throws IOException {
    return new GoraGVertexVertexReader();
  }

  /**
   * Gora vertex reader
   */
  protected class GoraGVertexVertexReader extends GoraVertexReader {

    /**
     * Transforms a GoraObject into a Vertex object.
     * @param goraObject Object from Gora to be translated.
     * @return Vertex Result from transforming the gora object.
     */
    @Override
    protected Vertex<LongWritable, DoubleWritable, FloatWritable>
    transformVertex(Object goraObject) {
      Vertex<LongWritable, DoubleWritable, FloatWritable> vertex;
      /* create the actual vertex */
      vertex = getConf().createVertex();
      GVertex tmpGVertex = (GVertex) goraObject;

      //System.out.println("[GORA_OBJECT]");
      //System.out.println(tmpGVertex.toString());
      LongWritable vrtxId = new LongWritable(
          Long.parseLong(tmpGVertex.getVertexId().toString()));
      DoubleWritable vrtxValue = new DoubleWritable(tmpGVertex.getValue());
      vertex.initialize(vrtxId, vrtxValue);
      if (tmpGVertex.getEdges() != null && !tmpGVertex.getEdges().isEmpty()) {
        Set<Utf8> keyIt = tmpGVertex.getEdges().keySet();
        for (Utf8 key : keyIt) {
          String keyVal = key.toString();
          String valVal = tmpGVertex.getEdges().get(key).toString();
          Edge<LongWritable, FloatWritable> edge;
          if (!keyVal.contains("vertexId")) {
            edge = EdgeFactory.create(
                new LongWritable(Long.parseLong(keyVal)),
                new FloatWritable(Float.parseFloat(valVal)));
            vertex.addEdge(edge);
          }
        }
      }
      //System.out.println("[GIRAPH_OBJECT]");
      //System.out.println(vertex.toString());
      return vertex;
    }
  }
}
