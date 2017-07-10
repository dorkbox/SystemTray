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

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;

/**
 * https://msdn.microsoft.com/en-us/library/windows/desktop/dd145037(v=vs.85).aspx
 */
public class LOGFONT extends Structure {

    public static final int LF_FACESIZE = 32;

    public static class ByValue extends LOGFONT implements Structure.ByValue {
    }

    public WinDef.LONG lfHeight;
    public WinDef.LONG lfWidth;
    public WinDef.LONG lfEscapement;
    public WinDef.LONG lfOrientation;
    public WinDef.LONG lfWeight;
    public byte lfItalic;
    public byte lfUnderline;
    public byte lfStrikeOut;
    public byte lfCharSet;
    public byte lfOutPrecision;
    public byte lfClipPrecision;
    public byte lfQuality;
    public byte lfPitchAndFamily;
    public char[] lfFaceName = new char[LF_FACESIZE];

    public LOGFONT() {
    }

    public LOGFONT(Pointer p) {
        super(p);

        read();
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("lfHeight",
                             "lfWidth",
                             "lfEscapement",
                             "lfOrientation",
                             "lfWeight",
                             "lfItalic",
                             "lfUnderline",
                             "lfStrikeOut",
                             "lfCharSet",
                             "lfOutPrecision",
                             "lfClipPrecision",
                             "lfQuality",
                             "lfPitchAndFamily",
                             "lfFaceName");
    }
}
