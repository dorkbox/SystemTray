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

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.OS;
import dorkbox.util.jna.JnaHelper;

/**
 * Bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DeprecatedIsStillUsed", "WeakerAccess"})
class GtkLoader {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    static final boolean isGtk2;
    static final boolean isGtk3;
    static final boolean isLoaded;


    static final boolean alreadyRunningGTK;

    static Function gtk_status_icon_position_menu = null;

    static final int MAJOR;
    static final int MINOR;
    static final int MICRO;

    /*
     * We can have GTK v3 or v2.
     *
     * Observations:
     * JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */
    static {
        boolean shouldUseGtk2 = SystemTray.FORCE_GTK2;
        boolean _isGtk2 = false;
        boolean _isLoaded = false;
        boolean _alreadyRunningGTK = false;
        int major = 0;
        int minor = 0;
        int micro = 0;

        boolean shouldLoadGtk = !(OS.isWindows() || OS.isMacOsX());
        if (!shouldLoadGtk) {
            _isLoaded = true;
        }

        // we can force the system to use the swing indicator, which WORKS, but doesn't support transparency in the icon. However, there
        // are certain GTK functions we might want to use (even if we are Swing or AWT), so we load GTK anyways...

        // in some cases, we ALWAYS want to try GTK2 first
        String gtk2LibName = "gtk-x11-2.0";
        String gtk3LibName = "libgtk-3.so.0";


        if (!_isLoaded && shouldUseGtk2) {
            try {
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;
                Gtk gtk = new Gtk2();

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk.gtk_main_level() != 0;
                _isLoaded = true;

                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        // now for the defaults...

        // start with version 3
        if (!_isLoaded) {
            try {
                // ALSO map Gtk2.java to GTK3 library.
                JnaHelper.register(gtk3LibName, Gtk3.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk3LibName, "gtk_status_icon_position_menu");
                Gtk3 gtk = new Gtk3();

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk.gtk_main_level() != 0;
                _isLoaded = true;

                major = gtk.gtk_get_major_version();
                minor = gtk.gtk_get_minor_version();
                micro = gtk.gtk_get_micro_version();

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk3LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        // now version 2
        if (!_isLoaded) {
            try {
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = Gtk2.gtk_main_level() != 0;
                _isLoaded = true;

                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        if (shouldLoadGtk && _isLoaded) {
            isLoaded = true;

            // depending on how the system is initialized, SWT may, or may not, have the gtk_main loop running. It will EVENTUALLY run, so we
            // do not want to run our own GTK event loop.
            _alreadyRunningGTK |= SystemTray.isSwtLoaded;

            if (SystemTray.DEBUG) {
                logger.debug("Is the system already running GTK? {}", _alreadyRunningGTK);
            }

            alreadyRunningGTK = _alreadyRunningGTK;
            isGtk2 = _isGtk2;
            isGtk3 = !_isGtk2;

            MAJOR = major;
            MINOR = minor;
            MICRO = micro;
        }
        else {
            isLoaded = false;

            alreadyRunningGTK = false;
            isGtk2 = false;
            isGtk3 = false;

            MAJOR = 0;
            MINOR = 0;
            MICRO = 0;
        }

        if (shouldLoadGtk) {
            // now we output what version of GTK we have loaded.
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("GTK Version: " + MAJOR + "." + MINOR + "." + MICRO);
            }

            if (!_isLoaded) {
                throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                           "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
            }
        }
    }
}

