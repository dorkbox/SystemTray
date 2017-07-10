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

import static com.sun.jna.platform.win32.WinDef.HBITMAP;
import static com.sun.jna.platform.win32.WinDef.HICON;

import com.sun.jna.Pointer;

import dorkbox.systemTray.jna.windows.structs.ICONINFO;

/**
 * http://www.pinvoke.net/default.aspx/user32.createiconindirect
 */
public class HICONWrap extends HICON {

    static HICON createIconIndirect(HBITMAP bm) {
        ICONINFO info = new ICONINFO();
        info.IsIcon = true;
        info.MaskBitmap = bm;
        info.ColorBitmap = bm;

        HICON hicon = User32.IMPL.CreateIconIndirect(info);
        if (hicon == null) {
            throw new GetLastErrorException();
        }

        return hicon;
    }

    private HBITMAPWrap bm;

    public HICONWrap() {
    }

    public HICONWrap(Pointer p) {
        super(p);
    }

    public HICONWrap(HBITMAPWrap bm) {
        this.bm = bm;
        setPointer(createIconIndirect(bm).getPointer());
    }

    public void close() {
        bm.close();

        if (Pointer.nativeValue(getPointer()) != 0) {
            GDI32.DeleteObject(this);
            setPointer(new Pointer(0));
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
