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

import com.sun.jna.*;
import dorkbox.util.Keep;
import dorkbox.systemTray.linux.jna.Gtk.GdkEventButton;

import java.util.Arrays;
import java.util.List;

public
interface Gobject extends Library {
    Gobject INSTANCE = (Gobject) Native.loadLibrary("gobject-2.0", Gobject.class);

    @Keep
    class GTypeClassStruct extends Structure {
        public
        class ByValue extends GTypeClassStruct implements Structure.ByValue {}


        public
        class ByReference extends GTypeClassStruct implements Structure.ByReference {}


        public NativeLong g_type;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("g_type");
        }
    }


    @Keep
    class GTypeInstanceStruct extends Structure {
        public
        class ByValue extends GTypeInstanceStruct implements Structure.ByValue {}


        public
        class ByReference extends GTypeInstanceStruct implements Structure.ByReference {}


        public Pointer g_class;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("g_class");
        }
    }


    @Keep
    class GObjectStruct extends Structure {
        public
        class ByValue extends GObjectStruct implements Structure.ByValue {}


        public
        class ByReference extends GObjectStruct implements Structure.ByReference {}


        public GTypeInstanceStruct g_type_instance;
        public int ref_count;
        public Pointer qdata;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("g_type_instance", "ref_count", "qdata");
        }
    }


    @Keep
    class GObjectClassStruct extends Structure {
        public
        class ByValue extends GObjectClassStruct implements Structure.ByValue {}


        public
        class ByReference extends GObjectClassStruct implements Structure.ByReference {}


        public GTypeClassStruct g_type_class;
        public Pointer construct_properties;
        public Pointer constructor;
        public Pointer set_property;
        public Pointer get_property;
        public Pointer dispose;
        public Pointer finalize;
        public Pointer dispatch_properties_changed;
        public Pointer notify;
        public Pointer constructed;
        public NativeLong flags;
        public Pointer dummy1;
        public Pointer dummy2;
        public Pointer dummy3;
        public Pointer dummy4;
        public Pointer dummy5;
        public Pointer dummy6;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("g_type_class", "construct_properties", "constructor", "set_property", "get_property", "dispose",
                                 "finalize", "dispatch_properties_changed", "notify", "constructed", "flags", "dummy1", "dummy2", "dummy3",
                                 "dummy4", "dummy5", "dummy6");
        }
    }


    @Keep
    interface GCallback extends Callback {
        /**
         * @return Gtk.TRUE if we handled this event
         */
        int callback(Pointer instance, Pointer data);
    }


    @Keep
    interface GEventCallback extends Callback {
        void callback(Pointer instance, GdkEventButton event);
    }


    @Keep
    class xyPointer extends Structure {
        public int value;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("value");
        }
    }


    @Keep
    interface GPositionCallback extends Callback {
        void callback(Pointer menu, xyPointer x, xyPointer y, Pointer push_in_bool, Pointer user_data);
    }



    void g_free(Pointer object);
    void g_object_ref(Pointer object);
    void g_object_unref(Pointer object);
    void g_object_ref_sink(Pointer object);

    NativeLong g_signal_connect_data(Pointer instance, String detailed_signal, Callback c_handler, Pointer data, Pointer destroy_data,
                               int connect_flags);

    void g_signal_handler_disconnect(Pointer instance,  NativeLong longAddress);

    Pointer g_markup_printf_escaped(String pattern, String inputString);
}
