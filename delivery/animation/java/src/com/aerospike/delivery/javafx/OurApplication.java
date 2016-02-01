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


import com.aerospike.delivery.App;
import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.swing.MapPanel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import javax.swing.*;
import java.net.URL;

public class OurApplication extends Application {

  public static final String name = "Aerospike Drone Courier";

  @Override
  public void start(Stage primaryStage) throws Exception {
    URL resource = getClass().getResource("/MainWindow.fxml");
    Parent root = FXMLLoader.load(resource);
    primaryStage.setTitle(name);
    primaryStage.setX(0);
    primaryStage.setY(0);
    primaryStage.setScene(new Scene(root));
    final SwingNode swingNode = new SwingNode();
    createAndSetSwingContent(primaryStage, swingNode);
    Pane animationContainer = (Pane) root.lookup("#animationPane");
    animationContainer.getChildren().add(swingNode);
  }

  private void createAndSetSwingContent(final Stage primaryStage, final SwingNode swingNode) {
    MapPanel.instance = new MapPanel(OurOptions.instance.database);

    SwingUtilities.invokeLater(() -> {
      swingNode.setContent(MapPanel.instance);
      MapPanel.instance.renderer.start();
      Platform.runLater(() -> {
        primaryStage.sizeToScene();
        primaryStage.setResizable(false);
        primaryStage.show();
        App.scheduleAnimation();
      });
    });
  }

  public static void startUI() {
    // Launch has to be called statically from this class (which extends Application). Apparently.
    try {
      String[] args = {};
      launch(args);
    } finally {
      // If this GUI is involved, it's in charge of cleanup because the call to launch returns when the GUI is done.
      App.cleanup();
    }
  }

}
