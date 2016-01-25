package com.aerospike.delivery.db.inmemory;


import com.aerospike.delivery.db.base.Database;

public class InMemoryDatabase extends Database {

  public InMemoryDatabase() {
    drones = new InMemoryDrones();
    jobs    = new InMemoryJobs();
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public void clear() {
    jobs   .clear();
    drones.clear();
  }

  @Override
  public String databaseType() {
    return "Java Collections";
  }

}
