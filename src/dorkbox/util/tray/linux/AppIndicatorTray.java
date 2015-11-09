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
package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;
import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.GtkSupport;

/**
 * Class for handling all system tray interactions.
 * <p/>
 * specialization for using app indicators in ubuntu unity
 * <p/>
 * Heavily modified from
 * <p/>
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public
class AppIndicatorTray extends GtkTypeSystemTray {
    private static final AppIndicator libappindicator = AppIndicator.INSTANCE;

    private volatile AppIndicator.AppIndicatorInstanceStruct appIndicator;

    public
    AppIndicatorTray(String iconName) {
        libgtk.gdk_threads_enter();
        this.appIndicator = libappindicator.app_indicator_new(System.nanoTime() + "DBST", "indicator-messages-new",
                                                              AppIndicator.CATEGORY_APPLICATION_STATUS);

        this.menu = libgtk.gtk_menu_new();
        libappindicator.app_indicator_set_menu(this.appIndicator, this.menu);

        libappindicator.app_indicator_set_icon(this.appIndicator, iconPath(iconName));
        libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_ACTIVE);

        libgtk.gdk_threads_leave();

        GtkSupport.startGui();
    }

    @Override
    public
    void shutdown() {
        libgtk.gdk_threads_enter();

        // this hides the indicator
        libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_PASSIVE);
        this.appIndicator.write();
        Pointer p = this.appIndicator.getPointer();
        libgobject.g_object_unref(p);

        // GC it
        this.appIndicator = null;

//        libgtk.gdk_threads_leave(); called by parent class
        super.shutdown();
    }

    @Override
    public
    void setIcon(final String iconName) {
        libgtk.gdk_threads_enter();
        libappindicator.app_indicator_set_icon(this.appIndicator, iconPath(iconName));
        libgtk.gdk_threads_leave();
    }
}
