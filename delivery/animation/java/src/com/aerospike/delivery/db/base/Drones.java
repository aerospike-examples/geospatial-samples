package com.aerospike.delivery.db.base;

import com.aerospike.delivery.Drone;

import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;


public abstract class Drones {

  public int nextID;
  private int size;

  public Drones() {
    super();
    nextID = Drone.FirstID;
    size = 0;
  }

  public final Drone newDrone() {
    ++size;
    Drone drone = new Drone(this);
    add(drone);
    return drone;
  }

  public abstract void add(Drone drone);

  public final int size() {
    return size;
  }

  //-----------------------------------------------------------------------------------
  // stuff to override


  public void clear() {
    nextID = Drone.FirstID;
    size = 0;
  }

  public abstract int size(Drone.State state);

  public abstract boolean changeState(Drone drone, Drone.State from, Drone.State to);

  public abstract void foreachCached(Predicate<? super Drone> action);

  public abstract BlockingQueue<Drone> makeQueueForRendering();

  public abstract boolean put(Drone drone);

  public abstract Drone getDroneWhereIdIs(int id);

  //-----------------------------------------------------------------------------------
  // our private stuff


}
