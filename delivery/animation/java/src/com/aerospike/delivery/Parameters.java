package com.aerospike.delivery;
/*
 * Copyright 2012-2015 Aerospike, Inc.
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


import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;

/**
 * Configuration data.
 */
public class Parameters {
  public final String host;
  public final int port;
  public final String user;
  public final String password;
  public final String namespace;
  final String set;
  WritePolicy writePolicy;
  Policy policy;
  boolean singleBin;
  boolean hasGeo;
  boolean hasUdf;
  boolean hasLargeDataTypes;

  protected Parameters(String host, int port, String user, String password, String namespace, String set) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.namespace = namespace;
    this.set = set;
  }

  @Override
  public String toString() {
    return "Parameters: host=" + host +
        " port=" + port +
        " ns=" + namespace +
        " set=" + set +
        " single-bin=" + singleBin;
  }

  public String getBinName(String name)
  {
    // Single bin servers don't need a bin name.
    return singleBin ? "" : name;
  }
}
