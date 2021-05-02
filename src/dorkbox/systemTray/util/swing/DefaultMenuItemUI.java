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
package dorkbox.systemTray.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.accessibility.Accessible;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.MenuItemUI;

public
class DefaultMenuItemUI extends MenuItemUI {
    private final ComponentUI ui;

    public
    DefaultMenuItemUI(final JComponent jComponent) {
        this.ui = UIManager.getDefaults()
                           .getUI(jComponent);
    }

    @Override
    public
    void installUI(final JComponent c) {
        ui.installUI(c);
    }

    @Override
    public
    void uninstallUI(final JComponent c) {
        ui.uninstallUI(c);
    }

    @Override
    public
    void paint(final Graphics g, final JComponent c) {
        ui.paint(g, c);
    }

    @Override
    public
    void update(final Graphics g, final JComponent c) {
        ui.update(g, c);
    }

    @Override
    public
    Dimension getPreferredSize(final JComponent c) {
        return ui.getPreferredSize(c);
    }

    @Override
    public
    Dimension getMinimumSize(final JComponent c) {
        return ui.getMinimumSize(c);
    }

    @Override
    public
    Dimension getMaximumSize(final JComponent c) {
        return ui.getMaximumSize(c);
    }

    @Override
    public
    boolean contains(final JComponent c, final int x, final int y) {
        return ui.contains(c, x, y);
    }

    @Override
    public
    int getBaseline(final JComponent c, final int width, final int height) {
        return ui.getBaseline(c, width, height);
    }

    @Override
    public
    Component.BaselineResizeBehavior getBaselineResizeBehavior(final JComponent c) {
        return ui.getBaselineResizeBehavior(c);
    }

    @Override
    public
    int getAccessibleChildrenCount(final JComponent c) {
        return ui.getAccessibleChildrenCount(c);
    }

    @Override
    public
    Accessible getAccessibleChild(final JComponent c, final int i) {
        return ui.getAccessibleChild(c, i);
    }
}
