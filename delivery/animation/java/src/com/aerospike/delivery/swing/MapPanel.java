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

import com.aerospike.delivery.db.base.Database;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.awt.*;
import java.awt.image.BufferedImage;

public class MapPanel extends JLabel implements Renderer.JComponentWithBufferedImage {

  private final BufferedImage bi;
  public final Renderer renderer;
  public static MapPanel instance;

  public MapPanel(Database database) {
    int width  = Database.mapWidthPx;
    int height = Database.mapHeightPx;
    setMinimumSize(new Dimension(width, height));
    bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    setIcon(new ImageIcon(bi));
    Graphics2D g2 = bi.createGraphics();
    g2.setColor(Color.black);
    g2.fillRect(0, 0, width, height);
    renderer = new Renderer(database, width, height, this);
  }

  @Override
  public void repaint() {
    super.repaint();
  }

  public BufferedImage getBufferedImage() {
    return bi;
  }

}