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

package com.aerospike.delivery.javafx;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import com.aerospike.delivery.App;
import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.swing.Renderer;

import com.aerospike.delivery.util.OurExecutor;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;


public class MainWindowController {

  public static volatile MainWindowController instance;

  @FXML // ResourceBundle that was given to the FXMLLoader
  private ResourceBundle resources;

  @FXML // URL location of the FXML file that was given to the FXMLLoader
  private URL location;

  @FXML
  private Pane animationPane;

  @FXML
  private Label dronesReady;
  @FXML
  private Label dronesEnroute;
  @FXML
  private Label dronesDelivering;
  @FXML
  private Label dronesDone;
  @FXML
  private Label dronesOffDuty;

  @FXML
  private Label jobsDelivering;
  @FXML
  private Label jobsWaiting;
  @FXML
  private Label jobsOnHold;


  @FXML // This method is called by the FXMLLoader when initialization is complete
  void initialize() {
    instance = this;
    System.out.println("initialized");
    setJobStats  (new Renderer.JobStats());
    setDroneStats(new Renderer.DroneStats());
  }

  public void setJobStats(final Renderer.JobStats jobStats) {
    Platform.runLater((Runnable) () -> {
      jobsWaiting   .setText(String.valueOf(jobStats.waiting));
      jobsDelivering.setText(String.valueOf(jobStats.delivering));
    });
  }

  public void setDroneStats(Renderer.DroneStats stats) {
    Platform.runLater((Runnable) () -> {
      dronesReady     .setText(String.valueOf(stats.ready));
      dronesEnroute   .setText(String.valueOf(stats.enroute));
      dronesDelivering.setText(String.valueOf(stats.delivering));
      dronesDone      .setText(String.valueOf(stats.done));
      dronesOffDuty   .setText(String.valueOf(stats.offDuty));
    });
  }

}
