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
import java.awt.Component;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.PopupMenuUI;
import javax.swing.plaf.SeparatorUI;
import javax.swing.plaf.metal.MetalBorders;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.ui.swing.SwingUIFactory;
import dorkbox.util.swing.DefaultMenuItemUI;
import dorkbox.util.swing.DefaultPopupMenuUI;
import dorkbox.util.swing.DefaultSeparatorUI;

/**
 * Factory to allow for Look & Feel of the Swing UI components in the SystemTray.
 *
 * This implementation is provided as an example of what looks reasonable on our systems for Nimbus. Naturally, everyone will have
 * different systems and thus will want to change this based on their own, specified Swing L&F.
 *
 * NOTICE: components can ALSO have different sizes attached to them, resulting in different sized components
 * mini
 *       myButton.putClientProperty("JComponent.sizeVariant", "mini");
 * small
 *       mySlider.putClientProperty("JComponent.sizeVariant", "small");
 * large
 *       myTextField.putClientProperty("JComponent.sizeVariant", "large");
 */
public
class LinuxSwingUI implements SwingUIFactory {

    public static class Metal_MenuItemBorder extends MetalBorders.MenuItemBorder {
        private final int verticalPadding;

        Metal_MenuItemBorder(int verticalPadding) {
            this.verticalPadding = verticalPadding;
        }

        @Override
        public
        Insets getBorderInsets(final Component c, final Insets newInsets) {
            newInsets.set(verticalPadding, 2, verticalPadding, 2);
            return newInsets;
        }
    }

    private static PopupMenuUI popupMenuUI = null;
    private static MenuItemUI menuItemUI = null;
    private static SeparatorUI separatorUI = null;

    /**
     * Allows one to specify the Look & Feel of the menus (The main SystemTray and sub-menus)
     *
     * @param jPopupMenu the swing JPopupMenu that is displayed when one clicks on the System Tray icon
     * @param entry the entry which is bound to the menu, or null if it is the main SystemTray menu.
     *
     * @return the UI used to customize the Look & Feel of the SystemTray menu + sub-menus
     */
    @Override
    public
    PopupMenuUI getMenuUI(final JPopupMenu jPopupMenu, final Menu entry) {
        if (popupMenuUI == null) {
            popupMenuUI = new DefaultPopupMenuUI(jPopupMenu) {
                @Override
                public
                void installUI(final JComponent c) {
                    super.installUI(c);

                    JPopupMenu popupMenu = (JPopupMenu) c;

                    // borderUI resource border type will get changed internally!
                    // setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));
                    popupMenu.setBorder(new EmptyBorder(1, 1, 1, 1));
                }
            };
        }

        return popupMenuUI;
    }

    /**
     * Allows one to specify the Look & Feel of a menu entry
     *
     * @param jMenuItem the swing JMenuItem that is displayed in the menu
     * @param entry the entry which is bound to the JMenuItem. Can be null during initialization.
     *
     * @return the UI used to customize the Look & Feel of the menu entry
     */
    @Override
    public
    MenuItemUI getItemUI(final JMenuItem jMenuItem, final Entry entry) {
        if (menuItemUI == null) {
            menuItemUI = new DefaultMenuItemUI(jMenuItem) {
                @Override
                public
                void installUI(final JComponent c) {
                    super.installUI(c);

                    JMenuItem menuItem = (JMenuItem) c;
                    menuItem.setIconTextGap(8);
                    // the original is hardcoded to always be 2 (top/bottom). We want this to be larger, so the vertical spacing looks like
                    // other menus
                    c.setBorder(new Metal_MenuItemBorder(4));
                }
            };
        }

        return menuItemUI;
    }

    /**
     * Allows one to specify the Look & Feel of a menu separator entry
     *
     * @param jSeparator the swing JSeparator that is displayed in the menu
     *
     * @return the UI used to customize the Look & Feel of a menu separator entry
     */
    @Override
    public
    SeparatorUI getSeparatorUI(final JSeparator jSeparator) {
        if (separatorUI == null) {
            separatorUI = new DefaultSeparatorUI(jSeparator);
        }

        return separatorUI;
    }

    /**
     * This saves a vector CheckMark to a correctly sized PNG file. The checkmark image will ALWAYS be centered in the targetImageSize
     * (which is square)
     *
     * @param color the color of the CheckMark
     * @param checkMarkSize the size of the CheckMark inside the image. (does not include padding)
     *
     * @return the full path to the checkmark image
     */
    @Override
    public
    String getCheckMarkIcon(final Color color, final int checkMarkSize, final int targetImageSize) {
        return HeavyCheckMark.get(color, checkMarkSize, targetImageSize);
    }
}
