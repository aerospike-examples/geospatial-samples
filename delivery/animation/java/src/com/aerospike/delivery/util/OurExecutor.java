package com.aerospike.delivery.util;


import java.util.concurrent.ScheduledThreadPoolExecutor;

public class OurExecutor {

  private static int ncores = Runtime.getRuntime().availableProcessors();
  public static final ScheduledThreadPoolExecutor instance = new ScheduledThreadPoolExecutor(ncores);

  static {
    OurExecutor.instance.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    OurExecutor.instance.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
  }

}
