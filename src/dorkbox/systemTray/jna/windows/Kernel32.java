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
package dorkbox.systemTray.jna.windows;

import com.sun.jna.Memory;
import com.sun.jna.ptr.PointerByReference;

public
class Kernel32 {
    public static
    String getLastErrorMessage() {
        // has to be Kernel32.INSTANCE, otherwise it will crash the JVM
        int hresult = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetLastError();
        if (hresult == 0) {
            return "HRESULT: 0x0 [No Error]";
        } else {
            Memory memory = new Memory(1024);
            PointerByReference reference = new PointerByReference(memory);

            // Must be Kernel32.INSTANCE because of how it pulls in variety arguments.
            com.sun.jna.platform.win32.Kernel32.INSTANCE.FormatMessage(com.sun.jna.platform.win32.Kernel32.FORMAT_MESSAGE_FROM_SYSTEM, null, hresult, 0, reference, (int) memory.size(), null);

            String memoryMessage = reference.getPointer()
                                            .getString(0, true);
            memoryMessage = memoryMessage.trim();

            return String.format("HRESULT: 0x%08x [%s]", hresult, memoryMessage);
        }
    }
}
