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
package dorkbox.systemTray.swingUI;

import java.awt.Font;
import java.awt.Insets;

import javax.swing.JMenuItem;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.ImageUtils;

class AdjustedJMenuItem extends JMenuItem {
    // get the scale (and multiply it to our font size) to get the new font size
    private static int scale = ImageUtils.ENTRY_SIZE / SystemTray.DEFAULT_MENU_SIZE;

    AdjustedJMenuItem() {
        super();

        Font font = getFont();
        int size = font.getSize();
        size = size * scale;
        setFont(new Font(font.getName(), font.getStyle(), size));
    }

    @Override
    public
    Insets getMargin() {
        Insets margin = super.getMargin();
        if (margin != null) {
            margin.set(2, -2, 2, 4);
        }
        return margin;
    }
}
