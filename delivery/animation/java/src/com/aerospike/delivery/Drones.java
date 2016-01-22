package com.aerospike.delivery;

import java.util.function.Predicate;


public abstract class Drones {

  public static int FirstID = 1;
  int nextID = FirstID;
  private int size;

  public Drones() {
    super();
    nextID = FirstID;
    size = 0;
  }

  public final Drone newDrone() {
    ++size;
    Drone drone = new Drone(this);
    add(drone);
    return drone;
  }

  public abstract void add(Drone drone);

  public final boolean changeState(Drone drone, Drone.State newState) {
    return changeState(drone, drone.getState(), newState);
  }


  public final int size() {
    return size;
  }

  //-----------------------------------------------------------------------------------
  // stuff to override


  public void clear() {
    nextID = FirstID;
    size = 0;
  }

  public abstract int size(Drone.State state);

  public abstract boolean changeState(Drone drone, Drone.State from, Drone.State to);

  public abstract void foreach(Predicate<? super Drone> action);

  public abstract boolean put(Drone drone);

  public abstract Drone getDroneWhereIdIs(int id);

  //-----------------------------------------------------------------------------------
  // our private stuff


}
