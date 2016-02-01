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

import com.aerospike.delivery.javafx.OurApplication;
import com.aerospike.delivery.util.OurExecutor;

import java.util.concurrent.TimeUnit;


public class App {

  private static final String appName = "Aerospike Drone Courier";

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
  }

  private static void runAnimationAndCleanUp() {
    try {
      makeAnimation().run();
    } finally {
      cleanup();
    }
  }


  private static Conductor makeAnimation() {
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
