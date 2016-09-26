/*
 * Copyright 2014 dorkbox, llc
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
package dorkbox.systemTray.linux;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.linux.jna.AppIndicator;
import dorkbox.systemTray.linux.jna.AppIndicatorInstanceStruct;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;

/**
 * Class for handling all system tray interactions.
 * <p/>
 * specialization for using app indicators in ubuntu unity
 * <p/>
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public
class AppIndicatorTray extends GtkTypeSystemTray {
    private AppIndicatorInstanceStruct appIndicator;
    private boolean isActive = false;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    public
    AppIndicatorTray() {
        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_GTK_STATUSICON) {
            // if we force GTK type system tray, don't attempt to load AppIndicator libs
            throw new IllegalArgumentException("Unable to start AppIndicator if 'SystemTray.FORCE_TRAY_TYPE' is set to GTK");
        }

        Gtk.startGui();

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                appIndicator = AppIndicator.app_indicator_new(System.nanoTime() + "DBST", "", AppIndicator.CATEGORY_APPLICATION_STATUS);
            }
        });

        super.waitForStartup();

        ImageUtils.determineIconSize(SystemTray.TYPE_APP_INDICATOR);
    }

    @Override
    public
    void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            dispatch(new Runnable() {
                @Override
                public
                void run() {
                    // STATUS_PASSIVE hides the indicator
                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_PASSIVE);
                    Pointer p = appIndicator.getPointer();
                    Gobject.g_object_unref(p);

                    appIndicator = null;
                }
            });

            super.shutdown();
        }
    }

    @Override
    protected
    void setIcon_(final File iconFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                AppIndicator.app_indicator_set_icon(appIndicator, iconFile.getAbsolutePath());

                if (!isActive) {
                    isActive = true;

                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_ACTIVE);
                }
            }
        });
    }

    /**
     * MUST BE AFTER THE ITEM IS ADDED/CHANGED from the menu
     */
    protected
    void onMenuAdded(final Pointer menu) {
        // see: https://code.launchpad.net/~mterry/libappindicator/fix-menu-leak/+merge/53247
        AppIndicator.app_indicator_set_menu(appIndicator, menu);
    }
}
