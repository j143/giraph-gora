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
import java.util.ArrayList;
import java.util.List;

import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.VertexInputFormat;
import org.apache.giraph.io.VertexReader;
import org.apache.giraph.io.gora.GoraUtils;
import org.apache.giraph.io.gora.generated.GVertex;
import org.apache.gora.mapreduce.GoraInputFormat;
import org.apache.gora.mapreduce.GoraInputSplit;
import org.apache.gora.persistency.impl.PersistentBase;
import org.apache.gora.query.PartitionQuery;
import org.apache.gora.query.Query;
import org.apache.gora.query.Result;
import org.apache.gora.query.impl.PartitionQueryImpl;
import org.apache.gora.store.DataStore;
import org.apache.gora.util.GoraException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.log4j.Logger;

/**
 *  Class which wraps the GoraInputFormat. It's designed
 *  as an extension point to VertexInputFormat subclasses who wish
 *  to read from Gora data sources.
 *
 *  Works with
 *  {@link GoraVertexOutputFormat}
 *
 * @param <I> vertex id type
 * @param <V>  vertex value type
 * @param <E>  edge type
 */
public abstract class GoraVertexInputFormat<
        I extends WritableComparable,
        V extends Writable,
        E extends Writable>
        extends VertexInputFormat<I, V, E> {

  /** Logger for Gora's vertex input format. */
  private static final Logger LOG =
          Logger.getLogger(GoraVertexInputFormat.class);

  private static Class<?> keyClass;
  /** The vertex itself will be used as a value inside Gora. */
  private static Class<? extends PersistentBase> persistentClass;
  
  /**
   * Data store used for querying data.
   */
  private static DataStore dataStore;

  /** counter for iinput records */
  private static int recordCounter = 0;

  /**
  * delegate HBase table input format
  */
 protected static ExtraGoraInputFormat goraInputFormat =
         new ExtraGoraInputFormat();

  /** @param conf configuration parameters */
  public void checkInputSpecs(Configuration conf) { }

  /**
   * Create a vertex reader for a given split. Guaranteed to have been
   * configured with setConf() prior to use.  The framework will also call
   * {@link VertexReader#initialize(InputSplit, TaskAttemptContext)} before
   * the split is used.
   *
   * @param split the split to be read
   * @param context the information about the task
   * @return a new record reader
   * @throws IOException
   */
  public abstract GoraVertexReader createVertexReader(InputSplit split,
    TaskAttemptContext context) throws IOException;

  public void initialize() {
    dataStore = getDataStore();
  }

  @Override
  public List<InputSplit> getSplits(JobContext context, int minSplitCountHint)
    throws IOException, InterruptedException {
    /*List<PartitionQuery> queries = dataStore.getPartitions(
        GoraUtils.getQuery(dataStore));
    if (queries != null) {
      System.out.println("Habia partitions en getSplits" + queries.size());
    }
    if (GoraUtils.getQuery(dataStore) != null) {
      System.out.println("Conseguimos crear una query getSplits");
    }
    List<InputSplit> splits = new ArrayList<InputSplit>(queries.size());
    for(PartitionQuery query : queries) {
      splits.add(new GoraInputSplit(context.getConfiguration(), query));
    }
    if (splits.size() > 0) {
      System.out.println("Guardamos algo de splits " + splits.size());
    }*/
    goraInputFormat.setDataStore(dataStore);
    goraInputFormat.setQuery(GoraUtils.getQuery(dataStore));
    List<InputSplit> splits = goraInputFormat.getSplits(context);
    if (splits != null) {
      System.out.println("Habia partitions en getSplits" + splits.size());
      String []locs = splits.get(0).getLocations();
      if ( locs != null ) {
        System.out.println("there were locations " + locs.length);
      }
      else {
        System.out.println("there were no locations");
      }
    }
    else {
      System.out.println("el input format sigue sin funcionar");
    }
    return splits;
  }

  /*private List<InputSplit> createSplits(JobContext context, Query query){
    int chunks = context.getConfiguration().getInt("mapred.map.tasks", 1);
    long chunkSize = chunks;
    List<InputSplit> splits = new ArrayList<InputSplit>();
    for (int i = 0; i < chunks; i++) {
      final ExtraGoraInputSplit split;
      final long              start;
      final long              end;

      start = i * chunkSize;
      end   = ((i + 1) == chunks) ? Long.MAX_VALUE :
                                    (i * chunkSize) + chunkSize;
      split = new ExtraGoraInputSplit();
      splits.add(split);

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Chunk: start %d; end %d;", start, end));
        LOG.debug(String.format("Chunk: size %d;", chunkSize));
        LOG.debug(split);
      }
    }
    System.out.println("Hubo " + chunks + " chunks.");
    return splits;
  }*/

  /**
   * Gets the data store object initialized.
   */
  public DataStore getDataStore() {
    try {
      if (getKeyClass() == null) {
        System.out.println("No hay key class");
      }
      if (getPersistentClass() == null) {
        System.out.println("No hay persistent class");
      }
      return GoraUtils.createSpecificDataStore(GoraUtils.CASSANDRA_STORE,
          getKeyClass(), getPersistentClass());
    } catch (GoraException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Abstract class to be implemented by the user based on their specific
   * vertex input. Easiest to ignore the key value separator and only use
   * key instead.
   */
  protected abstract class GoraVertexReader extends VertexReader<I, V, E> {
    /** Current vertex */
    private Vertex<I, V, E> vertex;
    /** Results gotten from Gora data store. */
    private Result readResults;
    /** Start key for querying Gora data store. */
    private Object startKey;
    /** End key for querying Gora data store. */
    private Object endKey;

    @Override
    public void initialize(InputSplit inputSplit, TaskAttemptContext context)
      throws IOException, InterruptedException {
      getResults();
      recordCounter = 0;
    }

    /**
     * Gets the next vertex from Gora data store.
     * @return true/false depending on the existence of vertices.
     * @throws IOException exceptions passed along.
     * @throws InterruptedException exceptions passed along.
     */
    @Override
    // CHECKSTYLE: stop IllegalCatch
    public boolean nextVertex() throws IOException, InterruptedException {
      System.out.println("Reading vertices from Gora");
      boolean flg = false;
      
      try {
        flg = this.getReadResults().next();
        this.vertex = transformVertex(this.getReadResults().get());
        System.out.println("Transformado");
        System.out.println(this.vertex.toString());
        ++recordCounter;
      } catch (Exception e) {
        LOG.debug("Error transforming vertices.");
        flg = false;
      }
      System.out.println("Transformamos " + recordCounter + " registros");
      return flg;
    }
    // CHECKSTYLE: resume IllegalCatch

    /**
     * Gets the progress of reading results from Gora.
     * @return the progress of reading results from Gora.
     */
    @Override
    public float getProgress() throws IOException, InterruptedException {
      float progress = 0.0f;
      if (getReadResults() != null) {
        progress = getReadResults().getProgress();
      }
      return progress;
    }

    /**
     * Gets current vertex.
     *
     * @return  The vertex object represented by a Gora object
     */
    @Override
    public Vertex<I, V, E> getCurrentVertex()
      throws IOException, InterruptedException {
      return this.vertex;
    }

    /**
     * Parser for a single Gora object
     *
     * @param   goraObject vertex represented as a GoraObject
     * @return  The vertex object represented by a Gora object
     */
    protected abstract Vertex<I, V, E> transformVertex(Object goraObject);

    /**
     * Performs a range query to a Gora data store.
     */
    protected void getResults() {
      setReadResults(GoraUtils.getRequest(dataStore,
          getStartKey(), getEndKey()));
    }

    /**
     * Finishes the reading process.
     * @throws IOException.
     */
    @Override
    public void close() throws IOException {
    }

    /**
     * Gets the start key for querying.
     * @return the start key.
     */
    public Object getStartKey() {
      return startKey;
    }

    /**
     * Gets the start key for querying.
     * @param startKey start key.
     */
    public void setStartKey(Object startKey) {
      this.startKey = startKey;
    }

    /**
     * Gets the end key for querying.
     * @return the end key.
     */
    Object getEndKey() {
      return endKey;
    }

    /**
     * Sets the end key for querying.
     * @param pEndKey start key.
     */
    void setEndKey(Object pEndKey) {
      this.endKey = pEndKey;
    }

    /**
     * Gets the results read.
     * @return results read.
     */
    Result getReadResults() {
      return readResults;
    }

    /**
     * Sets the results read.
     * @param readResults results read.
     */
    void setReadResults(Result readResults) {
      this.readResults = readResults;
    }
  }

  /**
   * Gets the persistent Class
   * @return persistentClass used
   */
  static Class<? extends PersistentBase> getPersistentClass() {
    return persistentClass;
  }

  /**
   * Sets the persistent Class
   * @param persistentClass to be set
   */
  static void setPersistentClass(Class<? extends PersistentBase> persistentClassUsed) {
    persistentClass = persistentClassUsed;
  }

  /**
   * Gets the key class used.
   * @return the key class used.
   */
  static Class<?> getKeyClass() {
    return keyClass;
  }

  /**
   * Sets the key class used.
   * @param keyClassUsed key class used.
   */
  static void setKeyClass(Class<?> keyClassUsed) {
    keyClass = keyClassUsed;
  }
}
