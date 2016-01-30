package com.aerospike.delivery;


import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Drones;
import com.aerospike.delivery.db.base.Jobs;
import com.aerospike.delivery.util.OurExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import static com.aerospike.delivery.Drone.State.*;

public class Drone extends Movable implements Runnable, Comparable<Drone> {

  private final Drones drones; // Where drones are stored
  public static int NullID  = 0;
  public static int FirstID = 1;
  public static final Drone NullDrone = new Drone();

  public ReentrantReadWriteLock lock; // todo Renderer should readLock, vs writeLocks for modifications

  private boolean willGoOffDuty = false;
  private int nbTrips;
  private List<Job> candidates;

  private static final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

  private long circleDelay()        { return slowdownFactor( 5); }
  private long initDelay()          { return slowdownFactor( 0); }
  private long gotAJobDelay()       { return slowdownFactor(10); }
  private long departingDelay()     { return slowdownFactor(10); }
  private long arrivedAtJobDelay()  { return slowdownFactor( 5); }
  private long deliveredDelay()     { return slowdownFactor( 5); }
  static double speed()             { return .0005 * OurOptions.instance.animationSpeed; }

  // Acceleration state
  private int speedStep;
  private final int nbSpeedSteps = 5;
  private double accelerationDistance;
  private double totalDistance;

  private boolean isSearchContinuing = false;
  private int nbSearchTries;

  public final int id;
  volatile boolean isActive;
  public Job job;

  //--- used by Renderer ----------------------------------
  public volatile State state;
  // location from super
  public int jobId;
  //--- used by Renderer if drawing circle and path -------
  public Location startLocation; // Where we were when we got a job
  public Location jobOrigin;
  public Location jobDestination;
  public double   currentRadius; // for display
  public boolean  isExample;

  private Location waypoint; // where we'll be after a delay
  private double radiusEnlargementFactor = 2;

  // For delayed submit in case we want to cancel
  private ScheduledFuture<?> future;


  //-----------------------------------------------------------------------------------

  // The only purpose for a drone made this way is for the Renderer.
  public Drone(Drones drones, int id, State state, Location location, int jobId, boolean isExample, double radius,
               Location startLocation, Location jobOrigin, Location jobDestination) {
    this.drones       = drones;
    this.id            = id;
    this.state         = state;
    super.setLocation   (location);
    this.jobId         = jobId;
    this.isExample     = isExample;
    this.currentRadius = radius;
    this.startLocation = startLocation;
    this.jobOrigin      = jobOrigin;
    this.jobDestination = jobDestination;

  }

  public Drone(Drones drones) {
    super();
    this.drones = drones;
    state = Init;
    waypoint = getLocation(); // todo Should this be here?
    this.id = this.drones.nextID++;
    candidates = new ArrayList<>();
  }

  public Drone() {
    super();
    drones = null;
    id = NullID;
  }

  public Drone copy() {
    Drone result = new Drone(drones, id, state, getLocation(), jobId, isExample, currentRadius, startLocation, jobOrigin, jobDestination);
    return result;
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


  public boolean setState(State newValue) {
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
      return delay * Conductor.slowdownFactorDefault;
    } else {
      return delay * Conductor.slowdownFactor();
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
      jobId          = job.id;
      jobOrigin      = job.origin;
      jobDestination = job.destination;
    } else {
      jobId          = Job.NullID;
      jobOrigin      = null;
      jobDestination = null;
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
        switch (state) {
          case Init:
            // OurExecutor.instance.submit() starts the animation here.
            nbTrips = 0;
            willGoOffDuty = false;

          // Ready
            if (delayInState(initDelay(), Ready))
              return; else continue;
          case Ready:
            startLocation = getLocation(); // used for drawing
            if (stillLookingForJob()) {
              long delay = circleDelay();
              future = OurExecutor.instance.schedule(this, isExample ? delay : 0, timeUnit);
              return;
            }
//            System.out.println("- read " + this);
            if (++nbTrips >= Conductor.maxTripsPerDrone) {
              willGoOffDuty = true;
              if (isExample || id == FirstID) {
                Conductor.isLeadDroneStillRunning = false;
              }
            }

          // GotAJob
            if (delayInState(gotAJobDelay(), GotAJob))
              return; else continue;
          case GotAJob:

          // Departing
            if (delayInState(departingDelay(), Departing))
              return; else continue;
          case Departing:
            speedStep = 0;

          // EnRoute
            setState(EnRoute);
            continue;
          case EnRoute:
            if (stillInMotion(job.getOrigin())) {
              return;
            }
            resetCandidates();

          // ArrivedAtJob
            if (delayInState(arrivedAtJobDelay(), ArrivedAtJob))
              return; else continue;
          case ArrivedAtJob:
            speedStep = 0;

          // Delivering
            setState(Delivering);
            continue;
          case Delivering:
            currentRadius = 0;
            if (stillInMotion(job.getDestination())) {
              return;
            }
            job.previousLocation = job.getLocation();

          // Delivered
            if (delayInState(deliveredDelay(), Delivered))
              return; else continue;
          case Delivered:

          // Done
            setState(Done);
            continue;
          case Done:
            Database.withWriteLock(job.lock, () -> {
              job.updateCoordinates();
              job.setTimeDelivered(Instant.now());
              return job.setStateAndPut(Job.State.Waiting);
            });
            setJob(null);
            if (!willGoOffDuty || Conductor.isLeadDroneStillRunning) {
              setState(Ready);
              continue;
            }

          // OffDuty exit
            isActive = false;
            currentRadius = 0;
            Conductor.activeDrones.countDown(this);
            setState(OffDuty);
            return;

          // OffDuty reeëntry - OurExecutor.instance.submit() resumes the animation here.
          case OffDuty:
            isActive = true;
            setState(Init);
            continue;
        }
        throw new Error("Must not break from this switch.");
      }
    } catch (InterruptedException e) {
      ; // ignore
    }
  }


  // For debugging, but performance is terrible if false!
  boolean isSchedulingInsteadOfDelaying = true;

  private boolean delayInState(long delay, State newState) throws InterruptedException {
    setState(newState);
    if (!isExample) {
      return false;
    }
    if (isSchedulingInsteadOfDelaying) {
      future = OurExecutor.instance.schedule(this, delay, timeUnit);
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
      double distancePerAnimationInterval = OurOptions.animationIntervalMs * currentSpeed;
      double thisSegment = Math.min(distanceToTarget, distancePerAnimationInterval);
      waypoint = getLocation().partWay(thisSegment, whereTo); // for next time
      double intervalPortion = thisSegment / distancePerAnimationInterval;
      future = OurExecutor.instance.schedule(this, (long) (OurOptions.animationIntervalMs * intervalPortion), timeUnit);
//      System.out.format("%s %s %1.3f   %1.3f   %1.3f   %s\n", waypoint, getLocation(), distance, thisSegment, intervalPortion, waypoint);
      result = true;
    }
    if (state == Delivering) {
      Database.withWriteLock(job.lock, () -> {
        Location droneLocation = getLocation();
        job.setLocation(droneLocation);
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
      currentRadius = OurOptions.startingRadius;
      nbSearchTries = 0;
    } else {
      //currentInnerRadius = currentRadius; // not doing donut search
      currentRadius *= radiusEnlargementFactor;
      ++nbSearchTries;
    }
    FoundAction action = new FoundAction(candidates);
    Jobs jobs = OurOptions.instance.database.getJobs();
    jobs.foreachJobNearestTo(getLocation(), currentRadius, action);
    Collections.sort(candidates, new Job.DistanceComparator(getLocation()));
    for (Job job : candidates) {
      if (!job.getLocation().equals(getLocation())) {
        if (Database.withWriteLock(job.lock, () -> {
          job.setTimePickedUp(Instant.now());
          job.setTimeDelivered(null);
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
    return String.format("%d %.5s %s", id, isActive, getLocation());
  }

}