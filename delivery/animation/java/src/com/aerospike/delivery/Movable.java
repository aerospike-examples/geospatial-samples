package com.aerospike.delivery;


public abstract class Movable {
  private Location location;

  public Movable() {
    location = Location.makeRandom();
  }

  public Location getLocation() { return location; }

  public void setLocation(Location newValue) { location = newValue; }

}
