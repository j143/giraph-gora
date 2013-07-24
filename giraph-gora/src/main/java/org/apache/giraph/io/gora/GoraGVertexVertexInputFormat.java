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

import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.gora.generated.GVertex;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * Implementation of a specific reader for a generated data bean.
 */
public class GoraGVertexVertexInputFormat
  extends GoraVertexInputFormat<Text, FloatWritable,
          FloatWritable> {

  @Override
  public GoraVertexReader createVertexReader(
      InputSplit split, TaskAttemptContext context) throws IOException {
    return new GoraGVertexVertexReader();
  }

  /**
   * Gora vertex reader
   */
  protected class GoraGVertexVertexReader extends GoraVertexReader {

    @Override
    protected Vertex<Text, FloatWritable, FloatWritable> transformVertex(
        Object goraObject) {
      Vertex<Text, FloatWritable, FloatWritable> vertex;
      /* create the actual vertex */
      vertex = getConf().createVertex();
      GVertex tmpGVertex = (GVertex) goraObject;
      Text vrtxId = new Text(tmpGVertex.getVertexId().toString());
      FloatWritable vrtxValue = new FloatWritable(tmpGVertex.getValue());
      vertex.initialize(vrtxId, vrtxValue);
      return vertex;
    }
  }
}
