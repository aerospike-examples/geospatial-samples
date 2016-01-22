package com.aerospike.delivery;

public class Metering implements Runnable {

  public static volatile int jobQueryWithinRadius;
  public static volatile int jobScans;
  public static volatile int jobScanResults;
  public static volatile int jobPuts;
  public static volatile int jobGets;
  public static volatile int jobRadiusResults;

  final int nbSeconds = 3;

    @Override
    public void run() {
      long time = System.currentTimeMillis();
      while (true) {
        long currentTime = System.currentTimeMillis();
        if (currentTime > time + nbSeconds * 1000) {
          System.out.format("jobs: circle %d:%d  puts %d  gets %d  scans %d:%d\n",
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
          time = currentTime;
        }
      }
    }
  }
