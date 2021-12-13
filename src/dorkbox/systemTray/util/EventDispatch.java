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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /**
     * Schedule an event to occur sometime in the future. We do not want to WAIT for a `runnable` to finish, because it is POSSIBLE that
     * this runnable wants to perform actions on the SAME dispatch thread that called this, resulting in a deadlock. Because we cannot
     * guarantee that this will never happen, we must always run this "in the future" as a queue - so that FIFO is obeyed.
     */
    public static
    void runLater(final Runnable runnable) {
        synchronized(EventDispatch.class) {
            if (eventDispatchExecutor == null) {
                eventDispatchExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SystemTrayEventDispatch", THREAD_PRIORITY, false));
            }
        }

        eventDispatchExecutor.execute(()->{
            runnable.run();
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
                    // we do not want to continue processing anything, so all non-executed events will be tossed.
                    eventDispatchExecutor.shutdownNow();
                    eventDispatchExecutor = null;
                }
            }
        });
    }
}
