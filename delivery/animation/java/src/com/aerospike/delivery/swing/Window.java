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
