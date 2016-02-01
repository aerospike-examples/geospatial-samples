/*
 * Copyright 2016 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.aerospike.delivery.db.aerospike;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Info;
import com.aerospike.client.ResultCode;
import com.aerospike.client.cluster.Node;
import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.util.OurExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;


public class InfoParser {

  /**
   * Uses the Info class to get latency stats averaged over the cluster.
   * The returned map has keys for reads, writes, proxy, udf, and query.
   * The value is a map that has keys for ops/sec, >1ms, >8ms, >64ms with value of double.
   * The returned map's toString() method produces this:
   *   proxy >1ms=0.0, >64ms=0.0, >8ms=0.0, ops/sec=0.0
   *   query >1ms=0.2, >64ms=0.0, >8ms=0.0, ops/sec=85.4
   *   reads >1ms=0.0, >64ms=0.0, >8ms=0.0, ops/sec=8.0
   *   udf >1ms=0.0, >64ms=0.0, >8ms=0.0, ops/sec=0.0
   *   writes_master >1ms=0.4, >64ms=0.0, >8ms=0.0, ops/sec=753.8
   */
  public static NumericInfoMap getClusterLatencyInfo(AerospikeClient client) {
    String[] nodeInfoStrings = infoAll(client, "latency:");
    NumericInfoListMap stats = new NumericInfoListMap(nodeInfoStrings);
    NumericInfoMap result = NumericInfoMap.average(stats);
    return result;
  }

  public static NumericInfoMap getNodeLatencyInfo(Node node) {
    String nodeInfoString = info(node, "latency:");
    NumericInfoMap result = NumericInfoMap.makeNumericInfoMap(nodeInfoString);
    return result;
  }

  public static String[] infoAll(AerospikeClient client, String name) {
    String[] result = new String[client.getNodes().length];
    int i = 0;
    for (Node node : client.getNodes()) {
      result[i++] = info(node, name);
    }
    return result;
  }


  public static String info(Node node, String name) {
    try {
      String result = Info.request(node.getHost().name, node.getHost().port, name);
      return result;
      // The result looks like this (broken here into 5 lines). Node the ; in the middle.
      //   reads:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms;20:07:55,0.0,0.00,0.00,0.00;
      //   writes_master:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms;20:07:55,0.0,0.00,0.00,0.00;
      //   proxy:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms;20:07:55,0.0,0.00,0.00,0.00;
      //   udf:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms;20:07:55,0.0,0.00,0.00,0.00;
      //   query:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms;20:07:55,0.0,0.00,0.00,0.00;
    } catch (AerospikeException e) {
      int resultCode = e.getResultCode();
      System.err.format("info request %s %s %s\n", name, ResultCode.getResultString(resultCode), e);
    }
    return null;
  }

  //------------------------------------------------------------------------------------

  public static class NumericInfoListMap extends LinkedHashMap<String, List<NumericInfo>> {

    public NumericInfoListMap(String[] nodeInfoStrings) {
      super();
      for (String nodeInfoString : nodeInfoStrings) {
        gatherNodeNumericInfo(nodeInfoString);
      }
    }

    private void gatherNodeNumericInfo(String nodeInfoString) {
      String[] st = nodeInfoString.split(";");
      for (int i = 0 ; i < st.length ; i += 2) {
        // "reads:20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms"
        // "20:07:55,0.0,0.00,0.00,0.00"
        gatherInfoForOperation(st[i], st[i + 1]);
      }
    }

    private void gatherInfoForOperation(String header, String values) {
      String[] nameSplit = header.split(":", 2);
      String key = nameSplit[0]; // "reads"
      // "20:07:45-GMT,ops/sec,>1ms,>8ms,>64ms"
      // "20:07:55,0.0,0.00,0.00,0.00"
      NumericInfo typeInfo = new NumericInfo(nameSplit[1], values);
      List<NumericInfo> listForType = getListForType(key);
      listForType.add(typeInfo);
    }

    private List<NumericInfo> getListForType(String key) {
      List<NumericInfo> result = get(key);
      if (result == null) {
        result = new ArrayList<>();
        put(key, result);
      }
      return result;
    }

  }

  //------------------------------------------------------------------------------------

  public static class NumericInfoMap extends LinkedHashMap<String, NumericInfo> {

    NumericInfoMap() { }

    public static NumericInfoMap makeNumericInfoMap(String info) {
      String[] infoStrings = new String[] { info };
      NumericInfoListMap stats = new NumericInfoListMap(infoStrings);
      NumericInfoMap result = NumericInfoMap.average(stats);
      return result;
    }

    public static NumericInfoMap average(NumericInfoListMap... maps) {
      NumericInfoMap result = new NumericInfoMap();
      for (NumericInfoListMap map : maps) {
        for (String key : map.keySet()) {
          List<NumericInfo> numericInfos = map.get(key);
          NumericInfo stats = NumericInfo.average(numericInfos.toArray(new NumericInfo[]{}));
          result.put(key, stats);
        }
      }
      return result;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean isFirst = true;
      for (String key : keySet()) {
        if (isFirst) {
          isFirst = false;
        } else {
          sb.append("\n");
        }
        sb.append(key).append(" ").append(get(key));
      }
      return sb.toString();
    }
  }

  //------------------------------------------------------------------------------------

  public static class NumericInfo extends LinkedHashMap<String, Double> {

    final Map<String, String> other = new LinkedHashMap<>();

    public NumericInfo() { }

    public NumericInfo(String headingRow, String valuesRow) {
      String[] headings = headingRow.split(",");
      String[] values   = valuesRow .split(",");
      // "20:07:45-GMT" "ops/sec" ">1ms" ">8ms" ">64ms"
      // "20:07:55"     "0.0"     "0.00" "0.00" "0.00"
      for (int i = 0 ; i < headings.length ; ++i) {
        try {
          put(headings[i], Double.valueOf(values[i]));
        } catch (NumberFormatException e) {
          other.put(headings[i], values[i]);
        }
      }
    }

    public static NumericInfo average(NumericInfo... infos) {
      NumericInfo result = new NumericInfo();
      for (String key : infos[0].keySet()) {
        double average = 0;
        for (NumericInfo info : infos) {
          average += info.get(key);
        }
        average /= infos.length;
        result.put(key, average);
      }
      return result;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean isFirst = true;
      for (String key : keySet()) {
        if (isFirst) {
          isFirst = false;
        } else {
          sb.append(", ");
        }
        sb.append(String.format("%s=%.1f", key, get(key)));
      }
      return sb.toString();
    }
  }

  //====================================================================================

  public static void main(String[] args) {
    OurOptions.instance.doCommandLineOptions("Metering-test", args);
    AerospikeDatabase database = (AerospikeDatabase) OurOptions.instance.database;
    database.connect();
    Metering.instance.stop();
    NumericInfoMap info;
    info = getClusterLatencyInfo(database.client);
    print(info);
    info = getNodeLatencyInfo(database.client.getNodes()[0]);
    print(info);
    OurExecutor.instance.shutdownNow();
  }

  private static void print(NumericInfoMap info) {
    for (String key : info.keySet()) {
      System.out.println(key + " " + info.get(key));
    }
    System.out.println();
  }

}
