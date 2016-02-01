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


import com.aerospike.delivery.*;

import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

public abstract class Jobs {

  public int nextID;
  private int size;

  public class Metadata { }


  public Jobs() {
    nextID = Job.FirstID;
    size = 0;
  }


  public final void addMore(int count) {
    for (int i = 0 ; i < count ; ++i) {
      // newJob adds the job to the Waiting set.
      newJob(Job.State.Waiting);
    }
  }


  // default implementation
  public void initMetadata(Job job) { }


  public abstract Job newJob(Job.State state);

  public void clear() {
    nextID = Job.FirstID;
    size = 0;
  }

  public abstract int size(Job.State state);

  public final int size() {
    return size;
  }

  public void incrementSize() {
    ++size;
  }

  public void foreachJobNearestTo(Location location, double outer, Predicate<Job> action) {
    ensureEnoughJobs();
  }

  public abstract void foreach(Job.State state, Predicate<? super Job> action);

  public abstract void foreach(                 Predicate<? super Job> action);

  public abstract BlockingQueue<Job> makeQueueForRendering();

  public abstract void promoteAJobFromOnHold();

  public abstract boolean putWithNewState(Job job, Job.State from, Job.State to);

  public abstract Job getJobWhereIdIs(int id);


  //-----------------------------------------------------------------------------------
  // private stuff

  private void ensureEnoughJobs() {
    int nbDrones = OurOptions.instance.database.getDrones().size();
    int count = Conductor.backlogExcess(nbDrones) - size();
    if (count > 0) {
      addMore(count);
    }
  }

  public abstract boolean put(Job job);


}
