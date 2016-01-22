package com.aerospike.delivery.swing;


import com.aerospike.delivery.App;
import com.aerospike.delivery.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class Window extends JFrame {

  private static Window window;

  public MapPanel renderingPanel;

  public Window(Database database) {
    super(App.appName);
    OurContentPane contentPane = new OurContentPane(database);
    setContentPane(contentPane);
    enableCmdW(contentPane);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  }

  public static Window instance() {
    return window;
  }

  public static void createWindow(Database database) {
    window = new Window(database);
    window.display();
  }

  private void enableCmdW(OurContentPane contentPane) {
    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    KeyStroke closeKey = KeyStroke.getKeyStroke(KeyEvent.VK_W, mask);
    contentPane.getInputMap().put(closeKey, "closeWindow");
    contentPane.getActionMap().put("closeWindow",
        new AbstractAction("Close Window") {
          @Override
          public void actionPerformed(ActionEvent e) {
            setVisible(false);
            dispose();
          }
        });
  }

  public void display() {
    setResizable(false);
    pack();
    setVisible(true);
  }

//==============================================================================

  class OurContentPane extends JPanel {

    OurContentPane(Database database) {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      renderingPanel = new MapPanel(database);
      add(renderingPanel);
      setOpaque(true); // Content panes must be opaque
    }
  }

//==============================================================================
}
