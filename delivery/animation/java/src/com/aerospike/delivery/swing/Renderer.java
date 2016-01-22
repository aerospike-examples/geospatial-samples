package com.aerospike.delivery.swing;

import com.aerospike.delivery.*;
import com.aerospike.delivery.aerospike.AerospikeDatabase;
import com.aerospike.delivery.aerospike.AerospikeJobs;
import com.aerospike.delivery.inmemory.InMemoryDrones;

import java.awt.image.DataBufferInt;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.TimerTask;


/**
 * Periodically renders drones and jobs into a BufferedImage and copies it to the GUI.
 *
 * Does its work in a thread allocated by the Timer class.
 * Each time it renders it uses the Aerospike scanAll method, which delivers results in multiple threads.
 * The calls to Graphics2D drawing methods are serialized via the synchronized keyword.
 */
public class Renderer {

  final int maxFramesPerSecond = 30;

  private final int width;
  private final int height;
  private final Drones drones;
  private final Jobs jobs;
  private final Database database;
  private final DefaultColors colors;
  private final long idealFramePeriod;
  private final BufferedImage bufferedImage;
  private final JComponentWithBufferedImage destinationComponent;
  private boolean isDrawingJobNumbers;
  private boolean isStopping;
  private int fractionShown;

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
    idealFramePeriod = (long) (1000_000_000. / maxFramesPerSecond);
    isDrawingJobNumbers = App.isDrawingJobNumbers;
  }


  public void start() {
    {
      Thread drawThread = new Thread(new DrawingTask());
      drawThread.setName("Renderer");
      drawThread.start();
    }

    // todo this is not the way to do this.
    // The render loop should start these then wait for a Future from each.
    // As soon as it gets from the future, it should submit another.
    {
      Thread droneScanThread = new Thread(new DroneScanningTask());
      droneScanThread.setName("Drone scanning");
      droneScanThread.start();
    }

    {
      Thread jobScanThread = new Thread(new JobScanningTask());
      jobScanThread.setName("Job scanning");
      jobScanThread.start();
    }

  }


  public void stop() {
    isStopping = true;
  }


  // todo desperately needs refactoring
  class JobScanningTask implements Runnable {

    /**
     * Runs until stopped.
     */
    @Override
    public void run() {
      long previousFrameState = System.nanoTime();
      for (int count = 0 ; !isStopping ; ++count) {
        try {
          jobs.refreshRenderCache();

          long currentTime = System.nanoTime();
          long timeTaken = currentTime - previousFrameState;
          previousFrameState = currentTime;
          long delayArg = idealFramePeriod - timeTaken;
          if (delayArg > 0) {
//            System.out.format("rendering rate %dfps, used %3.0f%% of the period\n", maxFramesPerSecond, 100. * timeTaken / idealFramePeriod);
            delayNs(delayArg);
          } else {
            if (false) {
              // This is needs work but seems hopeless.
              // Better to pick a value for fractionShown and stick with it.
              double seconds = timeTaken / 1_000_000_000.;
              double rate = 1 / seconds;
              System.out.format("rendering rate %.1ffps\n", rate);
              // run at an ad-hoc lower frame rate, no delay.
              int higherValue = 4 * fractionShown / 3;
              fractionShown = Math.max(higherValue, fractionShown + 1);
            }
          }
        } catch (Exception ex) {
          ex.printStackTrace();
          isStopping = true;
        }
      }
    }

  }

  class DroneScanningTask implements Runnable {

    /**
     * Runs until stopped.
     */
    @Override
    public void run() {
      long previousFrameState = System.nanoTime();
      for (int count = 0 ; !isStopping ; ++count) {
        try {
          drones.refreshRenderCache();

          long currentTime = System.nanoTime();
          long timeTaken = currentTime - previousFrameState;
          previousFrameState = currentTime;
          long delayArg = idealFramePeriod - timeTaken;
          if (delayArg > 0) {
//            System.out.format("rendering rate %dfps, used %3.0f%% of the period\n", maxFramesPerSecond, 100. * timeTaken / idealFramePeriod);
            delayNs(delayArg);
          } else {
            if (false) {
              // This is needs work but seems hopeless.
              // Better to pick a value for fractionShown and stick with it.
              double seconds = timeTaken / 1_000_000_000.;
              double rate = 1 / seconds;
              System.out.format("rendering rate %.1ffps\n", rate);
              // run at an ad-hoc lower frame rate, no delay.
              int higherValue = 4 * fractionShown / 3;
              fractionShown = Math.max(higherValue, fractionShown + 1);
            }
          }
        } catch (Exception ex) {
          ex.printStackTrace();
          isStopping = true;
        }
      }
    }

  }

  class DrawingTask implements Runnable {

    private Runnable imageCopier = new ImageCopier();

    public DrawingTask() {
      super();
    }

    /**
     * Runs until stopped.
     */
    @Override
    public void run() {
      fractionShown = 1;
      long previousFrameState = System.nanoTime();
      for (int count = 0 ; !isStopping ; ++count) {
        try {
          draw();
          SwingUtilities.invokeAndWait(imageCopier);

          long currentTime = System.nanoTime();
          long timeTaken = currentTime - previousFrameState;
          previousFrameState = currentTime;
          long delayArg = idealFramePeriod - timeTaken;
          if (delayArg > 0) {
//            System.out.format("rendering rate %dfps, used %3.0f%% of the period\n", maxFramesPerSecond, 100. * timeTaken / idealFramePeriod);
            delayNs(delayArg);
          } else {
            if (false) {
              // This is needs work but seems hopeless.
              // Better to pick a value for fractionShown and stick with it.
              double seconds = timeTaken / 1_000_000_000.;
              double rate = 1 / seconds;
              System.out.format("rendering rate %.1ffps\n", rate);
              // run at an ad-hoc lower frame rate, no delay.
              int higherValue = 4 * fractionShown / 3;
              fractionShown = Math.max(higherValue, fractionShown + 1);
            }
          }
        } catch (Exception ex) {
          ex.printStackTrace();
          isStopping = true;
        }
      }
    }

  }

  static boolean delayNs(long durationNs) {
    long ms = durationNs / 1000000;
    try {
      Thread.sleep(ms, (int) (durationNs % 1000000));
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }


  public interface JComponentWithBufferedImage {
    BufferedImage getBufferedImage();
    void repaint();
  }

  class ImageCopier implements Runnable {

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

  class DefaultColors {
    Color background = new Color(0, 0, 0);
    Color circleFill = new Color(60, 60, 60, 128);
    Color circleLine = new Color(80, 80, 80);

    Color defaultJob = new Color(255, 170, 0);
    Color deliveringJob = new Color(255, 0, 150);
    Color onHoldJob = new Color(212, 0, 255);
    Color candidateJob = new Color(178, 0, 255);

    final Color defaultDrone = new Color(119, 201, 255);
    final Color exampleDrone = defaultDrone;
    final Color offDutyDrone = defaultDrone;
    final Color readyDrone = defaultDrone;

    final Color enroutePath = new Color(0, 94, 153);
    final Color deliveringPath = defaultDrone;
  }


  private void draw() throws InterruptedException {
    Graphics2D g2 = bufferedImage.createGraphics();
    g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
    g2.setFont(new Font(null, Font.PLAIN, 8));
    g2.setColor(colors.background);
    g2.fillRect(0, 0, width, height);
    g2.translate(-Database.mapWidthPx / 2, -Database.mapHeightPx / 2);
//        g2.scale(-1, 1);
//        System.out.println(g2.getRenderingHints());
    drawStuff(g2);
    g2.dispose();
  }

  private void drawStuff(Graphics2D g) {
    if (database.isConnected()) {
      // layered from back to front
      drawRegions(g);
      drawCirclesAndPath(g);
      drawJobs(g);
      drawDrones(g);
//      drawFractionShown(g);
    }
  }

  private void drawFractionShown(Graphics2D g2) {
    g2.setFont(new Font(null, Font.PLAIN, 10));
    g2.setColor(new Color(174, 227, 189));
    g2.drawString(String.format("Drawing 1/%d", fractionShown), 3 * width / 2 - 70, 3 * width / 2 - 5);
    int radius = 100;
    int diameter = radius * 2;
  }

  private void drawRegions(Graphics2D g) {
//    g.setColor(Color.lightGray);
//    g.drawLine(0, 0, 0, 0);
  }


  //==================================================================================================================

  public void drawCirclesAndPath(Graphics2D g) {
    if (!App.isDrawingCirclesAndLines) {
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
            drawSearchCircle(g, drone);
            drawPath(g, drone);
            break;
          case ArrivedAtJob:
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
      g.setColor(colors.circleLine);
      g.drawOval(x - radius, y - radius, diameter, diameter);
    }
  }

  private void drawPath(Graphics2D g, Drone drone) {
    Location start = drone.startLocation;
    Location begin = drone.job.getOrigin();
    Location end = drone.job.getDestination();
    int xStart = transformX(start.x);
    int yStart = transformY(start.y);
    int xBegin = transformX(begin.x);
    int yBegin = transformY(begin.y);
    int xEnd = transformX(end.x);
    int yEnd = transformY(end.y);
    g.setColor(colors.enroutePath);
    g.drawLine(xStart, yStart, xBegin, yBegin);
    g.setColor(colors.deliveringPath);
    g.drawLine(xBegin, yBegin, xEnd, yEnd);
  }

  //==================================================================================================================

  public void drawJobs(Graphics2D g) {
    jobs.foreachInRenderCache(job -> {
      if ((job.id - 1) % fractionShown != 0) {
        return true;
      }
      // When the database is Aerospike, this runs in one of many client threads.
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
          drawJob(g, job, job.getLocation(), color, null);
          break;
        }
        case InProcess:
//              System.out.println("InProcess " + job);
          drawJob(g, job, job.getLocation(), colors.defaultJob, colors.deliveringJob);
          break;
        case OnHold:
          drawJob(g, job, job.getLocation(), colors.onHoldJob, null);
          break;
      }
      return true;
    });
  }

  private synchronized void drawJob(Graphics2D g, Job job, Location location, Color jobColor, Color highlightColor) {
    final int r = 6;
    final int r2 = r / 2;
    int x = transformX(location.x);
    int y = transformY(location.y);
    g.setColor(jobColor);
    g.fillRect(x - r2, y - r2, r, r);
    if (highlightColor != null) {
      g.setColor(highlightColor);
      g.drawRect(x - r2, y - r2, r, r);
    }
    if (isDrawingJobNumbers) {
      g.setColor(Color.lightGray);
      g.drawString(String.format("%d", job.id), x + 5, y + 4);
    }
  }

  //==================================================================================================================


  private void drawDrones(Graphics2D g) {
    drones.foreachInRenderCache(drone -> {
      if ((drone.id - 1) % fractionShown != 0) {
        return true;
      }
      // When the database is Aerospike, this runs in one of many client threads.
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
            drawDrone(g, drone, drone.getLocation(), colors.defaultDrone, false);
            break;
          case Done:
          case OffDuty:
            drawDrone(g, drone, drone.getLocation(), colors.readyDrone, false);
            break;
        }
      } else {
        switch (drone.state) {
          default:
            throw new Error("unhandled drone state");
          case Init:
          case Done:
          case Ready:
            drawDrone(g, drone, drone.getLocation(), colors.readyDrone, false);
            break;
          case GotAJob:
          case Departing:
          case EnRoute:
          case ArrivedAtJob:
          case Delivering:
          case Delivered:
            break;
          case OffDuty:
            drawDrone(g, drone, drone.getLocation(), colors.offDutyDrone, false);
            break;
        }
      }
      return true;
    });
  }

  private synchronized void drawDrone(Graphics2D g, Drone drone, Location location, Color color, boolean isMoving) {
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

  int transformX(double positiveIsToTheRight) {
    return width / 2 + (int) -(positiveIsToTheRight * Database.mapWidthPx);
  }

  int transformY(double positiveIsUp) {
    return height / 2 + (int) -(positiveIsUp * Database.mapHeightPx);
  }

  //--------------------------------------------------------------------------------------------------

  // for testing
  public static void main(String[] args) {
    App.doCommandLineOptions(args);
    AerospikeDatabase database = new AerospikeDatabase(App.parameters, true);
    AerospikeJobs    jobs    = (AerospikeJobs)    database.getJobs();
    InMemoryDrones drones = (InMemoryDrones) database.getDrones();
    if (database.connect()) {
      try {
//        database.clear();
        jobs.newJob(Job.State.Init);
        jobs.newJob(Job.State.Init);
        jobs.newJob(Job.State.Init);
        getAndPrintJob(jobs, 1);
        getAndPrintJob(jobs, 2);
        getAndPrintJob(jobs, 3);

        Drone drone = drones.newDrone();
        drone.isExample = true;
        getAndPrintDrone(drones, 1);

        App.isDrawingCirclesAndLines = true;

        int width = 800;
        int height = 800;
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Renderer renderer = new Renderer(database, width, height, new MyJPanel(bi));
        renderer.start();

        Thread.sleep(99999999);
        renderer.stop();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        database.close();
//        database.log.removeAllAppenders();
//        org.apache.log4j.LogManager.shutdown();
      }
    } else {
      System.err.println("Couldn't connect.");
    }
    App.executor.shutdownNow();
  }

  private static void getAndPrintJob(Jobs jobs, int id) {
    Job job = jobs.getJobWhereIdIs(id);
    System.out.printf("got job %s\n", job);
  }

  private static void getAndPrintDrone(Drones drones, int id) {
    Drone drone = drones.getDroneWhereIdIs(id);
    System.out.printf("got drone %s\n", drone);
  }

  private static class MyJPanel extends JPanel implements JComponentWithBufferedImage {

    private final BufferedImage bi;

    MyJPanel(BufferedImage bi) {
      this.bi = bi;
    }

    @Override
    public BufferedImage getBufferedImage() {
      return bi;
    }

  }

}
