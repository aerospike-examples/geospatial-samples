package com.aerospike.delivery;

public class Metering implements Runnable {

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

  public static Metering instance;
  final int nbSeconds = 3;
  public volatile long renders;


  public static Metering newInstance() {
    instance = new com.aerospike.delivery.Metering();
    return instance;
  }

  @Override
  public void run() {
    while (true) {
      printJobStats();
      printDroneStats();
      try {
        Thread.sleep(nbSeconds * 1000);
      } catch (InterruptedException e) {
        break;
      }
    }
    System.out.println("Metering stopped.");
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
