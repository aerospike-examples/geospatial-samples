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

package com.aerospike.delivery;


import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.db.base.Jobs;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.aerospike.delivery.Job.State.Waiting;


public class Job extends Movable implements Comparable<Job> {

  private final Jobs jobs;
  public Jobs.Metadata metadata;
  private int droneId;
  public static int NullID = 0;
  public static int FirstID = 1;
  public final int id;
  Location origin;
  Location destination;
  public Location previousLocation;
  public State state;
  public int droneid;
  public final ReentrantReadWriteLock lock; // todo Renderer should readLock
  private boolean isCandidate; // found by the circle query
  public static Job NullJob = new Job();
  public Instant timePickedUp;
  public Instant timeDelivered;

  public Job(Jobs jobs) {
    super();
    this.jobs = jobs;
    this.id = jobs.nextID++;
    state = State.Init;
    origin = getLocation();
    destination = Location.makeRandom();
    previousLocation = getLocation();
    jobs.initMetadata(this);
    lock = new ReentrantReadWriteLock(true);
  }

  public Job(Jobs jobs, Jobs.Metadata metadata, int id, State state,
             Location origin, Location destination, Location location, Location previousLocation,
             int droneId, boolean isCandidate,
             Instant timePickedUp, Instant timeDelivered) {
    super();
    this.jobs = jobs;
    this.metadata = metadata;
    this.id = id;
    this.state = state;
    this.droneId = droneId;
    this.isCandidate = isCandidate;
    this.origin = origin;
    this.destination = destination;
    this.timePickedUp = timePickedUp;
    this.timeDelivered = timeDelivered;
    super.setLocation(location);
    this.previousLocation = previousLocation;
    lock = new ReentrantReadWriteLock(true);
  }

  private Job() {
    jobs = null;
    id = NullID;
    lock = null;
  }

    public boolean put() {
    Database.assertWriteLocked(lock);
    return jobs.put(this);
  }


  void updateCoordinates() {
    Database.assertWriteLocked(lock);
    setOrigin  (getDestination());
    assert getLocation().equals(getDestination());
    setDestination(Location.makeRandom());
  }

  @Override
  public void setLocation(Location newValue) {
    Database.assertWriteLocked(lock);
    previousLocation = getLocation();
    super.setLocation(newValue);
  }

  public Location getOrigin() {
    return origin;
  }

  private void setOrigin(Location origin) {
    Database.assertWriteLocked(lock);
    this.origin = origin;
  }

  void setTimePickedUp(Instant newValue) {
    this.timePickedUp = newValue;
  }

  void setTimeDelivered(Instant newValue) {
    this.timeDelivered = newValue;
  }

  public enum State {
    Init,
    Waiting,
    InProcess,
    OnHold;

    public static State stateForName(Object name) {
      for (State state : State.values()) {
        if (state.name().equals(name)) return state;
      }
      return null;
    }
  }

  public Location getDestination() {
    return destination;
  }

  private void setDestination(Location newValue) {
    Database.assertWriteLocked(lock);
    destination = newValue;
  }

  public State getState() {
    return state;
  }


  public boolean setStateAndPut(Job.State newValue) {
    Database.assertWriteLocked(lock);
    boolean result = changeStateAndPut(state, newValue);
    return result;
  }


  public boolean changeStateAndPut(State from, State to) {
    Database.assertWriteLocked(lock);
    if (to != Waiting) {
      isCandidate = false;
    }
    boolean result = jobs.putWithNewState(this, from, to);
    return result;
  }

  public boolean isCandidate() {
    return isCandidate;
  }

  void setCandidateAndPut(boolean newValue) {
    Database.assertWriteLocked(lock);
    if (isCandidate != newValue) {
      isCandidate = newValue;
      put();
    }
  }


  @Override
  public int compareTo(Job o) {
    return new Integer(id).compareTo(o.id);
  }

  @Override
  public String toString() {
    return String.format("%d %s %s %s %s", id, origin, getLocation(), destination, state);
  }



  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Job job = (Job) o;
    return id == job.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  static class DistanceComparator implements Comparator<Job> {
    private final Location location;

    DistanceComparator(Location location) {
      super();
      this.location = location;
    }

    @Override
    public int compare(Job o1, Job o2) {
      double distance1 = location.distance(o1.getLocation());
      double distance2 = location.distance(o2.getLocation());
      if (distance1 > distance2) return  1;
      if (distance1 < distance2) return -1;
      return 0;
    }
  }
}
