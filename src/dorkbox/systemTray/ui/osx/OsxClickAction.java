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
package dorkbox.systemTray.ui.osx;

import java.util.HashMap;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

import dorkbox.jna.macos.cocoa.NSObject;
import dorkbox.jna.macos.cocoa.OsxClickCallback;
import dorkbox.jna.macos.foundation.ObjectiveC;

//@formatter:off
class OsxClickAction extends NSObject {
    // NOTE: The order in this file is CRITICAL to the behavior of this class. Changing the order of anything here will BREAK functionality!

    private static final Pointer registerObjectClass = ObjectiveC.objc_allocateClassPair(NSObject.objectClass, OsxClickAction.class.getSimpleName(), 0);
    private static final Pointer registerActionSelector = ObjectiveC.sel_registerName("action");
    static final Pointer action;

    static {
        Callback registerClickAction = new Callback() {
            @SuppressWarnings("unused")
            public
            void callback(Pointer self, Pointer selector) {
                if (selector.equals(registerActionSelector)) {
                    OsxClickAction action;

                    synchronized (clickMap){
                        action = clickMap.get(Pointer.nativeValue(self));
                    }

                    if (action != null){
                        action.callback.click();
                    }
                }
            }
        };

        if (!ObjectiveC.class_addMethod(registerObjectClass, registerActionSelector, registerClickAction, "v@:")) {
            throw new RuntimeException("Error initializing click action as a objective C class");
        }

        ObjectiveC.objc_registerClassPair(registerObjectClass);
        action = ObjectiveC.sel_getUid("action");
    }


    private static final Pointer objectClass = ObjectiveC.objc_lookUpClass(OsxClickAction.class.getSimpleName());
    private static final HashMap<Long, OsxClickAction> clickMap = new HashMap<Long, OsxClickAction>();


    private final OsxClickCallback callback;

    OsxClickAction(OsxClickCallback callback) {
        super(ObjectiveC.class_createInstance(objectClass, 0));

        synchronized (clickMap){
            clickMap.put(asPointer(), this);
        }

        this.callback = callback;
    }

    void remove() {
        clickMap.remove(asPointer());
    }

    @Override protected
    void finalize() throws Throwable {
        synchronized (clickMap){
            clickMap.remove(asPointer());
        }
        super.finalize();
    }
}
