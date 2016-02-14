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

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import dorkbox.util.Keep;

import java.util.Arrays;
import java.util.List;

/* bindings for libappindicator */
public
interface AppIndicator extends Library {
    // effing retarded. There are DIFFERENT versions, of which they all share the same basic compatibility (of the methods that
    // we use), however -- we cannot just LOAD via the 'base-name', we actually have to try each one. There are bash commands that
    // will tell us the linked library name, however - I'd rather not run bash commands to determine this.
    // This is so hacky it makes me sick.
    AppIndicator INSTANCE = AppIndicatorQuery.get();

    /** Necessary to provide warnings, because libappindicator3 won't properly work with GTK2 */
    boolean IS_VERSION_3 = AppIndicatorQuery.isVersion3;

    int CATEGORY_APPLICATION_STATUS = 0;
    int CATEGORY_COMMUNICATIONS = 1;
    int CATEGORY_SYSTEM_SERVICES = 2;
    int CATEGORY_HARDWARE = 3;
    int CATEGORY_OTHER = 4;

    int STATUS_PASSIVE = 0;
    int STATUS_ACTIVE = 1;
    int STATUS_ATTENTION = 2;


    @Keep
    class AppIndicatorInstanceStruct extends Structure {
        public Gobject.GObjectStruct parent;
        public Pointer priv;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("parent", "priv");
        }
    }

    // Note: AppIndicators DO NOT support tooltips, as per mark shuttleworth. Rather stupid IMHO.
    // See: https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12

    AppIndicatorInstanceStruct app_indicator_new(String id, String icon_name, int category);

    void app_indicator_set_status(AppIndicatorInstanceStruct self, int status);
    void app_indicator_set_menu(AppIndicatorInstanceStruct self, Pointer menu);
    void app_indicator_set_icon(AppIndicatorInstanceStruct self, String icon_name);
}
