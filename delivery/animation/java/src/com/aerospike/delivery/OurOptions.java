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

package com.aerospike.delivery;

import com.aerospike.client.Log;
import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.swing.Renderer;
import com.aerospike.delivery.util.OurRandom;
import org.apache.commons.cli.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;


public class OurOptions {

  public static final OurOptions instance = new OurOptions();

  long animationIntervalMs = 1000 / Renderer.maxFramesPerSecond;

  private String appName;
  int nbSwarmDrones = 70;
  int nbTrips = 5;
  double startingRadius = .005;
  double animationSpeed = 1.0;
  public boolean isDrawingJobNumbers = false;
  private int seedForRepeatableRandomBehavior = 6;
  private DatabaseToUse databaseToUse;
  boolean isShowingTutorial;
  boolean isRunningSwarm;
  AnimationMode animationMode;
  public Database database;

  enum AnimationMode {
    Reset,
    Full,     // app server and observing
    Observe,  // only observe the database
    Headless, // app server only
  }

  private enum DatabaseToUse {
    Aerospike,
    Collections; // Java collections

    static DatabaseToUse defaultPersistentDatabase = Aerospike;
  }


  public OurOptions() { }

  public boolean doCommandLineOptions(String appName, String[] args) {
    this.appName = appName;
    try {
      if (doCommandLineOptionsInner(args)) return false;
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
      ex.printStackTrace();
    }
    return true;
  }

  private boolean doCommandLineOptionsInner(String[] args) throws Exception {
    Options cliOptions = new Options();
    cliOptions.addOption("h", "host",        true,  "Server hostname (default: localhost)");
    cliOptions.addOption("p", "port",        true,  "Server port (default: 3000)");
    cliOptions.addOption("U", "user",        true,  "User name");
    cliOptions.addOption("P", "password",    true,  "Password");
    cliOptions.addOption("d", "debug",       false, "Run in debug mode.");
    cliOptions.addOption(null, "help",       false, "Print usage.");
    cliOptions.addOption(null, "headless",   false, "Run headless.");
    cliOptions.addOption(null, "observe",    false, "Only observe the database.");
    cliOptions.addOption(null, "collections",false, "Use Java collections (default).");
    cliOptions.addOption(null, "aerospike",  false, "Use aerospike.");
    cliOptions.addOption(null, "tutorial",   false, "Show circles and lines demo.");
    cliOptions.addOption(null, "swarm",      false, "No delays, 500 drones");
    cliOptions.addOption(null, "drones",     true,  "Number of swarm drones (default " + nbSwarmDrones + ")");
    cliOptions.addOption(null, "fixed-seed", false, "Use fixed random seed.");
    cliOptions.addOption(null, "trips",      true,  "Number of trips (default " + nbTrips + ")");
    cliOptions.addOption(null, "speed",      true,  "Animation speed (default 1.0)");
    cliOptions.addOption(null, "radius",     true,  "Starting radius (default " + startingRadius + ")");
    cliOptions.addOption(null, "reset",      false, "Reset database to empty.");

    // Doesn't complain if the parser sees things other than the options above
    CommandLineParser parser = new PosixParser();
    CommandLine cl = parser.parse(cliOptions, args, false);

    if (args.length == 0 || cl.hasOption("help")) {
      logUsage(appName, cliOptions);
      return true;
    }

    if (cl.hasOption("d")) {
      Log.setLevel(Log.Level.DEBUG);
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    if (cl.hasOption("headless") && cl.hasOption("observe")) {
      System.err.println("Can't run in both modes, headless and observe");
      logUsage(appName, cliOptions);
      return true;
    }
    if (cl.hasOption("reset")) {
      databaseToUse = DatabaseToUse.defaultPersistentDatabase;
      animationMode = AnimationMode.Reset;
    } else if (cl.hasOption("headless")) {
      databaseToUse = DatabaseToUse.defaultPersistentDatabase;
      animationMode = AnimationMode.Headless;
    } else if (cl.hasOption("observe")) {
      databaseToUse = DatabaseToUse.defaultPersistentDatabase;
      animationMode = AnimationMode.Observe;
    } else {
      animationMode = AnimationMode.Full;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    if (cl.hasOption("aerospike") && cl.hasOption("collections")) {
      System.err.println("Pick either --aerospike or --collections");
      logUsage(appName, cliOptions);
      return true;
    }

    if (cl.hasOption("collections")) {
      if (databaseToUse == DatabaseToUse.defaultPersistentDatabase) {
        System.err.println("Choice of database type is incompatible with choice of --headless, --observer, or --clear.");
        return false;
      }
      databaseToUse = DatabaseToUse.Collections;
    } else if (cl.hasOption("aerospike")) {
      databaseToUse = DatabaseToUse.Aerospike;
    } else {
      databaseToUse = DatabaseToUse.defaultPersistentDatabase;
    }

    switch (databaseToUse) {
      case Aerospike:
        database = Database.makeAerospikeDatabase(cl);
        if (database == null) {
          return false;
        }
        break;
      case Collections:
        database = Database.makeInMemoryDatabase(cl);
        if (database == null) {
          return false;
        }
        break;
      default:
        throw new Error("impossible case");
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    if (cl.hasOption("fixed-seed")) {
      OurRandom.instance = new Random(seedForRepeatableRandomBehavior);
    }

    if (!cl.hasOption("tutorial") && !cl.hasOption("swarm")) {
      isShowingTutorial = true;
      isRunningSwarm = true;
    } else {
      if (cl.hasOption("tutorial")) {
        isShowingTutorial = true;
      }
      if (cl.hasOption("swarm")) {
        isRunningSwarm = true;
      }
    }

    if (cl.hasOption("drones")) {
      String str = cl.getOptionValue("drones");
      nbSwarmDrones = Integer.parseInt(str);
    }

    if (cl.hasOption("trips")) {
      String str = cl.getOptionValue("trips");
      nbTrips = Integer.parseInt(str);
    }

    if (cl.hasOption("speed")) {
      String str = cl.getOptionValue("speed");
      animationSpeed = Double.parseDouble(str);
    }

    if (cl.hasOption("radius")) {
      String str = cl.getOptionValue("radius");
      startingRadius = Double.parseDouble(str);
    }
    return false;
  }

  /**
   * Write usage to console.
   */
  private static void logUsage(String appName, Options options) {
    HelpFormatter formatter = new HelpFormatter();
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    String syntax = appName + " [<options>] ";
    formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
    System.out.println(sw.toString());
  }

}
