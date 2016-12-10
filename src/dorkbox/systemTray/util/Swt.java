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

import static dorkbox.systemTray.SystemTray.logger;

import org.eclipse.swt.widgets.Display;

/**
 * Utility methods for SWT.
 * <p>
 * SWT system tray types are just GTK trays.
 */
public
class Swt {
    private static final Display currentDisplay;
    private static final Thread currentDisplayThread;

    static {
        // we MUST save this, otherwise it is "null" when methods are run from the swing EDT.
        currentDisplay = Display.getCurrent();

        currentDisplayThread = currentDisplay.getThread();
    }


    public static
    void init() {
        // empty method to initialize class
        if (currentDisplay == null) {
            logger.error("Unable to get the current display for SWT. Please create an issue with your OS and Java " +
                         "version so we may further investigate this issue.");
        }
    }

    public static
    void dispatch(final Runnable runnable) {
        currentDisplay.syncExec(runnable);
    }

    public static
    boolean isEventThread() {
        return Thread.currentThread() == currentDisplayThread;
    }

    public static
    void onShutdown(final Runnable runnable) {
        currentDisplay.getShells()[0].addListener(org.eclipse.swt.SWT.Close, new org.eclipse.swt.widgets.Listener() {
            @Override
            public
            void handleEvent(final org.eclipse.swt.widgets.Event event) {
                runnable.run();
            }
        });
    }
}
