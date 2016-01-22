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

  final int nbSeconds = 3;

    @Override
    public void run() {
      long time = System.currentTimeMillis();
      while (true) {
        long currentTime = System.currentTimeMillis();
        if (currentTime > time + nbSeconds * 1000) {
          printJobStats();
          printDroneStats();
          time = currentTime;
        }
      }
    }

  private void printJobStats() {
    System.out.format("jobs: circle %3d:%4d   puts %4d   gets %d   scans %2d:%4d\n",
        jobQueryWithinRadius / nbSeconds,
        jobRadiusResults     / nbSeconds,
        jobPuts              / nbSeconds,
        jobGets              / nbSeconds,
        jobScans             / nbSeconds,
        jobScanResults       / nbSeconds
    );
    jobQueryWithinRadius = 0;
    jobRadiusResults = 0;
    jobScans = 0;
    jobScanResults = 0;
    jobPuts = 0;
  }

  private void printDroneStats() {
    System.out.format("drones:                 puts %4d   gets %d   scans %2d:%4d\n",
        dronePuts              / nbSeconds,
        droneGets              / nbSeconds,
        droneScans             / nbSeconds,
        droneScanResults       / nbSeconds
    );
    droneScans = 0;
    droneScanResults = 0;
    dronePuts = 0;
  }
}
