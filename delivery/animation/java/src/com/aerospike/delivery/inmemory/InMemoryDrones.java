package com.aerospike.delivery.inmemory;

import com.aerospike.delivery.Drone;
import com.aerospike.delivery.Drones;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;


public class InMemoryDrones extends Drones {

  private final ConcurrentHashMap<Integer, Drone> contents;

  public InMemoryDrones() {
    contents = new ConcurrentHashMap<>();
  }

  public void add(Drone drone) {
    contents.put(drone.id, drone);
  }

  @Override
  public void clear() {
    super.clear();
    contents.clear();
  }

  @Override
  public int size(Drone.State state) {
    return 0;
  }

  public boolean put(Drone drone) {
    return true;
  }

  @Override
  public Drone getDroneWhereIdIs(int id) {
    return contents.get(id);
  }

  /**
   * This is simplistic for now.
   * It may have to function more like InMemoryJobs if we need to query
   * jobs in certain states.
   * @param drone
   * @param from
   * @param to
   * @return
   */
  @Override
  public boolean changeState(Drone drone, Drone.State from, Drone.State to) {
    if (to == Drone.State.Init) {
      return true;
    }
    if (from == to) {
      System.out.printf("changeState from %s == to %s\n", from, to);
      return false;
    }
    if (drone.getState() != from) {
      return false;
    }
    return true;
  }


  @Override
  public void foreach(Predicate<? super Drone> action) {
    for (Drone drone : contents.values()) {
      if (!action.test(drone))
        break;
    }
  }

}
