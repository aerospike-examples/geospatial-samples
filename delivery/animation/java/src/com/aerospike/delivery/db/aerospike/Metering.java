package com.aerospike.delivery.db.aerospike;

import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.util.OurExecutor;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Metering implements Runnable {

  public static final Metering instance = new Metering();

  public static volatile int jobQueryWithinRadius;
  public static volatile int jobRadiusResults;
  public static volatile int jobScans;
  public static volatile int jobScanResults;
  public static volatile int jobPuts;
  public static volatile int jobGets;
  public static volatile int droneScans;
  public static volatile int droneScanResults;
  public static volatile int dronePuts;
  public static volatile int droneGets;

  final int nbSeconds = 3;
  public volatile long renders;
  private volatile Future future;
  private volatile boolean isStopping;


  public static void start() {
    OurExecutor.instance.submit(Metering.instance);
  }

  public synchronized void stop() {
    isStopping = true;
    if (future != null) {
      future.cancel(true);
      future = null;
    }
  }

  @Override
  public synchronized void run() {
    if (isStopping) {
      isStopping = false;
      return;
    }
    future = null;
    printJobStats();
    printDroneStats();
    System.out.println(InfoParser.getClusterLatencyInfo(((AerospikeDatabase)OurOptions.instance.database).client));
    future = OurExecutor.instance.schedule(this, nbSeconds * 1000, TimeUnit.MILLISECONDS);
  }

  private void printJobStats() {
    System.out.format("%d jobs: circle %3d:%4d   puts %4d   gets %2d   scans %2d:%4d\n",
        renders,
        jobQueryWithinRadius / nbSeconds,
        jobRadiusResults     / nbSeconds,
        jobPuts              / nbSeconds,
        jobGets              / nbSeconds,
        jobScans             / nbSeconds,
        jobScanResults       / nbSeconds
    );
    jobQueryWithinRadius = 0;
    jobRadiusResults = 0;
    jobPuts = 0;
    jobGets = 0;
    jobScans = 0;
    jobScanResults = 0;
  }

  private void printDroneStats() {
    System.out.format("%d drones:                 puts %4d   gets %2d   scans %2d:%4d\n",
        renders,
        dronePuts              / nbSeconds,
        droneGets              / nbSeconds,
        droneScans             / nbSeconds,
        droneScanResults       / nbSeconds
    );
    dronePuts = 0;
    droneGets = 0;
    droneScans = 0;
    droneScanResults = 0;
  }

}
