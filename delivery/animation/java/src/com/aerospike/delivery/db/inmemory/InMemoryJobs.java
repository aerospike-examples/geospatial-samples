package com.aerospike.delivery.db.inmemory;

import com.aerospike.delivery.*;
import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Jobs;
import com.aerospike.delivery.util.OurExecutor;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;


public class InMemoryJobs extends Jobs {

  private ConcurrentHashMap<Integer, Job> jobsWaiting;
  private ConcurrentHashMap<Integer, Job> jobsInProcess;
  private ConcurrentHashMap<Integer, Job> jobsOnHold;

  class Metadata extends Jobs.Metadata { }


  public InMemoryJobs() {
    jobsWaiting   = new ConcurrentHashMap<>();
    jobsInProcess = new ConcurrentHashMap<>();
    jobsOnHold    = new ConcurrentHashMap<>();
  }

  @Override
  public void initMetadata(Job job) {
    super.initMetadata(job);
  }


  @Override
  public Job newJob(Job.State state) {
    incrementSize();
    Job job = new Job(this);
    boolean success = Database.withWriteLock(job.lock, () -> {
      return job.setStateAndPut(state);
    });
    if (!success) {
      throw new Error("Trouble in newJob()");
    }
    return job;
  }


  @Override
  public void clear() {
    super.clear();
    jobsWaiting  .clear();
    jobsInProcess.clear();
    jobsOnHold   .clear();
  }


  @Override
  public int size(Job.State state) {
    return getMap(state).size();
  }


  // brute force, no geo tricks.
  // We need only 2, so we can discard the first one if it is the same one we just dropped off.
  //
  @Override
  public void foreachJobNearestTo(Location location, double radius, Predicate<Job> action) {
    super.foreachJobNearestTo(location, radius, action);
    foreach(Job.State.Waiting, job -> {
      double distance = location.distanceTo(job.getOrigin());
      if (distance <= radius) {
        return action.test(job);
      }
      return true;
    });
  }


  @Override
  public void foreach(Job.State state, Predicate<? super Job> action) {
    for (Job job : getMap(state).values()) {
      if (!action.test(job))
        break;
    }
  }


  @Override
  public void foreach(Predicate<? super Job> action) {
    foreach(Job.State.Waiting,   action);
    foreach(Job.State.InProcess, action);
    foreach(Job.State.OnHold,    action);
  }

  @Override
  public BlockingQueue<Job> makeQueueForRendering() {
    // todo Is there something that that takes a sequence of collections and returns an iterable?
    // That would be better.
    BlockingQueue<Job> result = new LinkedBlockingQueue<>();
    OurExecutor.instance.execute(() -> {
      try {
        result.addAll(jobsWaiting.values());
        result.addAll(jobsInProcess.values());
        result.addAll(jobsOnHold.values());
        result.add(Job.NullJob);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    return result;
  }


  public void promoteAJobFromOnHold() {
    foreach(Job.State.OnHold, job -> {
      // Just have to find one, the promote it.
      if (job.changeStateAndPut(Job.State.OnHold, Job.State.Waiting)) {
        // That succeeded, so we're done.
        return false;
      }
      return true;
    });
  }


  @Override
  public boolean putWithNewState(Job job, Job.State from, Job.State to) {
    Database.assertWriteLocked(job.lock);
    if (from == to) {
      if (from != Job.State.Init) {
        System.out.printf("changeState from %s == to %s\n", from, to);
        return false;
      }
    }
    if (job.getState() != from) {
      return false;
    }
    if (from != Job.State.Init && getMap(from).remove(job.id) == null) {
      // Some other drone beat us to it.
      return false;
    }
    job.state = to;
//    if (to == Job.State.OnHold) {
//      job.touch();
//    }
    Map<Integer, Job> toSet = getMap(to);
    if (toSet != null) {
      toSet.put(job.id, job);
    }
    return true;
  }

  @Override
  public Job getJobWhereIdIs(int id) {
    Job result;
    result = jobsWaiting.get(id);
    if (result != null) return result;
    result = jobsInProcess.get(id);
    if (result != null) return result;
    result = jobsOnHold.get(id);
    return null;
  }

  @Override
  public boolean put(Job job) {
    return true;
  }


  //-----------------------------------------------------------------------------------
  // our private stuff

  private Map<Integer, Job> getMap(Job.State state) {
    switch (state) {
      default: throw new Error("unhandled job state");
      case Init:      return null;
      case Waiting:   return jobsWaiting;
      case InProcess: return jobsInProcess;
      case OnHold:    return jobsOnHold;
    }
  }

}
