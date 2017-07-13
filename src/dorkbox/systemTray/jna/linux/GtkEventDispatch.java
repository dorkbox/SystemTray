/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.systemTray.jna.linux;

import static dorkbox.systemTray.SystemTray.logger;
import static dorkbox.systemTray.jna.linux.Gtk.Gtk2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;

public
class GtkEventDispatch {
    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private static final LinkedList<Object> gtkCallbacks = new LinkedList<Object>();

    // This is required because the EDT needs to have it's own value for this boolean, that is a different value than the main thread
    private static ThreadLocal<Boolean> isDispatch = new ThreadLocal<Boolean>() {
        @Override
        protected
        Boolean initialValue() {
            return false;
        }
    };

    private static volatile boolean started = false;

    @SuppressWarnings("FieldCanBeLocal")
    private static Thread gtkUpdateThread = null;

    // when debugging the EDT, we need a longer timeout.
    private static final boolean debugEDT = true;

    // timeout is in seconds
    private static final int TIMEOUT = debugEDT ? 10000000 : 2;


    public static
    void startGui() {
        // only permit one startup per JVM instance
        if (!started) {
            started = true;

            // startup the GTK GUI event loop. There can be multiple/nested loops.


            if (!GtkLoader.alreadyRunningGTK) {
                // If JavaFX/SWT is used, this is UNNECESSARY (we can detect if the GTK main_loop is running)

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        Glib.GLogFunc orig = null;
                        if (SystemTray.DEBUG) {
                            logger.debug("Running GTK Native Event Loop");
                        } else {
                            // NOTE: This can output warnings, so we suppress them
                            orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);
                        }


                        // prep for the event loop.
                        // GThread.g_thread_init(null);  would be needed for g_idle_add()

                        if (!Gtk2.gtk_init_check(0)) {
                            if (SystemTray.DEBUG) {
                                logger.error("Error starting GTK");
                            }
                            return;
                        }

                        // gdk_threads_enter();  would be needed for g_idle_add()

                        if (orig != null) {
                            Glib.g_log_set_default_handler(orig, null);
                        }

                        // blocks unit quit
                        Gtk2.gtk_main();

                        // clean up threads
                        // gdk_threads_leave();  would be needed for g_idle_add()
                    }
                };
                gtkUpdateThread.setDaemon(false); // explicitly NOT daemon so that this will hold the JVM open as necessary
                gtkUpdateThread.setName("GTK Native Event Loop");
                gtkUpdateThread.start();
            }
        }
    }

    /**
     * Waits for the all posted events to GTK to finish loading
     */
    @SuppressWarnings("Duplicates")
    public static
    void waitForEventsToComplete() {
        final CountDownLatch blockUntilStarted = new CountDownLatch(1);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                blockUntilStarted.countDown();
            }
        });

        if (SystemTray.isJavaFxLoaded) {
            if (!JavaFX.isEventThread()) {
                try {
                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForEventsToComplete took longer than expected.",
                                                    new Exception(""));
                        }
                    }

                    // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (SystemTray.isSwtLoaded) {
            if (!Swt.isEventThread()) {
                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                try {
                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForEventsToComplete took longer than expected.",
                                                    new Exception(""));
                        }
                    }

                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            try {
                if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                    if (SystemTray.DEBUG) {
                        SystemTray.logger.error("Something is very wrong. The waitForEventsToComplete took longer than expected.",
                                                new Exception(""));
                    }
                }

                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                while (true) {
                    Thread.sleep(100);

                    synchronized (gtkCallbacks) {
                        if (gtkCallbacks.isEmpty()) {
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Best practices for GTK, is to call EVERYTHING for it on the GTK THREAD. This accomplishes that.
     */
    public static
    void dispatch(final Runnable runnable) {
        if (GtkLoader.alreadyRunningGTK) {
            if (SystemTray.isJavaFxLoaded) {
                // JavaFX only
                if (JavaFX.isEventThread()) {
                    // Run directly on the JavaFX event thread
                    runnable.run();
                }
                else {
                    JavaFX.dispatch(runnable);
                }

                return;
            }

            if (SystemTray.isSwtLoaded) {
                if (Swt.isEventThread()) {
                    // Run directly on the SWT event thread. If it's not on the dispatch thread, we can use raw GTK to put it there
                    runnable.run();

                    return;
                }
            }
        }

        // not javafx
        // gtk/swt are **mostly** the same in how events are dispatched, so we can use "raw" gtk methods for SWT
        if (isDispatch.get()) {
            // Run directly on the dispatch thread
            runnable.run();
        }
        else {
            final FuncCallback callback = new FuncCallback() {
                @Override
                public
                int callback(final Pointer data) {
                    synchronized (gtkCallbacks) {
                        gtkCallbacks.removeFirst(); // now that we've 'handled' it, we can remove it from our callback list
                    }

                    isDispatch.set(true);

                    try {
                        runnable.run();
                    } finally {
                        isDispatch.set(false);
                    }

                    return Gtk2.FALSE; // don't want to call this again
                }
            };

            synchronized (gtkCallbacks) {
                gtkCallbacks.offer(callback); // prevent GC from collecting this object before it can be called
            }

            // the correct way to do it. Add with a slightly higher value
            Gtk2.gdk_threads_add_idle_full(100, callback, null, null);
        }
    }

    public static
    void shutdownGui() {
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                // If JavaFX/SWT is used, this is UNNECESSARY (and will break SWT/JavaFX shutdown)
                if (!GtkLoader.alreadyRunningGTK) {
                    Gtk2.gtk_main_quit();
                }

                started = false;
            }
        });
    }

    public static
    void dispatchAndWait(final Runnable runnable) {
        if (isDispatch.get()) {
            // Run directly on the dispatch thread (should not "redispatch" this again)
            runnable.run();
        }
        else {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            dispatch(new Runnable() {
                @Override
                public
                void run() {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        SystemTray.logger.error("Error during GTK run loop: ", e);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });

            // this is slightly different than how swing does it. We have a timeout here so that we can make sure that updates on the GUI
            // thread occur in REASONABLE time-frames, and alert the user if not.
            try {
                if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                    if (SystemTray.DEBUG) {
                        SystemTray.logger.error(
                                        "Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT + " seconds " +
                                        "to complete.", new Exception(""));
                    }
                    else {
                        throw new RuntimeException("Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT +
                                                   " seconds " + "to complete.");
                    }
                }
            } catch (InterruptedException e) {
                SystemTray.logger.error("Error waiting for dispatch to complete.", new Exception(""));
            }
        }
    }

    /**
     * required to properly setup the dispatch flag when using native menus
     *
     * @param callback will never be null.
     */
    public static
    void proxyClick(final Entry menuEntry, final ActionListener callback) {
        isDispatch.set(true);

        try {
            if (menuEntry != null) {
                callback.actionPerformed(new ActionEvent(menuEntry, ActionEvent.ACTION_PERFORMED, ""));
            }
            else {
                // checkbox entries will not pass the menuEntry in, because they redispatch the click event so that the checkbox state is
                // toggled
                callback.actionPerformed(null);
            }
        } finally {
            isDispatch.set(false);
        }
    }
}
