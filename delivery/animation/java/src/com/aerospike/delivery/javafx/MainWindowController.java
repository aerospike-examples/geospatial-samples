package com.aerospike.delivery.javafx;

import java.net.URL;
import java.util.ResourceBundle;

import com.aerospike.delivery.swing.Renderer;

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
    setDroneStats(new Renderer.DroneStats());
  }

  public void setJobStats(final Renderer.JobStats jobStats) {
    Platform.runLater((Runnable) () -> {
      jobsWaiting   .setText(String.valueOf(jobStats.waiting));
      jobsDelivering.setText(String.valueOf(jobStats.delivering));
//      jobsOnHold    .setText(String.valueOf(jobStats.onHold));
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
