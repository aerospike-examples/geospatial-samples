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


import org.apache.commons.cli.CommandLine;

/**
 * Configuration data.
 */
public class Parameters {
  final String host;
  public final int port;
  final String user;
  final String password;
  public final String namespace;

  private Parameters(String host, int port, String user, String password, String namespace) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.namespace = namespace;
  }

  // todo add these parameters then reparse
  // with stopAtNonOption true so the parse fails if an unknown option argument is encountered.
  static Parameters parseServerParameters(CommandLine cl) {
    String host       = cl.getOptionValue("h", "127.0.0.1");
    String portString = cl.getOptionValue("p", "3000");
    String user       = cl.getOptionValue("U");
    String password   = cl.getOptionValue("P");
    String namespace  = cl.getOptionValue("n", "demo1");

    int port = Integer.parseInt(portString);

    if (user != null && password == null) {
      java.io.Console console = System.console();

      if (console != null) {
        char[] pass = console.readPassword("Enter password:");

        if (pass != null) {
          password = new String(pass);
        }
      }
    }
    try {
      return new Parameters(host, port, user, password, namespace);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "Parameters: host=" + host +
        " port=" + port +
        " ns=" + namespace;
  }

}
