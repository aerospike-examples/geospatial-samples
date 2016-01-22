package com.aerospike.delivery.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by yost on 2016-01-05.
 */
public class DebuggingCountDownLatch<T extends Comparable<? super T>> extends CountDownLatch {

  private static final Runnable dummyRunnable = new Runnable() { @Override public void run() {} };
  final boolean isDebugging;
  private final Runnable callout;
  private final CountDownLatch readyForCountdowns;
  private final Lock lock;
  private Set<T> remaining;
  private Collection<T> participantsSoFar;


  public DebuggingCountDownLatch() {
    this(false, null, dummyRunnable);
  }


  public DebuggingCountDownLatch(boolean isDebugging, Collection<T> expectedParticipants) {
    this(isDebugging, expectedParticipants, dummyRunnable);
  }


  /**
   * Constructs a {@code CountDownLatch} initialized with the given count.
   *
   * @param isDebugging
   * @throws IllegalArgumentException if {@code count} is negative
   */
  public DebuggingCountDownLatch(boolean isDebugging, Collection<T> expectedParticipants, Runnable poll) {
    super(expectedParticipants != null ? expectedParticipants.size() : 0);
    this.isDebugging = isDebugging;
    this.callout = poll;
    if (isDebugging && expectedParticipants != null) {
      remaining = new HashSet<>(expectedParticipants);
      participantsSoFar = new ArrayList<>();
      System.out.println("Latch start");
    }
    readyForCountdowns = new CountDownLatch(1);
    lock = new ReentrantLock();
  }


  public void countDown(T obj) {
    if (isDebugging) {
      try {
        readyForCountdowns.await();
      } catch (InterruptedException e) {
        //e.printStackTrace();
      }
      long count;
      lock.lock(); try {
        participantsSoFar.add(obj);
        count = getCount();
      } finally {
        lock.unlock();
      }
      super.countDown();
      System.out.println("Latch countDown " + (count - 1) + " ~ " + obj);
    } else {
      super.countDown();
    }
  }

  @Override
  public void countDown() {
    throw new Error("You have to call the countDown method that takes an arg.");
  }

  @Override
  public void await() throws InterruptedException {
    if (isDebugging) {
      printSnapshot();
      readyForCountdowns.countDown();
      while (getCount() != 0) {
        // Set a thread-only breakpoint on your callback if you want to look around.
        // Or put a breakpoint here.
        callout.run();
        // Meanwhile, other threads can be counting down.
        Thread.sleep(100);
        StringWriter outSW = new StringWriter();
        printGot(new PrintWriter(outSW, true));
        String output = outSW.toString();
        if (output.length() > 0) {
          System.out.println(output);
        }
      }
    }

    super.await();

    if (isDebugging) System.out.println("Latch await done");
  }


  long countSnapshot;

  /** Print the participantsSoFar then the participants remaining
   *
   */
  private void printSnapshot() {
    Collection<T> newParticipants;
    lock.lock(); try {
      newParticipants = new ArrayList<>(participantsSoFar);
      countSnapshot = getCount();
    } finally {
      lock.unlock();
    }

    StringWriter outSW = new StringWriter();
    PrintWriter  outWr = new PrintWriter(outSW, true);

    outWr.println();
    outWr.print("\nLatch await " + countSnapshot);
    printParticipants(newParticipants, outWr);
    printRemaining(outWr);
    System.out.println(outSW);
  }


  /** Print the participants we got since last time.
   *
   * @param outWr
   */
  private void printGot(PrintWriter outWr) {
    Set<T> newParticipants = null;

    lock.lock(); try {
      // RRRR---- remaining
      // --PPPPPP participantsSoFar
      // --NN---- newParticipants  retainAll
      // RR------ remaining        removeAll
      if (countSnapshot > getCount()) {
        newParticipants = new HashSet<>(remaining);
        newParticipants.retainAll(participantsSoFar);
        remaining.removeAll(newParticipants);
        countSnapshot = getCount();
      }
    } finally {
      lock.unlock();
    }

    if (newParticipants != null) {
      printParticipants(newParticipants, outWr);
    }
  }


  private void printParticipants(Collection<T> participantsArg, PrintWriter  outWr) {
    if (participantsArg.size() > 0) outWr.println();
    for (T t : participantsArg) {
      outWr.println("Latch got " + t);
    }
  }


  private void printRemaining(PrintWriter out) {
    if (remaining.size() > 0) {
      out.println("  remaining:");
      printSortedJobs(out);
    }
  }

  private void printSortedJobs(PrintWriter out) {
    for (T t : asSortedList(remaining)) out.println("  " + t);
  }

  public static
  <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
    List<T> list = new ArrayList<T>(c);
    java.util.Collections.sort(list);
    return list;
  }

}
