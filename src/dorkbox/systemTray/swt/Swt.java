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
package dorkbox.systemTray.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

/**
 * SWT system tray types are just GTK trays.
 * <p>
 * This isn't possible with reflection - so we compile it AHEAD of time and save the bytecode (which is then included with the release)
 */
public
class Swt {
    public static
    void onShutdown(final Runnable runnable) {
        Display.getCurrent().getShells()[0].addListener(SWT.Close, new Listener() {
            @Override
            public
            void handleEvent(final Event event) {
                runnable.run();
            }
        });
    }
}
