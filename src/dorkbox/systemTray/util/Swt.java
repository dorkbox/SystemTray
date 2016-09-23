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

/**
 * Utility methods for SWT.
 * <p>
 * SWT system tray types are just GTK trays.
 */
public
class Swt {
    public static
    void dispatch(final Runnable runnable) {
         org.eclipse.swt.widgets.Display.getCurrent()
                                        .asyncExec(runnable);
    }

    public static
    void onShutdown(final Runnable runnable) {
        org.eclipse.swt.widgets.Display.getCurrent()
                                       .getShells()[0].addListener(org.eclipse.swt.SWT.Close, new org.eclipse.swt.widgets.Listener() {
            @Override
            public
            void handleEvent(final org.eclipse.swt.widgets.Event event) {
                runnable.run();
            }
        });
    }
}
