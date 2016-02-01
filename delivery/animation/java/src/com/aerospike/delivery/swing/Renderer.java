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

import com.aerospike.delivery.*;
import com.aerospike.delivery.db.aerospike.Metering;
import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Drones;
import com.aerospike.delivery.db.base.Jobs;
import com.aerospike.delivery.javafx.MainWindowController;
import com.aerospike.delivery.util.OurExecutor;

import java.awt.image.DataBufferInt;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * Periodically renders drones and jobs into a BufferedImage and copies it to the GUI.
 *
 * Does its work in a thread allocated by the Timer class.
 * Each time it renders it uses the Aerospike scanAll method, which delivers results in multiple threads.
 * The calls to Graphics2D drawing methods are serialized via the synchronized keyword.
 *
 * todo Convert from Swing to JavaFX
 * Use separate layers for job sprites and drone sprites.
 * Update sprite properties (via invokeLater()) from the scan callbacks,
 * which occur in multiple threads.
 */
public class Renderer {

  public static final int maxFramesPerSecond = 15; // Best if this matches the Drone.

  private final int width;
  private final int height;
  private final Drones drones;
  private final Jobs jobs;
  private final Database database;
  private final DefaultColors colors;
  private final long desiredDrawingPeriod;
  private final BufferedImage bufferedImage;
  private final JComponentWithBufferedImage destinationComponent;
  private boolean isDrawingJobNumbers;

  public Renderer(Database database, int width, int height, JComponentWithBufferedImage destinationComponent) {
    super();
    this.database = database;
    this.destinationComponent = destinationComponent;
    this.drones = database.getDrones();
    this.jobs = database.getJobs();
    this.width = width;
    this.height = height;
    this.colors = new DefaultColors();
    bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    desiredDrawingPeriod = (long) (1000_000_000. / maxFramesPerSecond);
    isDrawingJobNumbers = OurOptions.instance.isDrawingJobNumbers;
  }


  public void start() {
    OurExecutor.instance.submit(new DrawingTask());
  }


  private boolean doingStatsUpdate;

  private class DrawingTask implements Runnable {

    private Runnable imageCopier = new ImageCopier();
    long drawingStartTime;
    private long lastStatsUpdateTime;

    DrawingTask() {
      super();
    }

    /**
     * Runs until stopped.
     */
    @Override
    public void run() {
      drawingStartTime = System.nanoTime();
      while (true) {
        ++Metering.instance.renders;
        determineIfTimeToUpdateStats();
        if (!render()) break; // interrupted
        deliverStats();
        long delayArg = calculateDelayUntilNextFrame();
        if (delayArg > 0) {
          // If we're lucky and drawing is quick enough
//            System.out.format("rendering rate %dfps, used %3.0f%% of the period\n", maxFramesPerSecond, 100. * timeTaken / desiredDrawingPeriod);
          delayNs(delayArg);
        } else {
          // Drawing took longer than we wanted.
//          double seconds = timeTaken / 1_000_000_000.;
//          double rate = 1 / seconds;
//          System.out.format("rendering rate %.1ffps\n", rate);
        }
      }
      System.out.println("Rendering stopped.");
    }

    private void determineIfTimeToUpdateStats() {
      long currentTime = System.currentTimeMillis();
      long timeTaken   = currentTime - lastStatsUpdateTime;
      if (timeTaken > 1000) {
        doingStatsUpdate = true;
        lastStatsUpdateTime = currentTime;
      } else {
        doingStatsUpdate = false;
      }
    }

    private long calculateDelayUntilNextFrame() {
      long currentTime = System.nanoTime();
      long timeTaken   = currentTime - drawingStartTime;
      drawingStartTime = currentTime;
      return desiredDrawingPeriod - timeTaken;
    }

    private boolean render() {
      if (database.isConnected()) {
        // The Aerospike client's scanAll method feeds these queues from multiple threads.
        BlockingQueue<Job>   jobsToDraw   = jobs  .makeQueueForRendering();
        BlockingQueue<Drone> dronesToDraw = drones.makeQueueForRendering();
        try {
          draw(jobsToDraw, dronesToDraw);
          SwingUtilities.invokeAndWait(imageCopier);
        } catch (Exception e) {
          return false;
        }
      }
      return true;
    }

  }

  private void deliverStats() {
    if (doingStatsUpdate && MainWindowController.instance != null) {
      MainWindowController.instance.setJobStats(jobStats);
      jobStats = new JobStats();
      MainWindowController.instance.setDroneStats(droneStats);
      droneStats = new DroneStats();
    }
  }

  private static boolean delayNs(long durationNs) {
    long ms = durationNs / 1000000;
    try {
      Thread.sleep(ms, (int) (durationNs % 1000000));
    } catch (InterruptedException e) {
      return false;
    }
    return true;
  }


  interface JComponentWithBufferedImage {
    BufferedImage getBufferedImage();
    void repaint();
  }

  private class ImageCopier implements Runnable {

    @Override
    public void run() {
      copySrcIntoDstAt(bufferedImage, destinationComponent.getBufferedImage(), 0, 0);
      destinationComponent.repaint();
      Toolkit.getDefaultToolkit().sync(); // This may help. Not sure in our case.
    }
  }

  // http://stackoverflow.com/questions/2825837/java-how-to-do-fast-copy-of-a-bufferedimages-pixels-unit-test-included/2826123#2826123
  private static void copySrcIntoDstAt(final BufferedImage src,
                                       final BufferedImage dst, final int dx, final int dy) {
    int[] srcbuf = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
    int[] dstbuf = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
    int width  = src.getWidth();
    int height = src.getHeight();
    int srcoffs = 0;
    int dstoffs = dx + dy * dst.getWidth();
    for (int y = 0 ; y < height ; y++ , dstoffs += dst.getWidth(), srcoffs += width ) {
      System.arraycopy(srcbuf, srcoffs , dstbuf, dstoffs, width);
    }
  }

  private class DefaultColors {
    Color background = new Color(0, 0, 0);
    Color circleFill = new Color(93, 93, 93, 128);
    Color circleLine = new Color(133, 133, 133);

    Color defaultJob    = new Color(255, 170, 0);
    Color candidateJob  = new Color(220, 0, 255);
    Color deliveringJob = candidateJob;
    Color onHoldJob     = new Color(212, 0, 255);
    Color jobTail       = new Color(0, 185, 255);

    final Color defaultDrone = new Color(119, 201, 255);
    final Color exampleDrone = defaultDrone;
    final Color offDutyDrone = defaultDrone;
    final Color readyDrone   = defaultDrone;

    final Color enroutePath    = new Color(0, 153, 225);
    final Color deliveringPath = defaultDrone;
  }

  private void draw(BlockingQueue<Job> jobsToDraw, BlockingQueue<Drone> dronesToDraw) throws InterruptedException {
//      System.out.println("drawStuff " + ++count);
    Graphics2D g2 = prepareToDraw();
    drawTheBackground(g2);
    drawTheLayers(jobsToDraw, dronesToDraw, g2);
    g2.dispose();
  }

  private Graphics2D prepareToDraw() {
    Graphics2D g2 = bufferedImage.createGraphics();
    g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
//      g2.scale(-1, 1);
//      System.out.println(g2.getRenderingHints());
    return g2;
  }

  private void drawTheBackground(Graphics2D g2) {
    g2.setColor(colors.background);
    g2.fillRect(0, 0, width, height);
  }

  private void drawTheLayers(BlockingQueue<Job> jobsToDraw, BlockingQueue<Drone> dronesToDraw, Graphics2D g2) throws InterruptedException {
    // layered from back to front
    // Longitude increases leftwards. Latitude increases upwards.
    g2.translate(-Database.mapWidthPx / 2, -Database.mapHeightPx / 2);
    drawCirclesAndPath(g2);
    drawJobs  (g2, jobsToDraw);
    drawDrones(g2, dronesToDraw);
  }


  //==================================================================================================================

  private void drawCirclesAndPath(Graphics2D g) {
    if (!Conductor.isDrawingCirclesAndLines) {
      return;
    }
    for (int id = 1; ; ++id) {
      Drone drone = drones.getDroneWhereIdIs(id);
      if (drone == null || !drone.isExample) {
        break;
      }
      if (drone.hasJob()) {
        switch (drone.state) {
          default:
            throw new Error("unhandled drone state");
          case Init:
//            throw new Error("Init can't have a job");
          case GotAJob:
          case Departing:
          case EnRoute:
          case ArrivedAtJob:
            drawSearchCircle(g, drone);
            drawPath(g, drone);
            break;
          case Delivering:
          case Delivered:
            drawPath(g, drone);
            break;
          case Ready:
          case Done:
          case OffDuty:
            break;
        }
      } else {
        switch (drone.state) {
          default:
            throw new Error("unhandled drone state");
          case Ready:
            drawSearchCircle(g, drone);
            break;
          case Init:
          case Done:
          case GotAJob:
          case Departing:
          case EnRoute:
          case ArrivedAtJob:
          case Delivering:
          case Delivered:
            break;
          case OffDuty:
            break;
        }
      }
    }
  }

  private void drawSearchCircle(Graphics2D g, Drone drone) {
    if (drone.currentRadius != 0) {
      int x = transformX(drone.startLocation.x);
      int y = transformY(drone.startLocation.y);
      int radius = transformDistance(drone.currentRadius);
      int diameter = 2 * radius;
      g.setColor(colors.circleFill);
      g.fillOval(x - radius, y - radius, diameter, diameter);
      g.setStroke(new BasicStroke(1));
      g.setColor(colors.circleLine);
      g.drawOval(x - radius, y - radius, diameter, diameter);
    }
  }

  private void drawPath(Graphics2D g, Drone drone) {
    Location start = drone.startLocation;
    if (drone.jobOrigin != null) {
      Location begin = drone.jobOrigin;
      Location end   = drone.jobDestination;
      g.setStroke(new BasicStroke(3));
      g.setColor(colors.enroutePath);
      drawLine(g, start, begin);
      g.setStroke(new BasicStroke(3));
      g.setColor(colors.deliveringPath);
      drawLine(g, begin, end);
    }
  }

  //==================================================================================================================

  private void drawJobs(Graphics2D g, BlockingQueue<Job> jobsToDraw) throws InterruptedException {
//    System.out.println("drawJobs");
    while (true) {
      Job job;
      if (false) {
        job = jobsToDraw.take();
      } else {
        // This timeout version allows for a breakpoint on continue.
        while (true) {
          job = jobsToDraw.poll(3, TimeUnit.SECONDS);
          if (job != null) break;
          continue; // breakpoint
        }
      }
//      System.out.println("--> job " + job.id);
      if (job == Job.NullJob) {
        break;
      }
      updateJobStats(job);
      switch (job.getState()) {
        default:
          throw new Error("unhandled job state");
        case Init:
          break;
        case Waiting: {
          Color color;
          if (job.isCandidate()) {
            color = colors.candidateJob;
          } else {
            color = colors.defaultJob;
          }
          drawJob(g, job, color, null);
          break;
        }
        case InProcess:
//              System.out.println("InProcess " + job);
          drawJob(g, job, colors.defaultJob, colors.deliveringJob);
          break;
        case OnHold:
          drawJob(g, job, colors.onHoldJob, null);
          break;
      }
    }
  }

  private synchronized void drawJob(Graphics2D g, Job job, Color jobColor, Color highlightColor) {
    final int r = 6;
    final int r2 = r / 2;
    Location location = job.getLocation();
    int x = transformX(location.x);
    int y = transformY(location.y);
    g.setStroke(new BasicStroke(1));
    g.setColor(jobColor);
    g.fillRect(x - r2, y - r2, r, r);
    if (highlightColor != null) {
      g.setColor(highlightColor);
      g.drawRect(x - r2, y - r2, r, r);
    }
    if (!location.equals(job.previousLocation)) {
      g.setColor(colors.jobTail);
      drawLine(g, job.previousLocation, location);
    }
    if (isDrawingJobNumbers) {
      g.setFont(new Font(null, Font.PLAIN, 8));
      g.setColor(Color.lightGray);
      g.drawString(String.format("%d", job.id), x + 5, y + 4);
    }
  }

  private void drawLine(Graphics2D g, Location begin, Location end) {
    int xStart = transformX(begin.x);
    int yStart = transformY(begin.y);
    int xBegin = transformX(end.x);
    int yBegin = transformY(end.y);
    g.drawLine(xStart, yStart, xBegin, yBegin);
  }

  private JobStats jobStats = new JobStats();

  public static class JobStats {
    public int waiting;
    public int delivering;
    public int onHold;
  }

  private void updateJobStats(Job job) {
    if (doingStatsUpdate) {
      switch (job.state) {
        default:
          throw new Error("unhandled job state");
        case Init:
          break;
        case Waiting:
          ++jobStats.waiting;
          break;
        case InProcess:
          ++jobStats.delivering;
          break;
        case OnHold:
          ++jobStats.onHold;
          break;
      }
    }
  }

  //==================================================================================================================


  private void drawDrones(Graphics2D g, BlockingQueue<Drone> dronesToDraw) throws InterruptedException {
    while (true) {
      Drone drone;
      if (false) {
        drone = dronesToDraw.take();
      } else {
        // This timeout version allows for a breakpoint on continue.
        while (true) {
          drone = dronesToDraw.poll(3, TimeUnit.SECONDS);
          if (drone != null) break;
          continue; // breakpoint
        }
      }
      if (drone == Drone.NullDrone) {
        break;
      }
      updateDroneStats(drone);
      if (drone.hasJob()) {
        switch (drone.state) {
          default:
            throw new Error("unhandled drone state");
          case Init:
//            throw new Error("Init can't have a job");
          case Ready:
          case GotAJob:
          case Departing:
          case EnRoute:
          case ArrivedAtJob:
          case Delivering:
          case Delivered:
            drawDrone(g, drone, drone.getLocation(), colors.defaultDrone);
            break;
          case Done:
          case OffDuty:
            drawDrone(g, drone, drone.getLocation(), colors.readyDrone);
            break;
        }
      } else {
        switch (drone.state) {
          default:
            throw new Error("unhandled drone state");
          case Init:
          case Done:
          case Ready:
            drawDrone(g, drone, drone.getLocation(), colors.readyDrone);
            break;
          case GotAJob:
          case Departing:
          case EnRoute:
          case ArrivedAtJob:
          case Delivering:
          case Delivered:
            break;
          case OffDuty:
            drawDrone(g, drone, drone.getLocation(), colors.offDutyDrone);
            break;
        }
      }
    }
  }

  private synchronized void drawDrone(Graphics2D g, Drone drone, Location location, Color color) {
    final int r = 4;
    int x = transformX(location.x);
    int y = transformY(location.y);
    g.setColor(drone.isExample ? color : colors.exampleDrone);
    fillDiamond(g, x, y, r);
  }

  private synchronized void fillDiamond(Graphics2D g, int x, int y, int r) {
    final int r2 = r / 2 + 1;
    int[] xPoints = {x - r2, x, x + r2, x};
    int[] yPoints = {y, y - r2, y, y + r2};
    g.fillPolygon(xPoints, yPoints, 4);
  }


  // 2 * ???
  private static int transformDistance(double distance) {
    return (int) (distance * Math.min(Database.mapHeightPx, Database.mapWidthPx));
  }

  private int transformX(double positiveIsToTheRight) {
    return width / 2 + (int) -(positiveIsToTheRight * Database.mapWidthPx);
  }

  private int transformY(double positiveIsUp) {
    return height / 2 + (int) -(positiveIsUp * Database.mapHeightPx);
  }


  public static class DroneStats {
    public int ready;
    public int enroute;
    public int delivering;
    public int done;
    public int offDuty;
  }

  private DroneStats droneStats = new DroneStats();

  private void updateDroneStats(Drone drone) {
    if (doingStatsUpdate) {
      switch (drone.state) {
        default:
          throw new Error("unhandled drone state");
        case Init:
          break;
        case Ready:
          ++droneStats.ready;
          break;
        case GotAJob:
        case Departing:
        case EnRoute:
          ++droneStats.enroute;
          break;
        case ArrivedAtJob:
        case Delivering:
          ++droneStats.delivering;
          break;
        case Delivered:
        case Done:
          ++droneStats.done;
          break;
        case OffDuty:
          ++droneStats.offDuty;
          break;
      }
    }
  }

}
