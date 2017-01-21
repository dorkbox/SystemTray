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

import com.sun.jna.Callback;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dorkbox.systemTray.jna.JnaHelper;

/**
 * bindings for libgobject-2.0
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class Gobject {

    static {
        try {
            NativeLibrary library = JnaHelper.register("gobject-2.0", Gobject.class);
            if (library == null) {
                logger.error("Error loading GObject library, it failed to load.");
            }
        } catch (Throwable e) {
            logger.error("Error loading GObject library, it failed to load {}", e.getMessage());
        }
    }

    // objdump -T /usr/lib/x86_64-linux-gnu/libgobject-2.0.so.0 | grep block

    public static native void g_object_get(Pointer object, String objectName, PointerByReference objectVal, Pointer nullValue);

    public static native void g_object_unref(Pointer object);

    public static native void g_object_force_floating(Pointer object);
    public static native void g_object_ref_sink(Pointer object);

    // note: the return type here MUST be long to avoid issues on freeBSD. NativeLong (previously used) worked on everything except BSD.
    public static native long g_signal_connect_object(Pointer instance, String detailed_signal, Callback c_handler, Pointer object, int connect_flags);

    public static native void g_signal_handler_block(Pointer instance, long handlerId);
    public static native void g_signal_handler_unblock(Pointer instance, long handlerId);
}
