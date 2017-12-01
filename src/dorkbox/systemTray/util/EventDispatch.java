package dorkbox.systemTray.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dorkbox.util.NamedThreadFactory;

/**
 * Adds events to a single thread event dispatch, so that regardless of OS, all event callbacks happen on the same thread -- which is NOT
 * the GTK/AWT/SWING event dispatch thread. There can be ODD peculiarities across on GTK with how AWT/SWING react with the GTK Event
 * Dispatch Thread.
 */
public
class EventDispatch {
    private static ExecutorService eventDispatchExecutor = null;

    /**
     * Schedule an event to occur sometime in the future.
     */
    public static synchronized
    void runLater(Runnable runnable) {
        if (eventDispatchExecutor == null) {
            eventDispatchExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SystemTrayEventDispatch", false));
        }

        eventDispatchExecutor.execute(runnable);
    }

    /**
     * Shutdown the event dispatch
     */
    public static synchronized
    void shutdown() {
        if (eventDispatchExecutor != null) {
            eventDispatchExecutor.shutdownNow();
            eventDispatchExecutor = null;
        }
    }
}
