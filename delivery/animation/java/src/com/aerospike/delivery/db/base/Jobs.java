package com.aerospike.delivery.db.base;


import com.aerospike.delivery.App;
import com.aerospike.delivery.Job;
import com.aerospike.delivery.Location;
import com.aerospike.delivery.OurOptions;

import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

public abstract class Jobs {

  public int nextID;
  private int size;

  public class Metadata { }


  public Jobs() {
    nextID = Job.FirstID;
    size = 0;
  }


  public final void addMore(int count) {
    for (int i = 0 ; i < count ; ++i) {
      // newJob adds the job to the Waiting set.
      newJob(Job.State.Waiting);
    }
  }


  // default implementation
  public void initMetadata(Job job) { }


  public abstract Job newJob(Job.State state);

  public void clear() {
    nextID = Job.FirstID;
    size = 0;
  }

  public abstract int size(Job.State state);

  public final int size() {
    return size;
  }

  public void incrementSize() {
    ++size;
  }

  public void foreachJobNearestTo(Location location, double outer, Predicate<Job> action) {
    ensureEnoughJobs();
  }

  public abstract void foreach(Job.State state, Predicate<? super Job> action);

  public abstract void foreach(                 Predicate<? super Job> action);

  public abstract BlockingQueue<Job> makeQueueForRendering();

  public abstract void promoteAJobFromOnHold();

  public abstract boolean putWithNewState(Job job, Job.State from, Job.State to);

  public abstract Job getJobWhereIdIs(int id);


  //-----------------------------------------------------------------------------------
  // private stuff

  private void ensureEnoughJobs() {
    int nbDrones = OurOptions.instance.database.getDrones().size();
    int nbWaiting = size(Job.State.Waiting);
    int count = App.backlogExcess(nbDrones) - nbWaiting;
    if (count > 0) {
      addMore(count);
    }
  }

  public abstract boolean put(Job job);


}
