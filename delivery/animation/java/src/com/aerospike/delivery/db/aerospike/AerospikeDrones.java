package com.aerospike.delivery.db.aerospike;

import com.aerospike.client.*;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.delivery.*;
import com.aerospike.delivery.db.base.Drones;
import com.aerospike.delivery.util.OurExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;


public class AerospikeDrones extends Drones {

  private final AerospikeDatabase database;
  String setName;
  private ConcurrentHashMap<Key, Drone> cache;
  private ConcurrentHashMap<Key, Drone> renderCache;
  private WritePolicy writePolicy;


  public AerospikeDrones(AerospikeDatabase database) {
    this.database = database;
    setName = "drones";
    cache = new ConcurrentHashMap<>();
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
    OurExecutor.executor.execute(new Runnable() {
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
            database.client.scanAll(scanPolicy, database.namespace, setName, new OurScanCallback(result), new String[]{});
          }
        } catch (AerospikeException e) {
          int resultCode = e.getResultCode();
          database.log.info(ResultCode.getResultString(resultCode));
          database.log.debug("Error details: ", e);
        }
        result.add(Drone.NullDrone);
      }

      class OurScanCallback implements ScanCallback {

        private final BlockingQueue<Drone> queue;

        public OurScanCallback(BlockingQueue<Drone> queue) {
          this.queue = queue;
        }

        public void scanCallback(Key key, Record record) throws AerospikeException {
          ++Metering.droneScanResults;
          Drone drone = get(key, record);
          queue.add(drone);
        }
      }
    });
    return result;
  }

  @Override
  public void foreach(Predicate<? super Drone> action) {
    for (Drone drone : cache.values()) {
      if (!action.test(drone)) {
        break;
      }
    }
  }

  //-----------------------------------------------------------------------------------

  Drone get(int id) {
    Key key = new Key(database.namespace, setName, id);
    Policy readPolicy = new Policy();
    Record record = database.client.get(readPolicy, key);
    return record == null ? null : get(key, record);
  }

  // Store only what is needed by Renderer.
  public boolean put(Drone drone) {
    Key key = new Key(database.namespace, setName, drone.id);
    cache.put(key, drone);
    Bin idBin       = new Bin("id",       drone.id);
    Bin stateBin    = new Bin("state",    drone.state.name());
    Bin locationBin =     Bin.asGeoJSON("location", drone.getLocation().toGeoJSONPointDouble());
    Bin jobIdBin    = new Bin("jobID",    drone.jobId);
    Bin exampleBin  = new Bin("example",  drone.isExample);
//    Bin radius  = new Bin("radius",   drone.currentRadius);
//    Bin start   = new Bin("start",    drone.startLocation);
    try {
      ++Metering.dronePuts;
      database.client.put(writePolicy, key, idBin, stateBin, locationBin, jobIdBin, exampleBin);
      return true;
    } catch (AerospikeException e) {
      e.printStackTrace();
      return false;
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
    int id = record.getInt("id");
    Drone.State state = Drone.State.stateForName(record.getValue("state"));
    Location location = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("location"));
    int jobId         = record.getInt("jobID");
    boolean isExample = record.getBoolean("example");
    Drone drone = new Drone(AerospikeDrones.this,
        id,
        state,
        location,
        jobId,
        isExample
    );
    return drone;
  }

}
