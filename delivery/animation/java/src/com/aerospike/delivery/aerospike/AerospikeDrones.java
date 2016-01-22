package com.aerospike.delivery.aerospike;

import com.aerospike.client.*;
import com.aerospike.client.policy.*;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.delivery.Drone;
import com.aerospike.delivery.Drones;
import com.aerospike.delivery.Job;
import com.aerospike.delivery.Location;

import java.util.function.Predicate;


public class AerospikeDrones extends Drones {

  private final AerospikeDatabase database;
  String setName;
  private WritePolicy writePolicy;


  public AerospikeDrones(AerospikeDatabase database) {
    this.database = database;
    setName = "drones";
    makeWritePolicy();
  }

  //-----------------------------
  @Override
  public void clear() {
    super.clear();
    database.clearSet("drones");
  }

  @Override
  public int size(Drone.State state) {
    return 0;
  }



  @Override
  public void add(Drone drone) {
    put(drone);
  }

  @Override
  public boolean changeState(Drone drone, Drone.State from, Drone.State to) {
    return false;
  }


  private WritePolicy makeWritePolicy() {
    writePolicy = new WritePolicy();
    writePolicy.recordExistsAction = RecordExistsAction.REPLACE;
    writePolicy.generationPolicy = GenerationPolicy.EXPECT_GEN_EQUAL;
    return writePolicy;
  }


  @Override
  public void foreach(Predicate<? super Drone> action) {
    ScanPolicy scanPolicy = new ScanPolicy();
    try {
      /*
       * Scan the entire Set using scannAll(). This will scan each node
       * in the cluster and return the record Digest to the call back object
       */
      database.client.scanAll(scanPolicy, database.namespace, setName, new OurScanCallback(action), new String[] {});
    } catch (AerospikeException e) {
      int resultCode = e.getResultCode();
      database.log.info(ResultCode.getResultString(resultCode));
      database.log.debug("Error details: ", e);
    }
  }


  class OurScanCallback implements ScanCallback {
    private final Predicate<? super Drone> action;

    OurScanCallback(Predicate<? super Drone> action) {
      this.action = action;
    }

    public void scanCallback(Key key, Record record) throws AerospikeException {
      Drone drone = get(key, record);
      // This returns false if we're done, but scanAll can't be cut short.
      action.test(drone);
    }
  }

  Drone get(int id) {
    Key key = new Key(database.namespace, setName, id);
    Policy readPolicy = new Policy();
    Record record = database.client.get(readPolicy, key);
    return record == null ? null : get(key, record);
  }

  public boolean put(Drone drone) {
    Key key = new Key(database.namespace, setName, drone.id);
    Bin id       = new Bin("id",       drone.id);
    Bin state    = new Bin("state",    drone.state);
    Bin location = new Bin("location", drone.getLocation());
    Bin radius   = new Bin("radius",   drone.currentRadius);
    Bin start    = new Bin("start",    drone.startLocation);
    Bin job      = new Bin("jobid",    drone.job.id);
    // Bin candidates todo needs to be a list of IDs. Is this worth doing?
    try {
      database.client.put(writePolicy, key, id, state, location, radius, start, job);
      return true;
    } catch (AerospikeException e) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public Drone getDroneWhereIdIs(int id) {
    Key key = new Key(database.namespace, setName, id);
    Policy readPolicy = new Policy();
    Record record = database.client.get(readPolicy, key);
    return record == null ? null : get(key, record);
  }


  public void foreach(Drone.State state, Predicate<? super Drone> action) {
    String[] bins = { state.name() };
    Statement stmt = new Statement();
    stmt.setNamespace(database.namespace);
    stmt.setSetName(setName);
    stmt.setIndexName("username_index");
    stmt.setBinNames(bins);
    stmt.setFilters(Filter.equal(state.name(), state.ordinal()));

    RecordSet rs = database.client.query(null, stmt);
    while (rs.next()) {
      Record r = rs.getRecord();
      Drone drone = new Drone(this);
      drone.setLocation((Location) r.getValue("location"));
      if (!action.test(drone)) {
        break;
      }
    }
  }

  private Drone get(Key key, Record record) {
    Drone.State state = (Drone.State) record.getValue("state");
    Job job = (Job)record.getValue("job");
    Drone drone = new Drone(AerospikeDrones.this,
        record.getInt("id"),
        state,
        (Location)record.getValue("location"),
        record.getDouble("radius"),
        (Location)record.getValue("start"),
        job
    );
    return drone;
  }

}
