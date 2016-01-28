package com.aerospike.delivery;

import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Jobs;
import com.aerospike.delivery.swing.Renderer;
import com.aerospike.delivery.swing.Window;
import com.aerospike.delivery.util.DebuggingCountDownLatch;
import com.aerospike.delivery.util.OurExecutor;

import java.util.*;
import java.util.concurrent.CountDownLatch;


public class App {

  static final OurOptions options = OurOptions.instance;

  private static int minimumNumberOfWaitingJobs = 30; // At least 2

  // MapPanel looks at these.
  public static final String appName = "Aerospike Drone Courier";
  public static boolean isDrawingCirclesAndLines; // set dynamically

  // Drone looks at this
  static int slowdownFactorDefault = 100;
  private static int slowdownFactor = slowdownFactorDefault;
  private static int benchmarkSlowdownFactor = 0; // full speed



  static int maxTripsPerDrone;

  static DebuggingCountDownLatch activeDrones;
  static List<Drone> exampleDrones = new ArrayList<>();

  static volatile boolean isLeadDroneStillRunning;

  //========================================================================
  // main


  public static void main(String[] args) throws InterruptedException {
    System.setProperty("apple.awt.application.name", appName);

    if (!options.doCommandLineOptions("delivery", args)) {
      return;
    }

    if (!options.database.connect()) {
      System.err.format("Can't connect to %s.\n", options.database.databaseType());
      return;
    } else {
      System.out.format("Connected to %s.\n", options.database.databaseType());
    }

    start();
  }

  //-----------------------------------------------------------------------------------

  static int slowdownFactor() { return slowdownFactor; }

  public static int backlogExcess(int nbDrones) {
    return (int) Math.max(minimumNumberOfWaitingJobs, nbDrones * 1.1);
  }

  //-----------------------------------------------------------------------------------

  public static void start() throws InterruptedException {
    switch (options.animationMode) {
      case Reset:
        options.database.clear();
        break;
      case Full:
        System.out.println("Running animation.");
        options.database.clear();
        // Fall through.
      case Observe:
        Renderer renderer = Window.createWindow(options.database);
        renderer.start();
        delayMs(1000);
        break;
      case Headless:
        System.out.println("Running animation in headless mode.");
        options.database.clear();
        break;
    }

    try {
      if (options.animationMode == OurOptions.AnimationMode.Reset) {
        System.out.println("Clearing the Aerospike database.");
      } else if (options.animationMode == OurOptions.AnimationMode.Observe) {
        System.out.println("Running in observe mode.");
        App.isDrawingCirclesAndLines = true;
        Thread.sleep(999999999);
      } else {
        if (options.isRunningAdHocTest) {
//          System.out.println("Running ad hoc test instead of normal operation.");
//          new AdHocTest().run();
        } else {
          doTheAnimation();
        }
      }
    } finally {
      options.database.close();
      // todo Wait for the path to go away before stopping the renderer.
//      Window.instance().renderingPanel.renderer.stop();
      // todo Wait for the renderer to stop before shutting down the executor.
//      executor.shutdown();
      // todo Something is still running.
      // Just as well. You don't want the window to disappear summarily.
    }
    return;
  }

  //-----------------------------------------------------------------------------------

  private static void doTheAnimation() throws InterruptedException {
    delayMs(1000);

    int nbJobsToStart = backlogExcess(1);
    for (int i = 0; i < nbJobsToStart; ++i) {
      Job job = options.database.getJobs().newJob(Job.State.Waiting);
      Thread.sleep(67);
    }

    int pauseMs = 1000;

    if (options.isShowingTutorial) {
      activateAndWait(1, 1, options.nbTrips, 0, true);
      if (options.isRunningBenchmark) {
        delayMs(pauseMs * 1);
      }
    }

    int durationNs = 20_000_000;

    if (options.isRunningBenchmark) {
      slowdownFactor = benchmarkSlowdownFactor;
      options.isDrawingJobNumbers = false;
      for (int i = 0 ; i < 99 ; ++i) {
        activateAndWait(options.nbBenchmarkDrones, 1, options.nbTrips, durationNs, true);
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
    options.database.getDrones().foreachCached(drone -> {
      drones.add(drone);
      return true;
    });
    activeDrones = new DebuggingCountDownLatch(false, drones);
  }

  private static void addDrones(int nbDronesRequired, int nbExamples, long durationNs) throws InterruptedException {
    durationNs = Math.min(durationNs, 2_000_000_000 / nbDronesRequired);
    Jobs jobs = options.database.getJobs();
    int nbJobsRequired = backlogExcess(nbDronesRequired) - jobs.size();
    int currentTotal = options.database.getDrones().size();
    CountDownLatch jobPromotionsLatch = new CountDownLatch(currentTotal);
    for (int id = 1 ; id <= currentTotal ; ++id) {
      OurExecutor.executor.submit(() -> {
        jobs.promoteAJobFromOnHold();
        jobPromotionsLatch.countDown();
      });
    }
    CountDownLatch newDronesLatch = new CountDownLatch(Math.max(0, nbDronesRequired - currentTotal));
    CountDownLatch newJobsLatch   = new CountDownLatch(Math.max(0, nbJobsRequired));
    for (int id = currentTotal + 1 ; id <= nbDronesRequired ; ++id) {
      OurExecutor.executor.submit(() -> {
        Drone drone = options.database.getDrones().newDrone();
        newDronesLatch.countDown();
      });
      if (--nbJobsRequired >= 0) {
        OurExecutor.executor.submit(() -> {
          Job job = jobs.newJob(Job.State.Waiting);
          newJobsLatch.countDown();
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
    newDronesLatch.await();
    options.database.getDrones().foreachCached(drone -> {
      if (drone.id <= nbExamples) {
        drone.setExample(true);
        exampleDrones.add(drone);
        return true;
      } else {
        return false;
      }
    });
    jobPromotionsLatch.await();
    newJobsLatch.await();
  }

  private static void activate(long durationNs) throws InterruptedException {
    isLeadDroneStillRunning = true;
    options.database.getDrones().foreachCached(drone -> {
      if (!drone.isActive) {
        OurExecutor.executor.submit(drone);
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
