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

package com.aerospike.delivery.db.base;


import com.aerospike.delivery.db.aerospike.AerospikeDatabase;
import com.aerospike.delivery.db.inmemory.InMemoryDatabase;
import org.apache.commons.cli.CommandLine;

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

  public static Database makeInMemoryDatabase(CommandLine commandLine) {
    return makeInMemoryDatabase();
  }

  public static Database makeInMemoryDatabase() {
    if (sharedInstance == null) {
      return sharedInstance = new InMemoryDatabase();
    } else if (!(sharedInstance instanceof InMemoryDatabase)) {
      throw new Error("There is already a shared instance for another kind of database.");
    } else {
      return sharedInstance;
    }
  }

  //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

  public static Database makeAerospikeDatabase(CommandLine commandLine) {
    AerospikeDatabase database = new AerospikeDatabase();
    if (database.parseOptions(commandLine)) {
      return database;
    } else {
      return null;
    }
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