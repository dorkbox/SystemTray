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

package dorkbox.systemTray.linux.jna;

import com.sun.jna.Native;
import dorkbox.systemTray.SystemTray;

/**
 * Helper for AppIndicator, because it is absolutely mindboggling how those whom maintain the standard, can't agree to what that standard
 * library naming convention or features set is. We just try until we find one that work, and are able to map the symbols we need.
 */
class AppIndicatorQuery {

    /**
     * must call get() before accessing this! Only "AppIndicator" interface should access this!
     */
    static volatile boolean isVersion3 = false;

    /**
     * Is AppIndicator loaded yet?
     */
    static volatile boolean isLoaded = false;


    public static
    AppIndicator get() {
        // objdump -T /usr/lib/x86_64-linux-gnu/libappindicator.so.1 | grep foo
        // objdump -T /usr/lib/x86_64-linux-gnu/libappindicator3.so.1 | grep foo

        Object library;

        // NOTE: GtkSupport uses this info to figure out WHAT VERSION OF GTK to use: appindiactor1 -> GTk2, appindicator3 -> GTK3.

        if (SystemTray.FORCE_GTK2 || SystemTray.COMPATIBILITY_MODE) {
            // try loading appindicator1 first, maybe it's there?

            try {
                library = Native.loadLibrary("appindicator1", AppIndicator.class);
                if (library != null) {
                    return (AppIndicator) library;
                }
            } catch (Throwable ignored) {
            }
        }

        // start with base version
        try {
            library = Native.loadLibrary("appindicator", AppIndicator.class);
            if (library != null) {
                String s = library.toString();
                if (s.indexOf("appindicator3") > 0) {
                    isVersion3 = true;
                }

                isLoaded = true;
                return (AppIndicator) library;
            }
        } catch (Throwable ignored) {
        }

        // whoops. Symbolic links are bugged out. Look manually for it...

        try {
            library = Native.loadLibrary("appindicator1", AppIndicator.class);
            if (library != null) {
                return (AppIndicator) library;
            }
        } catch (Throwable ignored) {
        }

        // now check all others. super hacky way to do this.
        for (int i = 10; i >= 0; i--) {
            try {
                library = Native.loadLibrary("appindicator" + i, AppIndicator.class);
            } catch (Throwable ignored) {
                library = null;
            }

            if (library != null) {
                String s = library.toString();
                // version 3 WILL NOT work with icons in the menu. This allows us to show a warning (in the System tray initialization)
                if (i == 3 || s.indexOf("appindicator3") > 0) {
                    isVersion3 = true;
                }
                return (AppIndicator) library;
            }
        }

        // another type. who knows...
        try {
            library = Native.loadLibrary("appindicator-gtk", AppIndicator.class);
            if (library != null) {
                return (AppIndicator) library;
            }
        } catch (Throwable ignored) {
        }

        // this is HORRID. such a PITA
        try {
            library = Native.loadLibrary("appindicator-gtk3", AppIndicator.class);
            if (library != null) {
                return (AppIndicator) library;
            }
        } catch (Throwable ignored) {
        }

        throw new RuntimeException("We apologize for this, but we are unable to determine which the appIndicator library is in use, if " +
                                   "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
    }
}
