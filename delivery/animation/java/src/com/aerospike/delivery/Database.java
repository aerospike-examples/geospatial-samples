package com.aerospike.delivery;


import com.aerospike.delivery.aerospike.AerospikeDatabase;
import com.aerospike.delivery.inmemory.InMemoryDatabase;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class Database {

  protected Drones drones;
  protected Jobs    jobs;

  private static Database sharedInstance; // Used only for InMemory

  // Don't change the aspect ratio if you want to use existing data in the database.
  public static final int mapWidthPx  = 800;
  public static final int mapHeightPx = 800;


  public Database() { }

  //----------------------------------------------------------------------------------

  public static Database makeInMemoryDatabase() {
    if (sharedInstance == null) {
      return sharedInstance = new InMemoryDatabase();
    } else if (!(sharedInstance instanceof InMemoryDatabase)) {
      throw new Error("There is already a shared instance for another kind of database.");
    } else {
      return sharedInstance;
    }
  }

  public static Database makeAerospikeDatabase(Parameters parameters, boolean useCaching) {
    return new AerospikeDatabase(parameters, useCaching);
  }

  //----------------------------------------------------------------------------------

  public boolean connect() { return true; }

  public abstract boolean isConnected();

  public void close() { }

  public abstract void clear();


  public Drones getDrones() {
    return drones;
  }

  public Jobs getJobs() {
    return jobs;
  }

  public abstract String databaseType();

  //----------------------------------------------------------------------------------

  public static boolean withWriteLock(ReentrantReadWriteLock lock, Callable<Boolean> action) {
    lock.writeLock().lock();
    try {
      return action.call();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public static boolean withReadLock(ReentrantReadWriteLock lock, Callable<Boolean> action) {
    lock.readLock().lock();
    try {
      return action.call();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      lock.readLock().unlock();
    }
  }

  public static void assertWriteLocked(ReentrantReadWriteLock lock) {
    if (!lock.isWriteLocked()) {
      throw new AssertionError("Call this operation from inside withWriteLocked().");
    }
  }

  public static void assertReadLocked(ReentrantReadWriteLock lock) {
    if (lock.getReadLockCount() == 0) {
      throw new AssertionError("Call this operation from inside withReadLocked().");
    }
  }
}