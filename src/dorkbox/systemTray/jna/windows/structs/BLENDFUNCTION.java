/*
 * Copyright 2017 dorkbox, llc
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
package dorkbox.systemTray.jna.windows.structs;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinUser;

public class BLENDFUNCTION extends Structure {
    public static class ByValue extends BLENDFUNCTION implements Structure.ByValue {
    }

    public static class ByReference extends BLENDFUNCTION implements Structure.ByReference {
    }

    public byte BlendOp = WinUser.AC_SRC_OVER; // only valid value
    public byte BlendFlags = 0; // only valid value
    public byte SourceConstantAlpha;
    public byte AlphaFormat;

    public
    BLENDFUNCTION() {
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("BlendOp", "BlendFlags", "SourceConstantAlpha", "AlphaFormat");
    }
}
