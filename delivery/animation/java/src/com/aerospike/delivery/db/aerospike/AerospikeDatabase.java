package com.aerospike.delivery.db.aerospike;


import com.aerospike.client.*;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.task.IndexTask;
import com.aerospike.delivery.*;
import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.util.OurExecutor;
import org.apache.commons.cli.CommandLine;

public class AerospikeDatabase extends Database {

  private Parameters parameters;
  public String namespace;
  AerospikeClient client;
  private final ClientPolicy clientPolicy;


  public AerospikeDatabase() {
    namespace = "test"; //default
    clientPolicy = new ClientPolicy();
    clientPolicy.failIfNotConnected = true;
  }

  public boolean parseOptions(CommandLine commandLine) {
    parameters = Parameters.parseServerParameters(commandLine);
    if (parameters == null) {
      return false;
    }
    this.namespace = parameters.namespace;
    clientPolicy.user = parameters.user;
    clientPolicy.password = parameters.password;
    clientPolicy.failIfNotConnected = true;
    return true;
  }

  @Override
  public boolean connect() {
    client = new AerospikeClient(clientPolicy, parameters.host, parameters.port);
    if (client.isConnected()) {
      drones = new AerospikeDrones(this);
      jobs = new AerospikeJobs(this);
      new Thread(new Metering()).start();
      return true;
    } else {
      return false;
    }
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
      System.out.println("Deleted " + callback.count + " records from set " + setName);
    } catch (AerospikeException e) {
      int resultCode = e.getResultCode();
      System.err.format("scanAll to clear set: %s %s %s\n", setName, ResultCode.getResultString(resultCode), e);
    }
  }


  class ClearScanCallback implements ScanCallback {
    int count;

    public void scanCallback(Key key, Record record) {
      try {
        client.delete(null, key);
      } catch (AerospikeException e) {
        int resultCode = e.getResultCode();
        System.err.format("delete %s %s\n", ResultCode.getResultString(resultCode), e);
      }
      count++;
    }
  }

  // ------------------------------------------------------------------------------------------------

  // for testing
  public static void main(String[] args) {
    OurOptions options = new OurOptions();
    options.doCommandLineOptions("ae-test", args);
    boolean useCaching = false;
    AerospikeDatabase database = Database.makeAerospikeDatabase();
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
    OurExecutor.executor.shutdownNow();
  }

  private static void getAndPrintJob(AerospikeJobs jobs, int id) {
    Job job = jobs.getJobWhereIdIs(id);
    System.out.printf("got job %s\n", job);
  }

}
