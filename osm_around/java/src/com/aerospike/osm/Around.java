/*
 * Copyright 2015 Aerospike, Inc.
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

package com.aerospike.osm;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.Record;

public class Around {

	private static int count = 0;
	
	static private class Parameters {
		String host;
		int port;
		String user;
		String password;
		String namespace;
		String set;
		double lat;
		double lng;
		double radius;

		public Parameters() {
			this.host = "localhost";
			this.port = 3000;
			this.user = "";
			this.password = "";
			this.namespace = "test";
			this.set = "osm";
			this.lat = 0.0;
			this.lng = 0.0;
			this.radius = 2000.0;
		}
	}

	private static void queryCircle(Parameters params, AerospikeClient client) {
		String locbin = "loc";
		String valbin = "val";
		
		String rgnstr =
			String.format("{ \"type\": \"Circle\", "
						  + "\"coordinates\": [[%.8f, %.8f], %f] }",
						  params.lng, params.lat, params.radius);

		Statement stmt = new Statement();
		stmt.setNamespace(params.namespace);
		stmt.setSetName(params.set);
		stmt.setBinNames(valbin);
		stmt.setFilters(Filter.geoWithin(locbin, rgnstr));
		
		RecordSet rs = client.query(null, stmt);
		
		try {
			while (rs.next()) {
				Key key = rs.getKey();
				Record record = rs.getRecord();
				String result = record.getString(valbin);
				System.out.println(result);
				count++;
			}
		}
		finally {
			rs.close();
		}
	}
	
	private static AerospikeClient setupAerospike(Parameters params) throws Exception {	
		ClientPolicy policy = new ClientPolicy();
		policy.user = params.user;
		policy.password = params.password;
		policy.failIfNotConnected = true;
		
		return new AerospikeClient(policy, params.host, params.port);
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		String syntax = "usage: " + Around.class.getName()
			+ " [<options>] -- <latitude> <longitude>";
		formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
		System.out.println(sw.toString());
	}
	
	private static Parameters parseParameters(String[] args) throws ParseException {
		Parameters params = new Parameters();

		Options options = new Options();
		options.addOption("h", "host", true, "Server hostname (default: localhost)");
		options.addOption("p", "port", true, "Server port (default: 3000)");
		options.addOption("U", "user", true, "User name");
		options.addOption("P", "password", true, "Password");
		options.addOption("n", "namespace", true, "Namespace (default: test)");
		options.addOption("s", "set", true, "Set name (default: osm)");
		options.addOption("r", "radius", true, "Radius in meters (default: 2000.0)");
		options.addOption("u", "usage", false, "Print usage");

		CommandLineParser parser = new PosixParser();
		CommandLine cl = parser.parse(options, args, false);

		params.host = cl.getOptionValue("h", "localhost");
		String portString = cl.getOptionValue("p", "3000");
		params.port = Integer.parseInt(portString);
		params.user = cl.getOptionValue("U");
		params.password = cl.getOptionValue("P");
		params.namespace = cl.getOptionValue("n","test");
		params.set = cl.getOptionValue("s", "osm");
		String radiusString = cl.getOptionValue("r", "2000");
		params.radius = Double.parseDouble(radiusString);

		if (cl.hasOption("u")) {
			usage(options);
			System.exit(0);
		}
		
		String[] latlng = cl.getArgs();
		if (latlng.length != 2) {
			System.out.println("missing latitude and longitude parameters");
			usage(options);
			System.exit(1);
		}
		params.lat = Double.parseDouble(latlng[0]);
		params.lng = Double.parseDouble(latlng[1]);

		return params;
	}

	private static void run(String[] args) throws Exception {
		Parameters params = parseParameters(args);

		AerospikeClient client = setupAerospike(params);

		queryCircle(params, client);

		System.out.printf("found %d records\n", count);
	}
	
	public static void main(String[] args) {
		try {
			run(args);
		}
		catch (Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
		}
	}
}
