package com.aerospike.delivery.swing;

import com.aerospike.delivery.*;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.awt.*;
import java.awt.image.BufferedImage;

public class MapPanel extends JLabel implements Renderer.JComponentWithBufferedImage {

  private final BufferedImage bi;
  public final Renderer renderer;

  MapPanel(Database database) {
    int width  = Database.mapWidthPx;
    int height = Database.mapHeightPx;
    bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    setIcon(new ImageIcon(bi));
    Graphics2D g2 = bi.createGraphics();
    g2.setColor(Color.black);
    g2.fillRect(0, 0, width, height);
    renderer = new Renderer(database, width, height, this);
  }

  public BufferedImage getBufferedImage() {
    return bi;
  }

}