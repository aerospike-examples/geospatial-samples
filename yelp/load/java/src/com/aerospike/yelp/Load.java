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

package com.aerospike.yelp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
	
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
	
import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Language;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.Record;
import com.aerospike.client.task.IndexTask;
import com.aerospike.client.Value;

public class Load {

	private static Policy policy;
	private static int count = 0;
	
	static private class Parameters {
		String host;
		int port;
		String user;
		String password;
		String namespace;
		String set;
		String infile;
		String locbin;
		String valbin;
		String mapbin;
		String locndx;

		public Parameters() {
			this.host = "localhost";
			this.port = 3000;
			this.user = "";
			this.password = "";
			this.namespace = "test";
			this.set = "yelp";
			this.infile = null;
			this.locbin = "loc";
			this.valbin = "val";
			this.mapbin = "map";
			this.locndx = null;
		}
	}

	private static void handleLine(Parameters params,
								   AerospikeClient client,
								   String line) throws Exception {
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(line);
		JsonObject obj = element.getAsJsonObject();

		double latitude = obj.get("latitude").getAsDouble();
		double longitude = obj.get("longitude").getAsDouble();
		String busid = obj.get("business_id").getAsString();
		
		JsonObject locobj = new JsonObject();
		locobj.addProperty("type", "Point");
		JsonArray coords = new JsonArray();
		coords.add(longitude);
		coords.add(latitude);
		locobj.add("coordinates", coords);
		Gson gson = new GsonBuilder().create();
		String locstr = gson.toJson(locobj);

		java.lang.reflect.Type mapType =
			new TypeToken<Map<String, Object>>(){}.getType();
		Gson gson2 = new Gson();
		Map<String, Object> mapval = gson2.fromJson(line, mapType);

		Key key = new Key(params.namespace, params.set, busid);

		WritePolicy policy = new WritePolicy();
		Bin locbin = Bin.asGeoJSON(params.locbin, locstr);
		Bin valbin = new Bin(params.valbin, line);
		Bin mapbin = new Bin(params.mapbin, mapval);
		client.put(policy, key, locbin, valbin, mapbin);
		
		if (++count % 1000 == 0) {
			System.err.write('.');
			System.err.flush();
		}
	}
	
	private static void processLines(Parameters params,
									 AerospikeClient client,
									 BufferedReader br) throws Exception {
		String line;
		while ((line = br.readLine()) != null)   {
			handleLine(params, client, line.trim());
		}
	}
	
	private static void createIndexes(Parameters params,
									  AerospikeClient client) throws Exception {
		Policy policy = new Policy();
		policy.timeout = 0; // Do not timeout on index create.
		IndexTask task =
			client.createIndex(policy, params.namespace, params.set,
							   params.locndx, params.locbin,
							   IndexType.GEO2DSPHERE);
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
		String syntax = "usage: " + Load.class.getName()
			+ " [<options>] <infile>";
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
		options.addOption("s", "set", true, "Set name (default: yelp)");
		options.addOption("u", "usage", false, "Print usage");

		CommandLineParser parser = new PosixParser();
		CommandLine cl = parser.parse(options, args, false);

		params.host = cl.getOptionValue("h", "localhost");
		String portString = cl.getOptionValue("p", "3000");
		params.port = Integer.parseInt(portString);
		params.user = cl.getOptionValue("U");
		params.password = cl.getOptionValue("P");
		params.namespace = cl.getOptionValue("n","test");
		params.set = cl.getOptionValue("s", "yelp");

		if (cl.hasOption("u")) {
			usage(options);
			System.exit(0);
		}
		
		String[] remargs = cl.getArgs();
		if (remargs.length != 1) {
			System.out.println("missing infile parameter");
			usage(options);
			System.exit(1);
		}

		params.infile = remargs[0];

		return params;
	}

	private static void run(String[] args) throws Exception {
		Parameters params = parseParameters(args);

		AerospikeClient client = setupAerospike(params);

		params.locndx = params.set + "-loc-index";

		// Open the file early to make sure we can.
		FileInputStream fstream = new FileInputStream(params.infile);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

		createIndexes(params, client);

		try {
			long t0 = System.nanoTime();
			processLines(params, client, br);
			long t1 = System.nanoTime();
			System.err.write('\n');
			System.out.printf("loaded %d points in %.3f seconds\n",
							  count, (t1 - t0) / 1e9);
		}
		finally {
			br.close();
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
