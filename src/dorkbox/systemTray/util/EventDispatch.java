/*
 * Copyright 2021 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.systemTray.util;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.NamedThreadFactory;

/**
 * Adds events to a single thread event dispatch, so that regardless of OS, all event callbacks happen on the same thread -- which is NOT
 * the GTK/AWT/SWING event dispatch thread. There can be ODD peculiarities across on GTK with how AWT/SWING/JavaFX react with the GTK Event
 * Dispatch Thread.
 */
public
class EventDispatch {
    /**
     * Specifies the thread priority used by the SystemTray event dispatch. By default, the "normal priority" is used.
     */
    private static int THREAD_PRIORITY = Thread.NORM_PRIORITY;

    private static ExecutorService eventDispatchExecutor = null;

    private static volatile CountDownLatch shutdownLatch = null;
    private static volatile boolean insideDispatch = false;

    /**
     * Schedule an event to occur sometime in the future. We do not want to WAIT for a `runnable` to finish, because it is POSSIBLE that
     * this runnable wants to perform actions on the SAME dispatch thread that called this, resulting in a deadlock. Because we cannot
     * guarantee that this will never happen, we must always run this "in the future" as a queue - so that FIFO is obeyed.
     */
    public static
    void runLater(final Runnable runnable) {
        synchronized(EventDispatch.class) {
            if (eventDispatchExecutor == null) {
                if (insideDispatch) {
                    SystemTray.logger.error("Unable to create a new event dispatch, while executing within the same context.");
                    return;
                }

                shutdownLatch = new CountDownLatch(1);
                eventDispatchExecutor = Executors.newSingleThreadExecutor(
                        new NamedThreadFactory("SystemTrayEventDispatch",
                                               Thread.currentThread().getThreadGroup(), THREAD_PRIORITY, false));
            }
        }

        eventDispatchExecutor.execute(()->{
            insideDispatch = true;
            runnable.run();
            insideDispatch = false;
        });
    }

    /**
     * Shutdown the event dispatch at the end of our current dispatch queue
     */
    public static
    void shutdown() {
        // we have to make sure we shut down on our own thread (and not the JavaFX/SWT/AWT/etc thread)
        runLater(()->{
            ExecutorService executorService = null;
            synchronized (EventDispatch.class) {
                executorService = eventDispatchExecutor;
                eventDispatchExecutor = null;
            }

            if (executorService != null) {
                final List<Runnable> runnables = executorService.shutdownNow();
                for (int i = 0; i < runnables.size(); i++) {
                    try {
                        runnables.get(i)
                                 .run();
                    } catch (Exception e) {
                        SystemTray.logger.error("Error shutting down EventDispatch", e);
                    }
                }

                shutdownLatch.countDown();
            }
        });
    }

    /**
     * Waits for the event dispatch to finish shutting down
     */
    public static void waitForShutdown() {
        CountDownLatch latch = null;
        synchronized (EventDispatch.class) {
            latch = shutdownLatch;
        }

        if (latch != null) {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
