package com.aerospike.delivery.aerospike;


import com.aerospike.client.*;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.delivery.App;
import com.aerospike.delivery.Database;
import com.aerospike.delivery.Job;
import com.aerospike.delivery.Parameters;
import com.aerospike.delivery.inmemory.InMemoryDrones;
import org.apache.log4j.Logger;

public class AerospikeDatabase extends Database {

  private final Parameters parameters;
  public final String namespace;
  private final boolean useCaching;
  AerospikeClient client;
  final ClientPolicy clientPolicy;
  Logger log;

  public AerospikeDatabase(Parameters parameters, boolean useCaching) {
    this.parameters = parameters;
    this.useCaching = useCaching;
    this.namespace = parameters.namespace;
    clientPolicy = new ClientPolicy();
    clientPolicy.user     = parameters.user;
    clientPolicy.password = parameters.password;
    clientPolicy.failIfNotConnected = true;

    drones = new InMemoryDrones();  // todo Drones are in not in the Aerospike database yet.

    jobs    = new AerospikeJobs(this, useCaching);

    log = Logger.getLogger(AerospikeDatabase.class);
  }

  @Override
  public boolean connect() {
    client = new AerospikeClient(clientPolicy, parameters.host, parameters.port);
    return client.isConnected();
  }

  @Override
  public boolean isConnected() {
    return client != null && client.isConnected();
  }

  @Override
  public void close() {
    super.close();
    if (client.isConnected()) {
      System.out.println("Closing the connection.");
      client.close();
    } else {
      System.err.println("Couldn't close the connection.");
    }
  }

  @Override
  public void clear() {
    jobs.clear();
    drones.clear();
  }

  @Override
  public String databaseType() {
    return "Aerospike database";
  }

  //----------------------------------------------------------------------------------
  // Specific to this subclass

  void clearSet(String setName) {
    ScanPolicy scanPolicy = new ScanPolicy();
    scanPolicy.timeout = 100;
    try {
      /*
       * Scan the entire Set using scannAll(). This will scan each node
       * in the cluster and return the record Digest to the call back object
       */
      ClearScanCallback callback = new ClearScanCallback();
      client.scanAll(scanPolicy, namespace, setName, callback);
      log.info("Deleted " + callback.count + " records from set " + setName);
    } catch (AerospikeException e) {
      int resultCode = e.getResultCode();
      log.info(ResultCode.getResultString(resultCode));
      log.debug("Error details: ", e);
    }
  }


  class ClearScanCallback implements ScanCallback {
    int count;

    public void scanCallback(Key key, Record record) throws AerospikeException {
      client.delete(null, key);
      count++;
      // After 25,000 records delete, return print the count.
      if (count % 25000 == 0) {
        log.info("Deleted " + count);
      }
    }
  }

  // ------------------------------------------------------------------------------------------------

  // for testing
  public static void main(String[] args) {
    App.doCommandLineOptions(args);
    boolean useCaching = false;
    AerospikeDatabase database = new AerospikeDatabase(App.parameters, useCaching);
    AerospikeJobs jobs = (AerospikeJobs) database.getJobs();
    if (database.connect()) {
      try {
        database.clear();
        jobs.newJob(Job.State.Init);
        jobs.newJob(Job.State.Init);
        jobs.newJob(Job.State.Init);
        getAndPrintJob(jobs, 1);
        getAndPrintJob(jobs, 2);
        getAndPrintJob(jobs, 3);
        jobs.foreach(job -> {
          System.out.println("job " + job);
          return true;
        });
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        database.close();
//        database.log.removeAllAppenders();
//        org.apache.log4j.LogManager.shutdown();
      }
    } else {
      System.err.println("Couldn't connect.");
    }
    App.executor.shutdownNow();
  }

  private static void getAndPrintJob(AerospikeJobs jobs, int id) {
    Job job = jobs.getJobWhereIdIs(id);
    System.out.printf("got job %s\n", job);
  }

}
