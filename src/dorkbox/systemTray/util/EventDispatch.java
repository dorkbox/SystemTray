package dorkbox.systemTray.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dorkbox.util.NamedThreadFactory;

/**
 * Adds events to a single thread event dispatch, so that regardless of OS, all event callbacks happen on the same thread -- which is NOT
 * the GTK/AWT/SWING event dispatch thread. There can be odd peculariaties across on GTK with how AWT/SWING react with the GTK Event
 * Dispatch Thread.
 */
public
class EventDispatch {
    private static final Executor eventDispatchExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SystemTray", true));

    /**
     * Schedule an event to occur sometime in the future.
     */
    public static
    void runLater(Runnable runnable) {
        eventDispatchExecutor.execute(runnable);
    }
}
