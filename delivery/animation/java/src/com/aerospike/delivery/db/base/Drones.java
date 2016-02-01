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
