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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.util.NamedThreadFactory;

/**
 * Adds events to a single thread event dispatch, so that regardless of OS, all event callbacks happen on the same thread -- which is NOT
 * the GTK/AWT/SWING event dispatch thread. There can be ODD peculiarities across on GTK with how AWT/SWING react with the GTK Event
 * Dispatch Thread.
 */
public
class EventDispatch {
    public static boolean DEBUG = false;

    public static final int TIMEOUT = 2;

    private static ExecutorService eventDispatchExecutor = null;

    // This is required because the EDT needs to have it's own value for this boolean, that is a different value than the main thread
    private static ThreadLocal<Boolean> isDispatch = new ThreadLocal<Boolean>() {
        @Override
        protected
        Boolean initialValue() {
            return false;
        }
    };

    /**
     * Schedule an event to occur and wait for it to finish
     */
    public static
    void run(final Runnable runnable) {
        // if we are on the dispatch queue, do not block
        if (EventDispatch.isDispatch.get()) {
            // don't block. The ORIGINAL call (before items were queued) will still be blocking. If the original call was a "normal"
            // dispatch, then subsequent dispatchAndWait calls are irrelevant (as they happen in the GTK thread, and not the main thread).
            runnable.run();
            return;
        }

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        runLater(()->{
            try {
                runnable.run();
            } catch (Exception e) {
                LoggerFactory.getLogger(GtkEventDispatch.class).error("Error during Event dispatch run loop: ", e);
            } finally {
                countDownLatch.countDown();
            }
        });

        // this is slightly different than how swing does it. We have a timeout here so that we can make sure that updates on the GUI
        // thread occur in REASONABLE time-frames, and alert the user if not.
        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                if (DEBUG) {
                    LoggerFactory.getLogger(EventDispatch.class).error(
                            "Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT + " seconds " +
                            "to complete.", new Exception(""));
                }
                else {
                    throw new RuntimeException("Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT +
                                               " seconds to complete.");
                }
            }
        } catch (InterruptedException e) {
            LoggerFactory.getLogger(GtkEventDispatch.class).error("Error waiting for dispatch to complete.", new Exception(""));
        }
    }

    /**
     * Schedule an event to occur sometime in the future.
     */
    public static
    void runLater(final Runnable runnable) {
        // if we are on the dispatch queue, do not block
        if (EventDispatch.isDispatch.get()) {
            // don't block. The ORIGINAL call (before items were queued) will still be blocking. If the original call was a "normal"
            // dispatch, then subsequent dispatchAndWait calls are irrelevant (as they happen in the GTK thread, and not the main thread).
            runnable.run();
            return;
        }

        synchronized(EventDispatch.class) {
            if (eventDispatchExecutor == null) {
                eventDispatchExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SystemTrayEventDispatch", false));
            }
        }

        eventDispatchExecutor.execute(()->{
            EventDispatch.isDispatch.set(true);

            runnable.run();

            EventDispatch.isDispatch.set(false);
        });
    }

    /**
     * Shutdown the event dispatch at the end of our current dispatch queue
     */
    public static
    void shutdown() {
        runLater(()->{
            synchronized (EventDispatch.class) {
                if (eventDispatchExecutor != null) {
                    eventDispatchExecutor.shutdownNow();
                    eventDispatchExecutor = null;
                }
            }
        });
    }
}
