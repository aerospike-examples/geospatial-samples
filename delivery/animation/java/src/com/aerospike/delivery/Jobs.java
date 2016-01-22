package com.aerospike.delivery;


import java.util.function.Predicate;

public abstract class Jobs {

  public static int FirstID = 1;
  int nextID;
  private int size;

  protected class Metadata { }


  public Jobs() {
    nextID = FirstID;
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
    nextID = FirstID;
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

  public abstract void promoteAJobFromOnHold();

  public abstract boolean putWithNewState(Job job, Job.State from, Job.State to);

  public abstract Job getJobWhereIdIs(int id);


  //-----------------------------------------------------------------------------------
  // private stuff

  private void ensureEnoughJobs() {
    int nbDrones = App.database.getDrones().size();
    int nbWaiting = size(Job.State.Waiting);
    int count = App.backlogExcess(nbDrones) - nbWaiting;
    if (count > 0) {
      addMore(count);
    }
  }

  public abstract boolean put(Job job);


}
