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

import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Jobs;
import com.aerospike.delivery.util.OurExecutor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;


public class Conductor implements Runnable {

  private final OurOptions options;

  public Conductor() {
    options = OurOptions.instance;
  }

  // Renderer looks at this
  public static volatile boolean isDrawingCirclesAndLines; // set dynamically

  // Drone accesses these
  static volatile boolean isLeadDroneStillRunning;
  static int slowdownFactorDefault = 100;
  static int maxTripsPerDrone;
  static CountDownLatch activeDrones;
  static int slowdownFactor() { return slowdownFactor; }

  // Jobs accesses this
  public static int backlogExcess(int nbDrones) {
    return nbDrones + minimumNumberOfWaitingJobs;
  }

  private static int slowdownFactor = slowdownFactorDefault;
  private static int minimumNumberOfWaitingJobs = 30; // At least 2
  private        int swarmSlowdownFactor = 0; // full speed
  private List<Drone> exampleDrones = new ArrayList<>();


  @Override
  public void run() {
    System.out.println("Running the animation.");
    try {
      runInner();
    } catch (Exception e) {
      // todo Tell the UI?
    }
  }

  private void runInner () throws InterruptedException {
    delayMs(1000);

    int nbJobsToStart = backlogExcess(1);
    for (int i = 0; i < nbJobsToStart; ++i) {
      Job job = options.database.getJobs().newJob(Job.State.Waiting);
      Thread.sleep(67);
    }

    int pauseMs = 1000;

    if (options.isShowingTutorial) {
      activateAndWait(1, 1, options.nbTrips, 0, true);
      if (options.isRunningSwarm) {
        delayMs(pauseMs * 1);
      }
    }

    int durationNs = 20_000_000;

    if (options.isRunningSwarm) {
      slowdownFactor = swarmSlowdownFactor;
      options.isDrawingJobNumbers = false;
      for (int i = 0 ; i < 99 ; ++i) {
        activateAndWait(options.nbSwarmDrones, 1, options.nbTrips, durationNs, true);
        delayMs(pauseMs);
      }
    }
  }

  // Activate all drones, including possibly n-pew ones,
  // and wait for them all to go off duty.
  private void activateAndWait(int totalDrones, int nbExamples, int maxTrips, long durationNs, boolean isDrawingCirclesAndLines) throws InterruptedException {
    Conductor.isDrawingCirclesAndLines = isDrawingCirclesAndLines;
    maxTripsPerDrone = maxTrips;
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
    Conductor.isDrawingCirclesAndLines = false;
    return;
  }

  private void prepareCountDownLatch(int totalDrones) {
    Set<Drone> drones = new HashSet<>();
    options.database.getDrones().foreachCached(drone -> {
      drones.add(drone);
      return true;
    });
    activeDrones = new CountDownLatch(drones.size());
  }

  private void addDrones(int nbDronesRequired, int nbExamples, long durationNs) throws InterruptedException {
    durationNs = Math.min(durationNs, 2_000_000_000 / nbDronesRequired);
    Jobs jobs = options.database.getJobs();
    int nbJobsRequired = backlogExcess(nbDronesRequired) - jobs.size();
    int currentTotal = options.database.getDrones().size();
    CountDownLatch jobPromotionsLatch = new CountDownLatch(currentTotal);
    for (int id = 1 ; id <= currentTotal ; ++id) {
      OurExecutor.instance.submit(() -> {
        jobs.promoteAJobFromOnHold();
        jobPromotionsLatch.countDown();
      });
    }
    CountDownLatch newDronesLatch = new CountDownLatch(Math.max(0, nbDronesRequired - currentTotal));
    CountDownLatch newJobsLatch   = new CountDownLatch(Math.max(0, nbJobsRequired));
    for (int id = currentTotal + 1 ; id <= nbDronesRequired ; ++id) {
      OurExecutor.instance.submit(() -> {
        Drone drone = options.database.getDrones().newDrone();
        newDronesLatch.countDown();
      });
      if (--nbJobsRequired >= 0) {
        OurExecutor.instance.submit(() -> {
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

  private void activate(long durationNs) throws InterruptedException {
    isLeadDroneStillRunning = true;
    options.database.getDrones().foreachCached(drone -> {
      if (!drone.isActive) {
        OurExecutor.instance.submit(drone);
      }
      if (durationNs > 0) {
        delayNs(durationNs);
      }
      return true;
    });
  }

  static void delayMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      e.printStackTrace();
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
