package com.aerospike.delivery;

import com.aerospike.delivery.javafx.OurApplication;
import com.aerospike.delivery.util.OurExecutor;

import java.util.concurrent.TimeUnit;


public class App {

  public  static final String appName = "Aerospike Drone Courier";

  //-----------------------------------------------------------------------------------

  public static void main(String[] args) throws InterruptedException {
    System.setProperty("apple.awt.application.name", appName);

    if (!OurOptions.instance.doCommandLineOptions("delivery", args)) {
      return;
    }

    App app = new App();
    app.start();
  }


  //-----------------------------------------------------------------------------------

  public void start() throws InterruptedException {

    if (!OurOptions.instance.database.connect()) {
      System.err.format("Can't connect to %s.\n", OurOptions.instance.database.databaseType());
      return;
    } else {
      System.out.format("Connected to %s.\n", OurOptions.instance.database.databaseType());
    }

    switch (OurOptions.instance.animationMode) {
      case Reset:
      case Full:
      case Headless:
        OurOptions.instance.database.clear();
        break;
      case Observe:
        break;
    }

    switch (OurOptions.instance.animationMode) {
      case Reset:
      case Full:
        break;
      case Observe:
        System.out.println("Running in observe mode.");
        break;
      case Headless:
        System.out.println("Running in headless mode.");
        break;
    }

    switch (OurOptions.instance.animationMode) {
      case Reset:
        break;
      case Full:
        // This calls makeAnimation().run() and calls cleanup().
        OurApplication.startUI();
        break;
      case Observe:
        System.out.println("Running in observe mode.");
        Conductor.isDrawingCirclesAndLines = true;
        // This calls cleanup().
        OurApplication.startUI();
        break;
      case Headless:
        runAnimationAndCleanUp();
        break;
    }

   return;
  }

  private static void runAnimationAndCleanUp() {
    try {
      makeAnimation().run();
    } finally {
      cleanup();
    }
  }


  public static Conductor makeAnimation() {
    return new Conductor();
  }

  //-----------------------------------------------------------------------------------
  // callbacks from javafx.OurApplication

  public static void scheduleAnimation() {
    if (OurOptions.instance.animationMode == OurOptions.AnimationMode.Full) {
      OurExecutor.instance.schedule(makeAnimation(), 1000, TimeUnit.MILLISECONDS);
    }
  }

  public static void cleanup() {
    OurOptions.instance.database.close();
    OurExecutor.instance.shutdownNow();
  }

}
