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
package dorkbox.systemTray.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JComponent;

import com.sun.java.swing.plaf.windows.WindowsMenuUI;

/**
 * The LAST version of the JVM this will compile with is Java8 and it will not compile on anything higher.
 *
 * WindowsXP with Java8+ is ALSO no longer supported by Oracle or anyone else.
 */
class WindowsXpMenuUI extends WindowsMenuUI {
    @Override
    public
    void installUI(final JComponent c) {
        super.installUI(c);
    }

    @Override
    protected
    void paintMenuItem(Graphics g, JComponent c, Icon checkIcon, Icon arrowIcon, Color background, Color foreground, int defaultTextIconGap) {
        super.paintMenuItem(g, c, null, arrowIcon, background, foreground, defaultTextIconGap);
    }


    @Override
    public
    Dimension getPreferredSize(JComponent c) {
        return getPreferredMenuItemSize(c, null, arrowIcon, defaultTextIconGap);
    }

    @Override
    protected
    Dimension getPreferredMenuItemSize(final JComponent c, final Icon checkIcon, final Icon arrowIcon, final int defaultTextIconGap) {
        return super.getPreferredMenuItemSize(c, null, arrowIcon, defaultTextIconGap);
    }
}
