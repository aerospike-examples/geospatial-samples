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
import com.aerospike.client.Language;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.ResultSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.Record;
import com.aerospike.client.task.RegisterTask;
import com.aerospike.client.Value;

public class Around {

	private static Policy policy;
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
		String amenity;

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
			this.amenity = null;
		}
	}

	private static void queryCircle(Parameters params, AerospikeClient client) {
		String locbin = "loc";
		String valbin = "val";
		
		String rgnstr =
			String.format("{ \"type\": \"AeroCircle\", "
						  + "\"coordinates\": [[%.8f, %.8f], %f] }",
						  params.lng, params.lat, params.radius);

		Statement stmt = new Statement();
		stmt.setNamespace(params.namespace);
		stmt.setSetName(params.set);
		stmt.setBinNames(valbin);
		stmt.setFilters(Filter.geoWithin(locbin, rgnstr));

		if (params.amenity != null) {
			stmt.setAggregateFunction("filter_by_amenity", "apply_filter",
									  Value.get(params.amenity));

			ResultSet rs = client.queryAggregate(null, stmt);

			try {
				while (rs.next()) {
					Object result = rs.getObject();
					System.out.println(result);
					count++;
				}
			}
			finally {
				rs.close();
			}
		}
		else {
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
	}
	
	private static void registerUDF(Parameters params,
									AerospikeClient client) throws Exception {
		RegisterTask task =
			client.register(policy, "udf/filter_by_amenity.lua",
							"filter_by_amenity.lua", Language.LUA);
		task.waitTillComplete();
	}

	private static AerospikeClient setupAerospike(Parameters params) throws Exception {	
		ClientPolicy clipolicy = new ClientPolicy();
		clipolicy.user = params.user;
		clipolicy.password = params.password;
		clipolicy.failIfNotConnected = true;

		policy = clipolicy.readPolicyDefault;
		
		return new AerospikeClient(clipolicy, params.host, params.port);
	}

	private static void cleanupAerospike(Parameters params,
										 AerospikeClient client) throws Exception {	
		client.close();
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
		options.addOption("a", "amenity", true, "Filter by amenity");
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
		params.amenity = cl.getOptionValue("a");

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

		try {
			if (params.amenity != null) {
				registerUDF(params, client);
			}

			long t0 = System.nanoTime();
			queryCircle(params, client);
			long t1 = System.nanoTime();

			System.out.printf("found %d records in %.3f milliseconds\n",
							  count, (t1 - t0) / 1e6);
		}
		finally {
			cleanupAerospike(params, client);
		}
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
