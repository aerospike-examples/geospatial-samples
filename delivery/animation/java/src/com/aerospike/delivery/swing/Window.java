package com.aerospike.delivery.swing;


import com.aerospike.delivery.OurOptions;
import com.aerospike.delivery.db.base.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

public class Window extends JFrame {

  private static Window window;

  private Window(Database database) {
    super(OurOptions.instance.appName);
    MapPanel.instance = new MapPanel(database);
    setContentPane(MapPanel.instance);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  }

  public static Window instance() {
    return window;
  }

  public static Renderer createWindow(Database database) {
    window = new Window(database);
    window.display();
    return MapPanel.instance.renderer;
  }

  public void display() {
    setResizable(false);
    pack();
    setVisible(true);
  }
}
