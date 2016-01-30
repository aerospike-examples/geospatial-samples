package com.aerospike.delivery.javafx;


import com.aerospike.delivery.App;
import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.swing.MapPanel;
import com.aerospike.delivery.util.OurExecutor;
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
  static final OurOptions options = OurOptions.instance;
  private static App app;
  private static boolean isDoingAnimation;

  @Override
  public void start(Stage primaryStage) throws Exception {
    URL resource = getClass().getResource("/MainWindow.fxml");
    Parent root = FXMLLoader.load(resource);
    javafx.stage.Window owner = primaryStage.getOwner();
    primaryStage.setTitle(name);
    primaryStage.setX(0);
    primaryStage.setY(0);
    primaryStage.setScene(new Scene(root));
    final SwingNode swingNode = new SwingNode();
    createAndSetSwingContent(primaryStage, swingNode);
    Pane animationContainer = (Pane) root.lookup("#animationPane");
    animationContainer.getChildren().add(swingNode);

    if (isDoingAnimation) {
      Thread.sleep(1000);
      OurExecutor.instance.submit(app.makeAnimation());
    }

  }

  private void createAndSetSwingContent(final Stage primaryStage, final SwingNode swingNode) {
    MapPanel.instance = new MapPanel(OurOptions.instance.database);

    SwingUtilities.invokeLater(() -> {
      swingNode.setContent(MapPanel.instance);
      MapPanel.instance.renderer.start();
      Platform.runLater((Runnable) () -> {
        primaryStage.sizeToScene();
        primaryStage.setResizable(false);
        primaryStage.show();
      });
    });
  }

  public static void startUI(App app, boolean isDoingAnimation) {
    OurApplication.app = app;
    OurApplication.isDoingAnimation = isDoingAnimation;
    // Launch has to be called statically from this class (which extends Animation). Apparently.
    try {
      String[] args = {};
      launch(args);
    } finally {
      // If this GUI is involved, it's in charge of cleanup because the call to launch returns when the GUI is done.
      app.cleanup();
    }
  }

}
