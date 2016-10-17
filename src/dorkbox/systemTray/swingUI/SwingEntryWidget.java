/*
 * Copyright 2016 dorkbox, llc
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
package dorkbox.systemTray.swingUI;

import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JComponent;

// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
class SwingEntryWidget extends SwingEntry implements dorkbox.systemTray.Separator {

    // this is ALWAYS called on the EDT.
    SwingEntryWidget(final SwingMenu parent, JComponent widget) {
        super(parent, widget);

        _native.setEnabled(true);
    }

    // called in the EDT thread
    @Override
    void renderText(final String text) {
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
