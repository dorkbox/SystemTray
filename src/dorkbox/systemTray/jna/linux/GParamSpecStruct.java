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

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

import dorkbox.util.Keep;

@Keep
public
class GParamSpecStruct extends Structure {
    public
    class ByValue extends GParamSpecStruct implements Structure.ByValue {}


    public
    class ByReference extends GParamSpecStruct implements Structure.ByReference {}


    public GTypeInstanceStruct g_type_instance;

    public String name;          /* interned string */
//    Pointer flags;
//    double 		 value_type;
//    double		 owner_type; /* class or interface using this property */

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("g_type_instance", "name");
    }
}
