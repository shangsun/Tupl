/*
 *  Copyright 2017 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.tupl.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A gang of {@link Worker workers} for running tasks. This class isn't thread safe for
 * enqueuing tasks, and so the caller must provide its own mutual exclusion to protect against
 * concurrent enqueues.
 *
 * @author Brian S O'Neill
 */
public class WorkerGang {
    private final Worker[] mWorkers;
    private int mLastSelected;

    /**
     * @param workerCount number of workers
     * @param maxSize maximum amount of tasks which can be enqueued per worker
     * @param notifyAvailable minimum available space in a worker queue before it can wake up a
     * blocked eneueue (pass zero for immediate wake up)
     * @param keepAliveTime maximum idle time before worker threads exit
     * @param unit keepAliveTime time unit per worker
     * @param threadFactory null for default
     */
    public WorkerGang(int workerCount,
                      int maxSize, int notifyAvailable,
                      long keepAliveTime, TimeUnit unit,
                      ThreadFactory threadFactory)
    {
        if (workerCount < 1) {
            throw new IllegalArgumentException();
        }

        if (threadFactory == null) {
            threadFactory = Executors.defaultThreadFactory();
        }

        Worker[] workers = new Worker[workerCount];

        for (int i=0; i<workers.length; i++) {
            workers[i] = new Worker(maxSize, notifyAvailable, keepAliveTime, unit, threadFactory);
        }

        mWorkers = workers;
    }

    /**
     * Attempts to select an available worker and enqueues a task without blocking. When the
     * task object is enqueued, it must not be used again for any other tasks.
     *
     * @return selected worker or null if all worker queues are full and task wasn't enqueued
     */
    public Worker tryEnqueue(Worker.Task task) {
        // Start the search just lower than the last one selected, to drive tasks towards the
        // lower workers. The higher workers can then idle and allow their threads to exit.
        int slot = Math.max(0, mLastSelected - 1);

        for (int i=0; i<mWorkers.length; i++) {
            Worker w = mWorkers[slot];
            if (w.tryEnqueue(task)) {
                mLastSelected = slot;
                return w;
            }
            slot++;
            if (slot >= mWorkers.length) {
                slot = 0;
            }
        }

        return null;
    }

    /**
     * Enqueue a task, blocking if necessary until space is available. When the task object is
     * enqueued, it must not be used again for any other tasks.
     *
     * @return selected worker
     */
    public Worker enqueue(Worker.Task task) {
        Worker w = tryEnqueue(task);
        if (w == null) {
            w = mWorkers[mLastSelected = ThreadLocalRandom.current().nextInt(mWorkers.length)];
            w.enqueue(task);
        }
        return w;
    }

    /**
     * Waits until all the worker queues are drained. If the worker threads are interrupted and
     * exit, new threads are started when new tasks are enqueued.
     *
     * @param interrupt pass true to interrupt the worker threads so that they exit
     */
    public void join(boolean interrupt) {
        for (Worker w : mWorkers) {
            w.join(interrupt);
        }
    }
}