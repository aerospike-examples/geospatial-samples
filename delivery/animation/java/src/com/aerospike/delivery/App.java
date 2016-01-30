package com.aerospike.delivery;

import com.aerospike.delivery.javafx.OurApplication;
import com.aerospike.delivery.util.OurExecutor;


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
        System.out.println("Clearing the Aerospike database.");
        OurOptions.instance.database.clear();
        break;
      case Observe:
        break;
    }

    switch (OurOptions.instance.animationMode) {
      case Reset:
        break;
      case Full:
        break;
      case Observe:
        System.out.println("Running in observe mode.");
        break;
      case Headless:
        System.out.println("Running in headless mode.");
        break;
    }

    Metering.start();

    switch (OurOptions.instance.animationMode) {
      case Reset:
        break;
      case Full:
        // This calls makeAnimation().run() and calls cleanup().
        OurApplication.startUI(this, true);
        break;
      case Observe:
        System.out.println("Running in observe mode.");
        Animation.isDrawingCirclesAndLines = true;
        // This calls cleanup().
        OurApplication.startUI(this, false);
        break;
      case Headless:
        runAnimationAndCleanUp();
        break;
    }

   return;
  }

  private void runAnimationAndCleanUp() {
    try {
      makeAnimation().run();
    } finally {
      cleanup();
    }
  }

  //-----------------------------------------------------------------------------------
  // callbacks from javafx.OurApplication

  public Animation makeAnimation() {
    return new Animation();
  }

  public static void cleanup() {
    OurOptions.instance.database.close();
    OurExecutor.instance.shutdownNow();
  }

}
