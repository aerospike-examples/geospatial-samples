package com.aerospike.delivery;

import com.aerospike.client.Log;
import com.aerospike.delivery.swing.Window;
import com.aerospike.delivery.util.DebuggingCountDownLatch;
import org.apache.commons.cli.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;


public class App {

  static Random random;
  private static int minimumNumberOfWaitingJobs = 20; // At least 2

  // MapPanel looks at these.
  public static final String appName = "Aerospike Drone Courier";
  public static boolean isDrawingCirclesAndLines; // set dynamically
  public static boolean isDrawingJobNumbers = false;

  // Drone looks at this
  static int slowdownFactorDefault = 100;
  private static int slowdownFactor = slowdownFactorDefault;
  private static int benchmarkSlowdownFactor = 0; // full speed

  // argument parsing and defaults
  public static Parameters parameters;
  public static Database database;
  private static int nbTrips   = 6;
  private static boolean isShowingTutorial;
  private static boolean isRunningBenchmark;
  private static boolean usingDefaultSeed;
  public static double animationSpeed = 1.0;
  public static Mode mode;
  public static DatabaseToUse databaseToUse;
  private static boolean isRunningAdHocTest;
  private static int nbBenchmarkDrones = 50;


  static int maxTripsPerDrone;

  static int slowdownFactor() { return slowdownFactor; }

  static int backlogExcess(int nbDrones) {
    return (int) Math.max(minimumNumberOfWaitingJobs, nbDrones * 1.1);
  }

  static DebuggingCountDownLatch activeDrones;
  static List<Drone> exampleDrones = new ArrayList<>();

  private static int ncores = Runtime.getRuntime().availableProcessors();
  public static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(ncores);
  static {
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
  }

  static volatile boolean isLeadDroneStillRunning;

  public enum Mode {
    Clear,
    Full,     // app server and observing
    Observe,  // only observe the database
    Headless, // app server only
  }

  enum DatabaseToUse {
    Aerospike,
    Collections, // Java collections
  }

  //========================================================================
  // main


  public static void main(String[] args) throws InterruptedException {
    System.setProperty("apple.awt.application.name", appName);
    if (!doCommandLineOptions(args)) {
      return;
    }

    if (!database.connect()) {
      System.err.format("Can't connect to %s.\n", database.databaseType());
      return;
    } else {
      System.out.format("Connected to %s.\n", database.databaseType());
    }

    start();
  }

  //-----------------------------------------------------------------------------------

  public static boolean doCommandLineOptions(String[] args) {

    try {
      Options options = new Options();
      options.addOption("h", "host",        true,  "Server hostname (default: localhost)");
      options.addOption("p", "port",        true,  "Server port (default: 3000)");
      options.addOption("U", "user",        true,  "User name");
      options.addOption("P", "password",    true,  "Password");
      options.addOption("n", "namespace",   true,  "Namespace (default: demo1)");
      options.addOption("s", "set",         true,  "Set name. (default: jobs)");
      options.addOption("d", "debug",       false, "Run in debug mode.");
      options.addOption(null, "help",       false, "Print usage.");
      options.addOption(null, "headless",   false, "Run headless.");
      options.addOption(null, "observe",    false, "Only observe the database.");
      options.addOption(null, "collections",false, "Use Java collections (default).");
      options.addOption(null, "aerospike",  false, "Use aerospike.");
      options.addOption(null, "tutorial",   false, "Show circles and lines demo.");
      options.addOption(null, "benchmark",  false, "No delays, 500 drones");
      options.addOption(null, "drones",     true,  "Number of benchmark drones (default " + nbBenchmarkDrones + ")");
      options.addOption(null, "fixed-seed", false, "Use fixed random seed.");
      options.addOption(null, "trips",      true,  "Number of trips (default " + nbTrips + ")");
      options.addOption(null, "speed",      true,  "Animation speed (default 1.0)");
      options.addOption(null, "radius",     true,  "Starting radius (default " + Drone.startingRadius + ")");
      options.addOption(null, "clear",      false, "Clear the database.");
//      options.addOption(null, "other", false, "Run an ad hoc test in the code");
//      options.addOption(null, "nocache",    false, "Run aerospike without using a HashMap cache.");

      // todo doesn't complain if it sees things other than the flags above
      CommandLineParser parser = new PosixParser();
      CommandLine cl = parser.parse(options, args, false);

      if (args.length == 0 || cl.hasOption("help")) {
        logUsage(options);
        return false;
      }

      if (cl.hasOption("d")) {
        Log.setLevel(Log.Level.DEBUG);
      }

      if (cl.hasOption("fixed-seed")) {
        usingDefaultSeed = true;
        random = new Random(2);
      } else {
        random = new Random();
      }

      if (!cl.hasOption("tutorial") && !cl.hasOption("benchmark")) {
        isShowingTutorial = true;
        isRunningBenchmark = true;
      } else {
        if (cl.hasOption("tutorial")) {
          isShowingTutorial = true;
        }
        if (cl.hasOption("benchmark")) {
          isRunningBenchmark = true;
        }
      }

      if (cl.hasOption("headless") && cl.hasOption("observe")) {
        System.err.println("Can't run in both modes, headless and observe");
        logUsage(options);
        return false;
      }
      if (cl.hasOption("clear")) {
        databaseToUse = DatabaseToUse.Aerospike;
        mode = Mode.Clear;
      } else if (cl.hasOption("headless")) {
        databaseToUse = DatabaseToUse.Aerospike;
        mode = Mode.Headless;
      } else if (cl.hasOption("observe")) {
        databaseToUse = DatabaseToUse.Aerospike;
        mode = Mode.Observe;
      } else {
        mode = Mode.Full;
      }
      if (cl.hasOption("other")) {
        isRunningAdHocTest = true;
      }

      if (cl.hasOption("aerospike") && cl.hasOption("collections")) {
        System.err.println("Pick either --aerospike or --collections");
        logUsage(options);
        return false;
      }
      if (cl.hasOption("collections")) {
        databaseToUse = DatabaseToUse.Collections;
      } else if (cl.hasOption("aerospike")) {
        databaseToUse = DatabaseToUse.Aerospike;
      } else {
        databaseToUse = DatabaseToUse.Aerospike;
      }

      switch (databaseToUse) {
        case Aerospike:
          parameters = parseServerParameters(cl);
          boolean useCache = false; // !cl.hasOption("nocache");
          database = Database.makeAerospikeDatabase(parameters, useCache);
          break;
        case Collections:
          database = Database.makeInMemoryDatabase();
          break;
      }

      if (cl.hasOption("drones")) {
        String str = cl.getOptionValue("drones");
        nbBenchmarkDrones = Integer.parseInt(str);
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
        Drone.startingRadius = Double.parseDouble(str);
      }

    } catch (Exception ex) {
      System.out.println(ex.getMessage());
      ex.printStackTrace();
    }
    return true;
  }

  /**
   * Write usage to console.
   */
  private static void logUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    StringWriter sw = new StringWriter();
    PrintWriter  pw = new PrintWriter(sw);
    String syntax = App.class.getName() + " [<options>] ";
    formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
    System.out.println(sw.toString());
  }

  /**
   * Parse command line parameters for server stuff.
   */
  private static Parameters parseServerParameters(CommandLine cl) throws Exception {
    String host       = cl.getOptionValue("h", "127.0.0.1");
    String portString = cl.getOptionValue("p", "3000");
    String namespace  = cl.getOptionValue("n", "demo1");
    String set        = cl.getOptionValue("s", "jobs");
    int port = Integer.parseInt(portString);

    if (set.equals("empty")) {
      set = "";
    }

    String user     = cl.getOptionValue("U");
    String password = cl.getOptionValue("P");

    if (user != null && password == null) {
      java.io.Console console = System.console();

      if (console != null) {
        char[] pass = console.readPassword("Enter password:");

        if (pass != null) {
          password = new String(pass);
        }
      }
    }
    return new Parameters(host, port, user, password, namespace, set);
  }

  //-----------------------------------------------------------------------------------

  public static void start() throws InterruptedException {
    switch (mode) {
      case Clear:
        database.clear();
        break;
      case Full:
        System.out.println("Running animation.");
        database.clear();
      case Observe:
        Window.createWindow(database);
        Window.instance().renderingPanel.renderer.start();
        delayMs(1000);
        break;
      case Headless:
        System.out.println("Running animation in headless mode.");
        database.clear();
        break;
    }

    try {
      if (mode == Mode.Clear) {
        System.out.println("Clearing the Aerospike database.");
      } else if (mode == Mode.Observe) {
        System.out.println("Running in observe mode.");
        Thread.sleep(999999999);
      } else {
        if (isRunningAdHocTest) {
          System.out.println("Running ad hoc test instead of normal operation.");
          new AdHocTest().run();
        } else {
          doTheAnimation();
        }
      }
    } finally {
      database.close();
      executor.shutdown();
      // todo stopping the renderer should happen after the path goes away.
//      Window.instance().renderingPanel.renderer.stop();
      // todo Something is still running.
      // Just as well. You don't want the window to disappear summarily.
    }
    return;
  }

  //-----------------------------------------------------------------------------------

  private static void doTheAnimation() throws InterruptedException {
    database.getJobs().addMore(backlogExcess(1));

    int pauseMs = 2000;
    int durationNs = 20_000_000;

    boolean isDrawingCirclesAndLines = databaseToUse == DatabaseToUse.Aerospike ? false : true;

    if (isShowingTutorial) {
      activateAndWait(1, 1, nbTrips, durationNs, isDrawingCirclesAndLines);
      if (isRunningBenchmark) {
        delayMs(pauseMs * 2);
      }
    }

    if (isRunningBenchmark) {
      slowdownFactor = benchmarkSlowdownFactor;
      isDrawingJobNumbers = false;
      for (int i = 0 ; i < 99 ; ++i) {
        activateAndWait(nbBenchmarkDrones, 1, nbTrips, durationNs, isDrawingCirclesAndLines);
        delayMs(pauseMs);
      }
    }
  }

  //-----------------------------------------------------------------------------------

  // Activate all drones, including possibly n-pew ones,
  // and wait for them all to go off duty.
  private static void activateAndWait(int totalDrones, int nbExamples, int maxTrips, long durationNs, boolean isDrawingCirclesAndLines) throws InterruptedException {
    App.isDrawingCirclesAndLines = isDrawingCirclesAndLines;
    maxTripsPerDrone = maxTrips;
    if (totalDrones > 100) {
      durationNs = 0;
    }

    addDrones(totalDrones, nbExamples, durationNs);
    delayMs(1000);
    prepareCountDownLatch(totalDrones);
    activate(durationNs);
//    for (Drone drone : drones.contents) System.out.println(drone);
    activeDrones.await();

    // There's only one, so no need for speed here.
    for (Drone drone : exampleDrones) {
      drone.setExample(false);
    }
    exampleDrones.clear();
    App.isDrawingCirclesAndLines = false;
    return;
  }

  private static void prepareCountDownLatch(int totalDrones) {
    Set<Drone> drones = new HashSet<>();
    database.getDrones().foreach(drone -> {
      drones.add(drone);
      return true;
    });
    activeDrones = new DebuggingCountDownLatch(false, drones);
  }

  private static void addDrones(int nbDronesRequired, int nbExamples, long durationNs) throws InterruptedException {
    durationNs = Math.min(durationNs, 2_000_000_000 / nbDronesRequired);
    Jobs jobs = database.getJobs();
    int nbJobsRequired = backlogExcess(nbDronesRequired) - jobs.size();
    int currentTotal = database.getDrones().size();
    for (int id = 1 ; id <= currentTotal ; ++id) {
      executor.submit(() -> {
        jobs.promoteAJobFromOnHold();
      });
    }
    for (int id = currentTotal + 1 ; id <= nbDronesRequired ; ++id) {
      executor.submit(() -> {
        Drone drone = database.getDrones().newDrone();
      });
      if (--nbJobsRequired >= 0) {
        executor.submit(() -> {
          Job job = jobs.newJob(Job.State.Waiting);
          Database.withWriteLock(job.lock, () -> {
            job.put();
            return true;
          });
        });
      }
      if (durationNs > 0) {
        delayNs(durationNs);
      }
    }
    database.getDrones().foreach(drone -> {
      if (drone.id <= nbExamples) {
        drone.setExample(true);
        exampleDrones.add(drone);
        return true;
      } else {
        return false;
      }
    });
  }

  private static void activate(long durationNs) throws InterruptedException {
    isLeadDroneStillRunning = true;
    database.getDrones().foreach(drone -> {
      if (!drone.isActive) {
        executor.submit(drone);
      }
      if (durationNs > 0) {
        delayNs(durationNs);
      }
      return true;
    });
  }

  static void delayMs(long ms) {
    if (true) {
      try {
        Thread.sleep(ms);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  static void delayNs(long durationNs) {
    long ms = durationNs / 1000000;
    try {
      Thread.sleep(ms, (int) (durationNs % 1000000));
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}
