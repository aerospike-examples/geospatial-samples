package com.aerospike.delivery.util;


import java.util.concurrent.ScheduledThreadPoolExecutor;

public class OurExecutor {

  private static int ncores = Runtime.getRuntime().availableProcessors();
  public static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(ncores);

  static {
    OurExecutor.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    OurExecutor.executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
  }

}
