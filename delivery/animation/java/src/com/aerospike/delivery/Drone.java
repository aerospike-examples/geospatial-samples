package com.aerospike.delivery;


import com.aerospike.delivery.aerospike.AerospikeDrones;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class Drone extends Movable implements Runnable, Comparable<Drone> {

  private final Drones drones; // Where drones are stored
  public static int NullID  = 0;
  public static int FirstID = 1;
  public final int id;

  List<State> debugStates = new ArrayList<>(10);
  volatile boolean isActive;
  private boolean willGoOffDuty = false;
  private int nbTrips;
  private List<Job> candidates;

  static final TimeUnit timeUnit = TimeUnit.MILLISECONDS;
  static long animationIntervalMs = 20;

  //
  private long circleDelay()        { return slowdownFactor( 5); }
  private long initDelay()          { return slowdownFactor(10); }
  private long gotAJobDelay()       { return slowdownFactor(10); }
  private long departingDelay()     { return slowdownFactor(10); }
  private long arrivedAtJobDelay()  { return slowdownFactor( 5); }
  private long deliveredDelay()     { return slowdownFactor( 5); }
  static double speed()             { return .0005 * App.animationSpeed; }
  //
  // Acceleration state
  private int speedStep;
  private final int nbSpeedSteps = 5;
  private double accelerationDistance;
  private double totalDistance;

  // findNearbyJob()
  private static final double startingRadius = .05;
  private boolean isSearchContinuing = false;
  private int nbSearchTries;

  public Job job;

  //--- used by Renderer ----------------------------------
  public volatile State state;
  // location from super
  public int jobId;
  //--- used by Renderer if drawing circle and path -------
  public Location startLocation; // Where we were when we got a job
  public double currentRadius; // for display
  public boolean isExample;

  Location waypoint; // where we'll be after a delay
  private double radiusEnlargementFactor = 2;

  // For delayed submit in case we want to cancel
  ScheduledFuture<?> future;


  //-----------------------------------------------------------------------------------

  // A drone object made this way is usable only by the Renderer.
  public Drone(AerospikeDrones aerospikeDrones, int id, State state, Location location, int jobId, boolean isExample) {
    drones = aerospikeDrones;
    this.id = id;
    this.state = state;
    super.setLocation(location);
    this.jobId = jobId;
    this.isExample = isExample;
  }

  public Drone(Drones drones) {
    super();
    this.drones = drones;
    state = State.Init;
    waypoint = getLocation(); // todo Should this be here?
    this.id = this.drones.nextID++;
    candidates = new ArrayList<>();
  }

  public enum State {
    Init,
    Ready,
    GotAJob,
    Departing,
    EnRoute,
    ArrivedAtJob,
    Delivering,
    Delivered,
    Done,
    OffDuty;

    public static State stateForName(Object name) {
      for (State state : State.values()) {
        if (state.name().equals(name)) return state;
      }
      return null;
    }
  }


  public State getState() {
    return state;
  }


  public boolean setState(Drone.State newValue) {
    return drones.changeState(this, state, newValue);
  }

  @Override
  public void setLocation(Location newValue) {
    super.setLocation(newValue);
    put();
  }

  public void setExample(boolean example) {
    this.isExample = example;
    put();
  }

  public boolean put() {
    return drones.put(this);
  }

  private long slowdownFactor(int delay) {
    if (isExample) {
      return delay * App.slowdownFactorDefault;
    } else {
      return delay * App.slowdownFactor();
    }
  }

  public boolean hasJob() {
    return jobId != NullID;
  }

  public Job getJob() {
    return job;
  }

  public void setJob(Job newValue) {
    job = newValue;
    if (newValue != null) {
      jobId = job.id;
    } else {
      jobId = Job.NullID;
    }
    put();
  }

  @Override
  public void run() {
    try {
      runSafe();
    } catch (Throwable e) {
      System.err.println("Drone run problem: " + e.getMessage());
    }
  }

  private void runSafe() {
    isActive = true;
    try {
      while (true) {
        if (state == State.Ready) debugStates.clear();
        debugStates.add(state);
        switch (state) {
          case Init:
            nbTrips = 0;
            willGoOffDuty = false;
            if (delayAndThen(initDelay(), State.Ready))
              return; else continue;
          case Ready:
            startLocation = getLocation(); // used for drawing
            if (stillLookingForJob()) {
              long delay = circleDelay();
              future = App.executor.schedule(this, isExample ? delay : 0, timeUnit);
              return;
            }
//            System.out.println("- read " + this);
            if (++nbTrips >= App.maxTripsPerDrone) {
              willGoOffDuty = true;
            }
            if (delayAndThen(gotAJobDelay(), State.GotAJob))
              return; else continue;
          case GotAJob:
            if (delayAndThen(departingDelay(), State.Departing))
              return; else continue;
          case Departing:
            setState(State.EnRoute);
            speedStep = 0;
            continue;
          case EnRoute:
            if (stillInMotion(job.getOrigin())) {
              return;
            }
            resetCandidates();
            if (delayAndThen(arrivedAtJobDelay(), State.ArrivedAtJob)) return;
            else continue;
          case ArrivedAtJob:
            setState(State.Delivering);
            speedStep = 0;
            continue;
          case Delivering:
            currentRadius = 0;
            if (stillInMotion(job.getDestination())) {
              return;
            }
            if (delayAndThen(deliveredDelay(), State.Delivered))
              return; else continue;
          case Delivered:
            setState(State.Done);
            continue;
          case Done:
            if (willGoOffDuty && (isExample || id == FirstID)) {
              App.isLeadDroneStillRunning = false;
            }
            if (willGoOffDuty && !App.isLeadDroneStillRunning) {
//              System.out.println("- off1 " + this);
              Database.withWriteLock(job.lock, () -> {
                job.updateCoordinates();
                return job.put();
              });
              setState(State.OffDuty);
            } else {
//              System.out.println("- done " + this);
              Database.withWriteLock(job.lock, () -> {
                job.updateCoordinates();
                return job.setStateAndPut(Job.State.Waiting);
              });
              job = null;
              setState(State.Ready);
            }
            continue;
          case OffDuty:
//            System.out.println("- off2 " + this);
            Database.withWriteLock(job.lock, () -> {
              return job.setStateAndPut(Job.State.Waiting);
            });
//            System.out.println("- off3 " + this);
            job = null;
            isActive = false;
            setState(State.Init);
            currentRadius = 0;
//            System.out.println("- off4 " + this);
            App.activeDrones.countDown(this);
            return;
        }
        throw new Error("Must not break from this switch.");
      }
    } catch (InterruptedException e) {
      ; // ignore
    }
  }

  class TakeOffHold implements Runnable {
    private final Job job;

    public TakeOffHold(Job job) {
      this.job = job;
    }

    @Override
    public void run() {
      job.setStateAndPut(Job.State.Waiting);
    }
  }

  // Performance is terrible if false!
  boolean isSchedulingInsteadOfDelaying = true;

  private boolean delayAndThen(long delay, State newState) throws InterruptedException {
    setState(newState);
    if (!isExample) {
      return false;
    }
    if (isSchedulingInsteadOfDelaying) {
      future = App.executor.schedule(this, delay, timeUnit);
      return true;
    } else {
      Thread.sleep(delay);
      return false;
    }
  }

  /**
   * Schedule a move to the next waypoint of the motion animation.
   *
   * @param whereTo
   * @return true if a delay is scheduled; otherwise, go to the next state
   */
  boolean stillInMotion(Location whereTo) {
    boolean result;
    if (waypoint.equals(whereTo)) {
      setLocation(whereTo);
      result = false;
    } else {
      // location == waypoint the first time through here.
      setLocation(waypoint);
      double distanceToTarget = getLocation().distanceTo(whereTo);
      if (speedStep == 0) {
        speedStep = 1;
        totalDistance = distanceToTarget;
        accelerationDistance = 0;
//        System.out.println("start");
      } else {
        if (accelerationDistance == 0) {
          if (speedStep < nbSpeedSteps) {
            ++speedStep;
//            System.out.format("%d %f\n", speedStep, distanceToTarget);
          } else {
            accelerationDistance = totalDistance - distanceToTarget;
//            System.out.format("%d %f %f\n", speedStep, accelerationDistance, distanceToTarget);
          }
        } else if (false) {
          if (distanceToTarget <= accelerationDistance) {
            if (speedStep > 1) {
              --speedStep;
//              System.out.format("%d %f\n", speedStep, distanceToTarget);
            }
          }
        }
      }
      double currentSpeed = (isExample ? 1 : .5) * speed() * speedStep / nbSpeedSteps;
//      System.out.printf("%d %f %f\n", speedStep, distance, currentSpeed);
      double distancePerAnimationInterval = animationIntervalMs * currentSpeed;
      double thisSegment = Math.min(distanceToTarget, distancePerAnimationInterval);
      waypoint = getLocation().partWay(thisSegment, whereTo); // for next time
      double intervalPortion = thisSegment / distancePerAnimationInterval;
      future = App.executor.schedule(this, (long) (animationIntervalMs * intervalPortion), timeUnit);
//      System.out.format("%s %s %1.3f   %1.3f   %1.3f   %s\n", waypoint, getLocation(), distance, thisSegment, intervalPortion, waypoint);
      result = true;
    }
    if (state == State.Delivering) {
      Database.withWriteLock(job.lock, () -> {
        job.setLocation(Drone.this.getLocation());
        job.put();
        return true;
      });
    }
    drones.put(this);
    return result;
  }

  private boolean stillLookingForJob() {
    setJob(findNearbyJob());
    return job == null;
  }

  private void resetCandidates() {
    for (Job job : candidates) {
      Database.withWriteLock(job.lock, () -> {
        job.setCandidateAndPut(false);
        return true;
      });
    }
    candidates.clear();
  }

  private Job findNearbyJob() {
//    resetCandidates(); // should not be necessary
    if (!isSearchContinuing) {
      currentRadius = startingRadius;
      nbSearchTries = 0;
    } else {
      //currentInnerRadius = currentRadius; // not doing donut search
      currentRadius *= radiusEnlargementFactor;
      ++nbSearchTries;
    }
    FoundAction action = new FoundAction(candidates);
    Jobs jobs = App.database.getJobs();
    jobs.foreachJobNearestTo(getLocation(), currentRadius, action);
    Collections.sort(candidates, new Job.DistanceComparator(getLocation()));
    for (Job job : candidates) {
      if (!job.getLocation().equals(getLocation())) {
        if (Database.withWriteLock(job.lock, () -> {
          return job.changeStateAndPut(Job.State.Waiting, Job.State.InProcess);
        })) {
          isSearchContinuing = false;
          return job;
        }
        // Some other drone got it.
      }
    }
    isSearchContinuing = true;
//      System.out.println(nbSearchTries + " at radius " + currentRadius + (isSearchContinuing ? "" : " done"));
    return null;
  }


  class FoundAction implements Predicate<Job> {
    private final List<Job> foundJobs;

    public FoundAction(List<Job> foundJobs) {
      super();
      this.foundJobs = foundJobs;
    }

    @Override
    public boolean test(Job job) {
      if (isExample) {
        Database.withWriteLock(job.lock, () -> {
          job.setCandidateAndPut(true);
          return true;
        });
      }
      foundJobs.add(job);
      return true;
    }
  }


  @Override
  public int compareTo(Drone o) {
    return new Integer(id).compareTo(o.id);
  }

  @Override
  public String toString() {
    return String.format("%d %.5s %s %s", id, isActive, getLocation(), debugStates.size() > 0 ? debugStates.get(debugStates.size() - 1) : "");
  }

}