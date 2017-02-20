/*
 * Copyright 2016 dorkbox, llc
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


import java.lang.reflect.Method;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.OS;

/**
 * Utility methods for JavaFX.
 * <p>
 * We use reflection for these methods so that we can compile everything under Java 1.6 (which doesn't have JavaFX).
 */
public
class JavaFX {

    // Methods are cached for performance
    private static final Method dispatchMethod;
    private static final Method isEventThreadMethod;
    private static final Object isEventThreadObject;

    static {
        Method _isEventThreadMethod = null;
        Method _dispatchMethod = null;
        Object _isEventThreadObject = null;

        try {
            Class<?> clazz = Class.forName("javafx.application.Platform");
            _dispatchMethod = clazz.getMethod("runLater", Runnable.class);

            // JAVA 7
            // javafx.application.Platform.isFxApplicationThread();

            // JAVA 8
            // com.sun.javafx.tk.Toolkit.getToolkit().isFxUserThread();
            if (OS.javaVersion <= 7) {
                clazz = Class.forName("javafx.application.Platform");
                _isEventThreadMethod = clazz.getMethod("isFxApplicationThread");
                _isEventThreadObject = null;
            } else {
                clazz = Class.forName("com.sun.javafx.tk.Toolkit");
                _isEventThreadMethod = clazz.getMethod("getToolkit");

                _isEventThreadObject = _isEventThreadMethod.invoke(null);
                _isEventThreadMethod = _isEventThreadObject.getClass()
                            .getMethod("isFxUserThread", (java.lang.Class<?>[])null);
            }
        } catch (Throwable e) {
            SystemTray.logger.error("Cannot initialize JavaFX", e);
        }

        dispatchMethod = _dispatchMethod;
        isEventThreadMethod = _isEventThreadMethod;
        isEventThreadObject = _isEventThreadObject;
    }

    public static
    void init() {
        if (dispatchMethod == null || isEventThreadMethod == null) {
            SystemTray.logger.error("Unable to initialize JavaFX! Please create an issue with your OS and Java " +
                                    "version so we may further investigate this issue.");
        }
    }


    public static
    void dispatch(final Runnable runnable) {
//         javafx.application.Platform.runLater(runnable);

        try {
            dispatchMethod.invoke(null, runnable);
        } catch (Throwable e) {
            SystemTray.logger.error("Unable to execute JavaFX runLater(). Please create an issue with your OS and Java " +
                                    "version so we may further investigate this issue.");
        }
    }

    public static
    boolean isEventThread() {
        // JAVA 7
        // javafx.application.Platform.isFxApplicationThread();

        // JAVA 8
        // com.sun.javafx.tk.Toolkit.getToolkit().isFxUserThread();

        try {
            if (OS.javaVersion <= 7) {
                return (Boolean) isEventThreadMethod.invoke(null);
            } else {
                return (Boolean) isEventThreadMethod.invoke(isEventThreadObject, (java.lang.Class<?>[])null);
            }
        } catch (Throwable e) {
            SystemTray.logger.error("Unable to check if JavaFX is in the event thread. Please create an issue with your OS and Java " +
                                    "version so we may further investigate this issue.");
        }

        return false;
    }

    public static
    void onShutdown(final Runnable runnable) {
        // com.sun.javafx.tk.Toolkit.getToolkit()
        //                          .addShutdownHook(runnable);

        try {
            Class<?> clazz = Class.forName("com.sun.javafx.tk.Toolkit");
            Method method = clazz.getMethod("getToolkit");
            Object o = method.invoke(null);
            Method m = o.getClass()
                               .getMethod("addShutdownHook", Runnable.class);
            m.invoke(o, runnable);
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                SystemTray.logger.error("Cannot initialize JavaFX", e);
            }
            SystemTray.logger.error("Unable to insert shutdown hook into JavaFX. Please create an issue with your OS and Java " +
                                    "version so we may further investigate this issue.");
        }
    }
}
