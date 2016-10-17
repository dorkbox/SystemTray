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
package dorkbox.systemTray.nativeUI;

import static java.awt.Font.DIALOG;

import java.awt.Font;
import java.awt.MenuItem;
import java.awt.event.ActionListener;
import java.io.File;

import dorkbox.systemTray.Status;

class AwtEntryStatus extends AwtEntry implements Status {

    // this is ALWAYS called on the EDT.
    AwtEntryStatus(final AwtMenu parent, final String label) {
        super(parent, new MenuItem());
        setText(label);
    }

    // called in the EDT thread
    @Override
    void renderText(final String text) {
        Font font = _native.getFont();
        if (font == null) {
            font = new Font(DIALOG, Font.BOLD, 12); // the default font used for dialogs.
        } else {
            font = font.deriveFont(Font.BOLD);
        }

        _native.setFont(font);
        _native.setLabel(text);

        // this makes sure it can't be selected
        _native.setEnabled(false);
    }

    @Override
    void setImage_(final File imageFile) {
    }

    @Override
    void removePrivate() {
    }

    @Override
    public
    void setShortcut(final char key) {
    }

    @Override
    public
    boolean hasImage() {
        return false;
    }

    @Override
    public
    void setCallback(final ActionListener callback) {
    }
}
