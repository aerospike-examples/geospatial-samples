package com.aerospike.delivery.aerospike;

import com.aerospike.client.*;
import com.aerospike.client.policy.*;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.delivery.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;


// aql -c create index geo on demo1.jobs (Waiting) GEO2DSPHERE


public class AerospikeJobs extends Jobs {

  private final AerospikeDatabase database;
  final String setName;
  private ConcurrentHashMap<Key, Job> cache;
  private final Set<Job> jobsOnHoldCache;

  // One of these is attached to every job.
  class Metadata extends Jobs.Metadata {
    int generation = 0;
    Job.State previousState = Job.State.Init;
  }

  public AerospikeJobs(AerospikeDatabase database, boolean useCaching) {
    this.database = database;
    setName = "jobs";
    if (useCaching) {
      cache = new ConcurrentHashMap<>();
    }
    jobsOnHoldCache = ConcurrentHashMap.newKeySet();
    new Thread(new Metering()).start();
  }


  @Override
  public void initMetadata(Job job) {
    job.metadata = new AerospikeJobs.Metadata();
  }


  @Override
  public Job newJob(Job.State state) {
    incrementSize();
    Job job = new Job(this);
    boolean success = Database.withWriteLock(job.lock, () -> {
      job.setStateAndPut(state);
      return true;
    });
    if (!success) {
      database.log.error(String.format("unable to put new job."));
    }
    return job;
  }


  @Override
  public void clear() {
    super.clear();
    database.clearSet("jobs");
  }


  @Override
  public int size(Job.State state) {
    return modifyCount(state, 0);
  }

  //----------------------------------------------------------------------------------------
  // geo query

  // radius is in terms of degrees at the equator
  public void foreachJobNearestTo(Location location, double radius, Predicate<Job> action) {
    super.foreachJobNearestTo(location, radius, action);
    if (true) {
      // https://en.wikipedia.org/wiki/Decimal_degrees
      int metersPerDegreeAtTheEquator = 111_320;
      queryCircle(location, radius * metersPerDegreeAtTheEquator, action);
    } else {
      // Brute force
      queryCircleBruteForce(location, radius, action);
    }
  }


  private void queryCircle(Location droneLocation, double radius, Predicate<? super Job> action) {
    Statement stmt = new Statement();
    stmt.setNamespace(database.namespace);
    stmt.setSetName(setName);
    String binName = Job.State.Waiting.name();
//    stmt.setBinNames(); // all
    stmt.setFilters(Filter.geoWithinRadius(binName, droneLocation.x, droneLocation.y, radius));

    QueryPolicy policy = new QueryPolicy();
    ++Metering.jobQueryWithinRadius;
    RecordSet rs = database.client.query(policy, stmt);
    try {
      while (rs.next()) {
        ++Metering.jobRadiusResults;
        Job job = get(rs.getKey(), rs.getRecord());
        if (!action.test(job)) {
          break;
        }
      }
    } finally {
      rs.close();
    }
  }


  // alternative not using geo circle query
  private void queryCircleBruteForce(Location location, double radius, Predicate<? super Job> action) {
    foreach(job -> {
      if (job.state == Job.State.Waiting) {
        double distance = location.distanceTo(job.getOrigin());
        if (distance <= radius) {
          return action.test(job);
        }
      }
      return true;
    });
  }

  //----------------------------------------------------------------------------------------

//  // This is not used and not tested and wrong
  @Override
  public void foreach(Job.State state, Predicate<? super Job> action) {
    if (true) {
      throw new Error("not tested");
    } else {
      String[] bins = { state.name() };
      Statement stmt = new Statement();
      stmt.setNamespace(database.namespace);
      stmt.setSetName(setName);
      stmt.setIndexName("username_index");
      stmt.setBinNames(bins);
//    stmt.setFilters(Filter.equal(state.name(), ___));

      RecordSet rs = database.client.query(null, stmt);
      while (rs.next()) {
        Record r = rs.getRecord();
        Job job = new Job(this);
        job.setLocation((Location) r.getValue("location"));
        if (!action.test(job)) {
          break;
        }
      }
    }
  }

  @Override
  public void foreach(Predicate<? super Job> action) {
    if (cache != null) {
      for (Job job : cache.values()) {
        if (!action.test(job)) {
          break;
        }
      }
    } else {
      ScanPolicy scanPolicy = new ScanPolicy();
      try {
        /*
         * Scan the entire Set using scannAll(). This will scan each node
         * in the cluster and return the record Digest to the call back object
         */
        ++Metering.jobScans;
        database.client.scanAll(scanPolicy, database.namespace, setName, new ForeachScanCallback(action));
      } catch (AerospikeException e) {
        int resultCode = e.getResultCode();
        database.log.info(ResultCode.getResultString(resultCode));
        database.log.debug("Error details: ", e);
      }
    }
  }


  class ForeachScanCallback implements ScanCallback {
    private final Predicate<? super Job> action;

    ForeachScanCallback(Predicate<? super Job> action) {
      this.action = action;
    }

    public void scanCallback(Key key, Record record) throws AerospikeException {
      // todo Should we get them in batches?
      ++Metering.jobScanResults;
      Job job = get(key, record);
      // action.test() returns false if the caller doesn't need any more.
      // We ignore that because scanAll can't deal with it.
      // ScanCallback could provide a cancel() method, maybe someday.
      action.test(job);
    }
  }


  public void promoteAJobFromOnHold() {
    for (Job job : jobsOnHoldCache) {
      Database.withWriteLock(job.lock, () -> {
        if (job.changeStateAndPut(Job.State.OnHold, Job.State.Waiting)) {
          jobsOnHoldCache.remove(job);
        }
        return true;
      });
    }
  }

  @Override
  public boolean putWithNewState(Job job, Job.State from, Job.State to) {
    Database.assertWriteLocked(job.lock);
    if (from == to && from != Job.State.Init) {
      System.out.printf("changeState from %s == to %s\n", from, to);
      return false;
    }
    if (job.getState() != from) {
      return false;
    }
    ((Metadata) job.metadata).previousState = job.state;
    job.state = to;
    boolean success = put(job);
    if (!success) {
      job.state = from;
    } else {
      modifyCount(from, -1);
      modifyCount(to, +1);
      if (to == Job.State.OnHold) {
        // hold it in our cache
        jobsOnHoldCache.add(job);
      }
    }
    return success;
  }


  public Job getJobWhereIdIs(int id) {
    Key key = new Key(database.namespace, setName, id);
    Policy readPolicy = new Policy();
    ++Metering.jobGets;
    Record record = database.client.get(readPolicy, key);
    return record == null ? null : get(key, record);
  }


  public boolean put(Job job) {
    Database.assertWriteLocked(job.lock);
    Key key = new Key(database.namespace, setName, job.id);
    if (cache != null) {
      cache.put(key, job);
    }
    String originBinName = job.state.name(); // location is stored in a bin by this name
    Bin idBin          = new Bin("id",        job.id);
    Bin stateBin       = new Bin("state",     originBinName);
    Bin originBin      =     Bin.asGeoJSON(originBinName,   job.getOrigin()     .toGeoJSONPointDouble());
    Bin destinationBin =     Bin.asGeoJSON("destination",   job.getDestination().toGeoJSONPointDouble());
    Bin locationBin    =     Bin.asGeoJSON("location",      job.getLocation()   .toGeoJSONPointDouble());
    Bin candidateBin   = new Bin("candidate", job.isCandidate());
    Bin droneIdBin    = new Bin("droneid",  job.droneid);
//    System.out.printf("put %s\n", job);
    WritePolicy writePolicy = makePutWritePolicy(job);
    try {
      ++Metering.jobPuts;
      database.client.put(writePolicy, key, idBin, stateBin, originBin, destinationBin, locationBin, candidateBin, droneIdBin);
      ++((Metadata)job.metadata).generation;
//      database.log.info(String.format("changed %s to %s %s %d", ((Metadata)job.metadata).previousState, job.state, job, ((Metadata)job.metadata).generation));
      return true;
    } catch (AerospikeException e) {
      if (e.getResultCode() != ResultCode.GENERATION_ERROR) {
        e.printStackTrace();
      } else {
        // This happens a lot more than seems reasonable.
//        database.log.info(String.format("failed  %s to %s %s %d", ((Metadata)job.metadata).previousState, job.state, job, ((Metadata)job.metadata).generation));
      }
      return false;
    }
  }

  private WritePolicy makePutWritePolicy(Job job) {
    WritePolicy writePolicy = new WritePolicy();
    writePolicy.recordExistsAction = RecordExistsAction.REPLACE;
    if (((Metadata)job.metadata).generation != 0) {
      // We care only when we're changing the job's state,
      // but it doesn't hurt to leave this enabled.
      writePolicy.generationPolicy = GenerationPolicy.EXPECT_GEN_EQUAL;
      writePolicy.generation = ((Metadata)job.metadata).generation;
    }
    return writePolicy;
  }

  //-----------------------------------------------------------------------------------
  // private stuff


  private Job get(Key key, Record record) {
    Job job;
    if (cache != null) {
      job = cache.get(key);
      if (job == null) {
        throw new Error("job is not in cache");
      }
    } else {
      Job.State state = Job.State.stateForName(record.getValue("state"));
      String locationBinName = state.name(); // location is stored in a bin by this name
      AerospikeJobs.Metadata metadata = new Metadata();
      metadata.generation = record.generation;
      int id = record.getInt("id");
      Location origin      = Location.makeFromGeoJSONPointDouble(record.getGeoJSON(locationBinName));
      Location destination = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("destination"));
      Location location    = Location.makeFromGeoJSONPointDouble(record.getGeoJSON("location"));
      int droneId = record.getInt("droneId");
      boolean isCandidate = record.getBoolean("candidate");
      job = new Job(AerospikeJobs.this,
          metadata,
          id,
          state,
          origin,
          destination,
          location,
          droneId,
          isCandidate
      );
    }
//    System.out.printf("get %s\n", job);
    return job;
  }


  private int countWaiting;
  private int countInProcess;
  private int countOnHold;

  private int modifyCount(Job.State state, int amount) {
    switch (state) {
      default: throw new Error("unhandled job state");
      case Init:      return 0; // used in job construction only
      case Waiting:   return countWaiting   += amount;
      case InProcess: return countInProcess += amount;
      case OnHold:    return countOnHold    += amount;
    }
  }


}
