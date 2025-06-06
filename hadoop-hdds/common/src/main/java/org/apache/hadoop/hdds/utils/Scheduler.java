/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.ratis.util.function.CheckedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates ScheduledExecutorService.
 */
public class Scheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(Scheduler.class);

  private ScheduledExecutorService scheduledExecutorService;

  private volatile boolean isClosed;

  private final String threadName;

  /**
   * Creates a ScheduledExecutorService based on input arguments.
   * @param threadName - thread name
   * @param isDaemon - if true the threads in the scheduler are started as
   *                 daemon
   * @param numCoreThreads - number of core threads to maintain in the scheduler
   */
  public Scheduler(String threadName, boolean isDaemon, int numCoreThreads) {
    scheduledExecutorService = Executors.newScheduledThreadPool(numCoreThreads,
        r -> {
          Thread t = new Thread(r);
          t.setName(threadName);
          t.setDaemon(isDaemon);
          return t;
        });
    this.threadName = threadName;
    isClosed = false;
  }

  public void schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
    scheduledExecutorService.schedule(runnable, delay, timeUnit);
  }

  public void schedule(CheckedRunnable<?> runnable, long delay,
      TimeUnit timeUnit, Logger logger, String errMsg) {
    scheduledExecutorService.schedule(() -> {
      try {
        runnable.run();
      } catch (Throwable throwable) {
        logger.error(errMsg, throwable);
      }
    }, delay, timeUnit);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable,
      long initialDelay, long fixedDelay, TimeUnit timeUnit) {
    return scheduledExecutorService
        .scheduleWithFixedDelay(runnable, initialDelay, fixedDelay, timeUnit);
  }

  public boolean isClosed() {
    return isClosed;
  }

  /**
   * Closes the scheduler for further task submission. Any pending tasks not
   * yet executed are also cancelled. For the executing tasks the scheduler
   * waits 60 seconds for completion.
   */
  public synchronized void close() {
    isClosed = true;
    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdownNow();
      try {
        scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOG.info("{} interrupted while waiting for task completion.",
                threadName, e);
        // Re-interrupt the thread while catching InterruptedException
        Thread.currentThread().interrupt();
      }
    }
    scheduledExecutorService = null;
  }
}
