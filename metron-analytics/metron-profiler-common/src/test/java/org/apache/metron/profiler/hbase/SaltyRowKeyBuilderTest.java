/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.metron.profiler.hbase;

import org.apache.storm.tuple.Tuple;
import org.apache.metron.profiler.ProfileMeasurement;
import org.apache.metron.profiler.ProfilePeriod;
import org.apache.metron.profiler.hbase.SaltyRowKeyBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the SaltyRowKeyBuilder.
 */
public class SaltyRowKeyBuilderTest {

  private static final int saltDivisor = 1000;
  private static final long periodDuration = 15;
  private static final TimeUnit periodUnits = TimeUnit.MINUTES;

  private SaltyRowKeyBuilder rowKeyBuilder;
  private ProfileMeasurement measurement;
  private Tuple tuple;

  /**
   * Thu, Aug 25 2016 13:27:10 GMT
   */
  private long AUG2016 = 1472131630748L;

  @Before
  public void setup() throws Exception {

    // a profile measurement
    measurement = new ProfileMeasurement("profile", "entity", AUG2016, periodDuration, periodUnits);
    measurement.setValue(22);

    // the tuple will contain the original message
    tuple = mock(Tuple.class);
    when(tuple.getValueByField(eq("measurement"))).thenReturn(measurement);

    rowKeyBuilder = new SaltyRowKeyBuilder(saltDivisor, periodDuration, periodUnits);
  }

  /**
   * Build a row key that includes only one group.
   */
  @Test
  public void testRowKeyWithOneGroup() throws Exception {
    // setup
    List<Object> groups = Arrays.asList("group1");
    measurement.setGroups(groups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .put(SaltyRowKeyBuilder.getSalt(measurement.getPeriod(), saltDivisor))
            .put(measurement.getProfileName().getBytes())
            .put(measurement.getEntity().getBytes())
            .put("group1".getBytes())
            .putLong(1635701L);

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate
    byte[] actual = rowKeyBuilder.rowKey(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));
  }

  /**
   * Build a row key that includes two groups.
   */
  @Test
  public void testRowKeyWithTwoGroups() throws Exception {
    // setup
    List<Object> groups = Arrays.asList("group1","group2");
    measurement.setGroups(groups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .put(SaltyRowKeyBuilder.getSalt(measurement.getPeriod(), saltDivisor))
            .put(measurement.getProfileName().getBytes())
            .put(measurement.getEntity().getBytes())
            .put("group1".getBytes())
            .put("group2".getBytes())
            .putLong(1635701L);

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate
    byte[] actual = rowKeyBuilder.rowKey(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));
  }

  /**
   * Build a row key that includes a single group that is an integer.
   */
  @Test
  public void testRowKeyWithOneIntegerGroup() throws Exception {
    // setup
    List<Object> groups = Arrays.asList(200);
    measurement.setGroups(groups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .put(SaltyRowKeyBuilder.getSalt(measurement.getPeriod(), saltDivisor))
            .put(measurement.getProfileName().getBytes())
            .put(measurement.getEntity().getBytes())
            .put("200".getBytes())
            .putLong(1635701L);

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate
    byte[] actual = rowKeyBuilder.rowKey(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));
  }

  /**
   * Build a row key that includes a single group that is an integer.
   */
  @Test
  public void testRowKeyWithMixedGroups() throws Exception {
    // setup
    List<Object> groups = Arrays.asList(200, "group1");
    measurement.setGroups(groups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .put(SaltyRowKeyBuilder.getSalt(measurement.getPeriod(), saltDivisor))
            .put(measurement.getProfileName().getBytes())
            .put(measurement.getEntity().getBytes())
            .put("200".getBytes())
            .put("group1".getBytes())
            .putLong(1635701L);

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate
    byte[] actual = rowKeyBuilder.rowKey(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));
  }

  /**
   * Build a row key that does not include any groups.
   */
  @Test
  public void testRowKeyWithNoGroup() throws Exception {
    // setup
    List<Object> groups = Collections.emptyList();
    measurement.setGroups(groups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .put(SaltyRowKeyBuilder.getSalt(measurement.getPeriod(), saltDivisor))
            .put(measurement.getProfileName().getBytes())
            .put(measurement.getEntity().getBytes())
            .putLong(1635701L);

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate
    byte[] actual = rowKeyBuilder.rowKey(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));
  }

  /**
   * `rowKeys` should return all of the row keys needed to retrieve the profile values over a given time horizon.
   */
  @Test
  public void testRowKeys() throws Exception {
    int hoursAgo = 1;

    // setup
    List<Object> groups = Collections.emptyList();
    rowKeyBuilder = new SaltyRowKeyBuilder(saltDivisor, periodDuration, periodUnits);

    // a dummy profile measurement
    long now = System.currentTimeMillis();
    long oldest = now - TimeUnit.HOURS.toMillis(hoursAgo);
    ProfileMeasurement m = new ProfileMeasurement("profile", "entity", oldest, periodDuration, periodUnits);
    m.setValue(22);

    // generate a list of expected keys
    List<byte[]> expectedKeys = new ArrayList<>();
    for  (int i=0; i<(hoursAgo * 4)+1; i++) {

      // generate the expected key
      byte[] rk = rowKeyBuilder.rowKey(m);
      expectedKeys.add(rk);

      // advance to the next period
      ProfilePeriod next = m.getPeriod().next();
      m = new ProfileMeasurement("profile", "entity", next.getStartTimeMillis(), periodDuration, periodUnits);
    }

    // execute
    List<byte[]> actualKeys = rowKeyBuilder.rowKeys(measurement.getProfileName(), measurement.getEntity(), groups, oldest, now);

    // validate - expectedKeys == actualKeys
    for(int i=0; i<actualKeys.size(); i++) {
      byte[] actual = actualKeys.get(i);
      byte[] expected = expectedKeys.get(i);
      assertThat(actual, equalTo(expected));
    }
  }

  private void printBytes(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    Formatter formatter = new Formatter(sb);
    for (byte b : bytes) {
      formatter.format("%02x ", b);
    }
    System.out.println(sb.toString());
  }
}
