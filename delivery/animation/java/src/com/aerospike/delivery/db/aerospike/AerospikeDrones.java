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

package com.aerospike.delivery.db.aerospike;

import com.aerospike.client.*;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.delivery.*;
import com.aerospike.delivery.db.base.Drones;
import com.aerospike.delivery.util.OurExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;


public class AerospikeDrones extends Drones {

  private final AerospikeDatabase database;
  String setName;
  private List<Drone> cache; // a List so we can iterate in id order.
  private ConcurrentHashMap<Key, Drone> renderCache;
  private WritePolicy writePolicy;


  public AerospikeDrones(AerospikeDatabase database) {
    this.database = database;
    setName = "drones";
    cache = new ArrayList<>();
    renderCache = new ConcurrentHashMap<>();
    makeWritePolicy();
  }

  //-----------------------------
  @Override
  public void clear() {
    super.clear();
    database.clearSet("drones");
    renderCache.clear();
  }

  @Override
  public int size(Drone.State state) {
    return 0;
  }



  @Override
  public void add(Drone drone) {
    put(drone);
  }

  // There is no concurrency issue here, currently.
  @Override
  public boolean changeState(Drone drone, Drone.State from, Drone.State to) {
    if (to == Drone.State.Init) {
      drone.state = to;
      return true;
    }
    if (from == to) {
      System.out.printf("changeState from %s == to %s\n", from, to);
      return false;
    }
    if (drone.getState() != from) {
      return false;
    }
    drone.state = to;
    return put(drone);
  }


  private WritePolicy makeWritePolicy() {
    writePolicy = new WritePolicy();
    writePolicy.recordExistsAction = RecordExistsAction.REPLACE;
    return writePolicy;
  }

  //-----------------------------------------------------------------------------------

  @Override
  public BlockingQueue<Drone> makeQueueForRendering() {
    BlockingQueue<Drone> result = new LinkedBlockingQueue<>();
    OurExecutor.instance.execute(new Runnable() {
      @Override
      public void run() {
        ScanPolicy scanPolicy = new ScanPolicy();
        try {
      /*
       * Scan the entire Set using scannAll(). This will scan each node
       * in the cluster and return the record Digest to the call back object
       */
          if (database.client.isConnected()) {
            ++Metering.droneScans;
            database.scanAllWorkaround(scanPolicy, database.namespace, setName, new OurScanCallback(result));
          }
        } catch (AerospikeException e) {
          int resultCode = e.getResultCode();
          System.err.format("scanAll of %-6s %s %s\n", setName, ResultCode.getResultString(resultCode), e);
        }
        result.add(Drone.NullDrone);
      }

      class OurScanCallback implements ScanCallback {

        private final BlockingQueue<Drone> queue;

        public OurScanCallback(BlockingQueue<Drone> queue) {
          this.queue = queue;
        }

        public void scanCallback(Key key, Record record) {
          ++Metering.droneScanResults;
          Drone drone = get(key, record);
          queue.add(drone);
        }
      }
    });
    return result;
  }

  @Override
  public void foreachCached(Predicate<? super Drone> action) {
    for (Drone drone : cache) {
      if (!action.test(drone)) {
        break;
      }
    }
  }

  //-----------------------------------------------------------------------------------

  // Store only what is needed by Renderer.
  public boolean put(Drone drone) {
    Key key = new Key(database.namespace, setName, drone.id);
    putIntoCache(drone);
    Bin idBin          = new Bin("id", drone.id);
    Bin stateBin       = new Bin("state", drone.state.name());
    Bin locationBin    =     Bin.asGeoJSON("location", drone.getLocation().toGeoJSONPointDouble());
    Bin jobIdBin       = new Bin("jobID", drone.jobId);
    Bin exampleBin     = new Bin("example", drone.isExample);
    Bin radiusBin      = new Bin("radius", drone.currentRadius);
    List<Bin> binsList = new ArrayList<>(Arrays.asList(idBin, stateBin, locationBin, jobIdBin, exampleBin, radiusBin));
    if (drone.startLocation != null) {
      binsList.add(Bin.asGeoJSON("start", drone.startLocation.toGeoJSONPointDouble()));
    }
    if (drone.jobOrigin != null) {
      binsList.add(Bin.asGeoJSON("jobOrigin",      drone.jobOrigin     .toGeoJSONPointDouble()));
      binsList.add(Bin.asGeoJSON("jobDestination", drone.jobDestination.toGeoJSONPointDouble()));
    }
    Bin[] bins = binsList.toArray(new Bin[] {});
    try {
      ++Metering.dronePuts;
      database.client.put(writePolicy, key, bins);
      return true;
    } catch (AerospikeException e) {
      int resultCode = e.getResultCode();
      System.err.format("put to %-6s %s %s\n", setName, ResultCode.getResultString(resultCode), e);
      return false;
    }
  }

  private void putIntoCache(Drone drone) {
    // id 1 goes into cache[0]
    while (true) {
      int index = drone.id - 1;
      if (index < cache.size()) {
        cache.set(index, drone);
        break;
      } else if (index == cache.size()) {
        cache.add(drone);
        break;
      } else {
        cache.add(null); // placeholder
      }
    }
  }

  // Used only when Renderer draws circles and path.
  @Override
  public Drone getDroneWhereIdIs(int id) {
    Key key = new Key(database.namespace, setName, id);
    Policy readPolicy = new Policy();
    ++Metering.droneGets;
    Record record = database.client.get(readPolicy, key);
    return record == null ? null : get(key, record);
  }

  private Drone get(Key key, Record record) {
    int         id             = record.getInt                                        ("id");
    Drone.State state          = Drone.State.stateForName(record.getValue             ("state"));
    Location    location       = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("location"));
    int         jobId          = record.getInt                                        ("jobID");
    boolean     isExample      = record.getBoolean                                    ("example");
    double      radius         = record.getDouble                                     ("radius");
    Location    startLocation  = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("start"));
    Location    jobOrigin      = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("jobOrigin"));
    Location    jobDestination = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("jobDestination"));
    Drone drone = new Drone(this,
        id,
        state,
        location,
        jobId,
        isExample,
        radius,
        startLocation,
        jobOrigin,
        jobDestination
    );
    return drone;
  }

}
