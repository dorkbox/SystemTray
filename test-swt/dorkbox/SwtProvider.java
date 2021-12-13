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

package dorkbox;


import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

import dorkbox.jna.rendering.ProviderType;
import dorkbox.jna.rendering.Renderer;
import dorkbox.os.OS;

public class SwtProvider implements Renderer {
    private static Display currentDisplay = null;
    private static Thread currentDisplayThread = null;

    static {
        // we MUST save this on init, otherwise it is "null" when methods are run from the swing EDT.
        currentDisplay = Display.getCurrent();
        currentDisplayThread = currentDisplay.getThread();
    }

    @Override
    public
    boolean isSupported() {
        // versions of SWT older than v4.4, are INCOMPATIBLE with us.
        // Of note, v4.3 is the "last released" version of SWT by eclipse AND IT WILL NOT WORK!!
        // for NEWER versions of SWT via maven, use http://maven-eclipse.github.io/maven
        return SWT.getVersion() > 4430;
    }

    @Override
    public
    ProviderType getType() {
        return ProviderType.SWT;
    }

    @Override
    public
    boolean alreadyRunning() {
        // ONLY true for SWT. JavaFX/etc are checked elsewhere
        return true;
    }

    @Override
    public
    boolean isEventThread() {
        return Thread.currentThread() == currentDisplayThread;
    }

    @Override
    public
    int getGtkVersion() {
        if (!OS.isLinux()) {
            return 0;
        }

        // Swt has a property that tells us the version information
        //        "org.eclipse.swt.internal.gtk.version=3.12.2"
        // Only possible Java9+ (so our case, Java11+ since 9 is no longer available, 11 is officially LTS)
        String version = OS.getProperty("org.eclipse.swt.internal.gtk.version", "2");
        if ("3".equals(version) || version.startsWith("3.")) {
            return 3;
        } else {
            return 2;
        }
    }

    @Override
    public
    boolean dispatch(final Runnable runnable) {
        if (isEventThread()) {
            // Run directly on the SWT event thread. If it's not on the dispatch thread, we will use the DEFAULT GTK to put it there.
            // We do not need to dispatch manually onto swt??
            runnable.run();

            // we only directly run if we are SWT, and in SWTs event thread
            return true;
        }

        // let the default handler manage it
        return false;
    }
}
