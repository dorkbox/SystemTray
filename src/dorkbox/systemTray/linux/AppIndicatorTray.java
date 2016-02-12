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
    private static final AppIndicator appindicator = AppIndicator.INSTANCE;

    private AppIndicator.AppIndicatorInstanceStruct appIndicator;

    public
    AppIndicatorTray(String iconPath) {
        gtk.gdk_threads_enter();

        this.appIndicator = appindicator.app_indicator_new(System.nanoTime() + "DBST", iconPath,
                                                           AppIndicator.CATEGORY_APPLICATION_STATUS);
        appindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_ACTIVE);

        gtk.gdk_threads_leave();

        GtkSupport.startGui();
    }

    @Override
    public synchronized
    void shutdown() {
        gtk.gdk_threads_enter();

        // this hides the indicator
        appindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_PASSIVE);
        Pointer p = this.appIndicator.getPointer();
        gobject.g_object_unref(p);

        // GC it
        this.appIndicator = null;

//        libgtk.gdk_threads_leave(); called by super class
        super.shutdown();
    }

    @Override
    protected synchronized
    void setIcon_(final String iconPath) {
        gtk.gdk_threads_enter();
        appindicator.app_indicator_set_icon(this.appIndicator, iconPath);
        gtk.gdk_threads_leave();
    }


    /**
     * Called inside the gdk_threads block. MUST BE AFTER THE ITEM IS ADDED/CHANGED from the menu
     */
    protected
    void onMenuAdded(final Pointer menu) {
        appindicator.app_indicator_set_menu(this.appIndicator, menu);
    }
}
