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

import static dorkbox.systemTray.SystemTray.logger;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;

/**
 * bindings for libappindicator
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DanglingJavadoc"})
public
class AppIndicator {
    public static boolean isVersion3 = false;
    private static boolean isLoaded = false;

    /**
     * Loader for AppIndicator, because it is absolutely mindboggling how those whom maintain the standard, can't agree to what that
     * standard library naming convention or features/API set is. We just try until we find one that work, and are able to map the
     * symbols we need. There are bash commands that will tell us the linked library name, however - I'd rather not run bash commands
     * to determine this.
     *
     * This is so hacky it makes me sick.
     */
    static {
        // objdump -T /usr/lib/x86_64-linux-gnu/libappindicator.so.1 | grep foo
        // objdump -T /usr/lib/x86_64-linux-gnu/libappindicator3.so.1 | grep foo

        // NOTE:
        //  ALSO WHAT VERSION OF GTK to use? appindiactor1 -> GTk2, appindicator3 -> GTK3.
        //     appindicator3 doesn't support menu icons via GTK2!!

        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_GTK_STATUSICON) {
            // if we force GTK type system tray, don't attempt to load AppIndicator libs
            if (SystemTray.DEBUG) {
                logger.debug("Forcing GTK tray, not using appindicator");
            }
            isLoaded = true;
        }

        if (!isLoaded && SystemTray.FORCE_GTK2) {
            // if specified, try loading appindicator1 first, maybe it's there?
            // note: we can have GTK2 + appindicator3, but NOT ALWAYS.
            try {
                final NativeLibrary library = JnaHelper.register("appindicator1", AppIndicator.class);
                if (library != null) {
                    isLoaded = true;
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.debug("Error loading GTK2 explicit appindicator1. {}", e.getMessage());
                }
            }
        }

        String nameToCheck1;
        String nameToCheck2;
        if (Gtk.isGtk2) {
            nameToCheck1 = "appindicator";
        }
        else {
            nameToCheck1 = "appindicator3";
        }

        // start with base version using whatever the OS specifies as the proper symbolic link
        if (!isLoaded) {
            try {
                final NativeLibrary library = JnaHelper.register(nameToCheck1, AppIndicator.class);
                String s = library.getFile().getName();

                if (SystemTray.DEBUG) {
                    logger.debug("Loading library (first attempt): '{}'", s);
                }

                if (s.contains("appindicator3")) {
                    isVersion3 = true;
                }

                isLoaded = true;
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.debug("Error loading library: '{}'. \n{}", nameToCheck1, e.getMessage());
                }
            }
        }

        // whoops. Symbolic links are bugged out. Look manually for it...
        // Super hacky way to do this.
        if (!isLoaded) {
            if (Gtk.isGtk2) {
                if (SystemTray.DEBUG) {
                    logger.debug("Checking GTK2 first for appIndicator");
                }

                // have to check gtk2 first
                for (int i = 0; i <= 10; i++) {
                    if (!isLoaded) {
                        try {
                            final NativeLibrary library = JnaHelper.register("appindicator" + i, AppIndicator.class);
                            String s = library.getFile().getName();

                            if (SystemTray.DEBUG) {
                                logger.debug("Loading library: '{}'", s);
                            }

                            // version 3 WILL NOT work with icons in the menu. This allows us to show a warning (in the System tray initialization)
                            if (i == 3 || s.contains("appindicator3")) {
                                isVersion3 = true;
                                if (SystemTray.DEBUG) {
                                    logger.debug("Unloading library: '{}'", s);
                                }
                                Native.unregister(AppIndicator.class);
                            }

                            isLoaded = true;
                            break;
                        } catch (Throwable e) {
                            if (SystemTray.DEBUG) {
                                logger.debug("Error loading library: '{}'. \n{}", "appindicator" + i, e.getMessage());
                            }
                        }
                    }
                }

            } else {
                // have to check gtk3 first (maybe it's there?)
                for (int i = 10; i >= 0; i--) {
                    if (!isLoaded) {
                        try {
                            final NativeLibrary library = JnaHelper.register("appindicator" + i, AppIndicator.class);
                            String s = library.getFile().getName();

                            if (SystemTray.DEBUG) {
                                logger.debug("Loading library: '{}'", s);
                            }

                            // version 3 WILL NOT work with icons in the menu. This allows us to show a warning (in the System tray initialization)
                            if (i == 3 || s.contains("appindicator3")) {
                                isVersion3 = true;
                            }

                            isLoaded = true;
                            break;
                        } catch (Throwable e) {
                            if (SystemTray.DEBUG) {
                                logger.debug("Error loading library: '{}'. \n{}", "appindicator" + i, e.getMessage());
                            }
                        }
                    }
                }
            }


        }

        // If we are GTK2, change the order we check and load libraries

        if (Gtk.isGtk2) {
            nameToCheck1 = "appindicator-gtk";
            nameToCheck2 = "appindicator-gtk3";
        }
        else {
            nameToCheck1 = "appindicator-gtk3";
            nameToCheck2 = "appindicator-gtk";
        }

        // another type. who knows...
        if (!isLoaded) {
            try {
                JnaHelper.register(nameToCheck1, AppIndicator.class);
                isLoaded = true;
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.debug("Error loading library: '{}'. \n{}", nameToCheck1, e.getMessage());
                }
            }
        }

        // this is HORRID. such a PITA
        if (!isLoaded) {
            try {
                JnaHelper.register(nameToCheck2, AppIndicator.class);
                isLoaded = true;
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.debug("Error loading library: '{}'. \n{}", nameToCheck2, e.getMessage());
                }
            }
        }
    }

    // Note: AppIndicators DO NOT support tooltips, as per mark shuttleworth. Rather stupid IMHO.
    // See: https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12

    public static final int CATEGORY_APPLICATION_STATUS = 0;
//    public static final int CATEGORY_COMMUNICATIONS = 1;
//    public static final int CATEGORY_SYSTEM_SERVICES = 2;
//    public static final int CATEGORY_HARDWARE = 3;
//    public static final int CATEGORY_OTHER = 4;

    public static final int STATUS_PASSIVE = 0;
    public static final int STATUS_ACTIVE = 1;
//    public static final int STATUS_ATTENTION = 2;


    public static native AppIndicatorInstanceStruct app_indicator_new(String id, String icon_name, int category);

    public static native void app_indicator_set_status(AppIndicatorInstanceStruct self, int status);
    public static native void app_indicator_set_menu(AppIndicatorInstanceStruct self, Pointer menu);
    public static native void app_indicator_set_icon(AppIndicatorInstanceStruct self, String icon_name);
}
