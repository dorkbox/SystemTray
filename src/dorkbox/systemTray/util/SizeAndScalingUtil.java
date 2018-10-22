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

import static com.sun.jna.platform.win32.WinUser.SM_CYMENUCHECK;
import static com.sun.jna.platform.win32.WinUser.SM_CYSMICON;

import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.ui.swing._SwingTray;
import dorkbox.systemTray.ui.swing._WindowsNativeTray;
import dorkbox.util.OS;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.linux.GtkTheme;
import dorkbox.util.jna.windows.User32;

public
class SizeAndScalingUtil {
    // the tray size as best as possible for the current OS
    static int TRAY_SIZE = 0;
    static int TRAY_MENU_SIZE = 0;

    public static
    double getMacOSScaleFactor() {
        // apple will ALWAYS return 2.0 on (apple) retina displays. This is enforced by apple

        // java6 way of getting it...
        if (OS.javaVersion == 6) {
            Object obj = Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor");
            if (obj instanceof Float) {
                // 1 = retina not available (regular display). Usually, for retina enabled displays returns 2.
                if ((Float) obj > 1.9F) {
                    // this means it's really 2.0F
                    return 2.0D;
                } else {
                    return 1.0D;
                }
            }
        }

        // java7+ way of getting it.
        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        try {
            Field field = graphicsDevice.getClass().getDeclaredField("scale");
            if (field != null) {
                field.setAccessible(true);
                Object scale = field.get(graphicsDevice);

                if (scale instanceof Integer) {
                    return (Integer) scale;
                }
            }
        } catch (Exception ignored) {
        }

        // apple.awt.contentScaleFactor or graphics scale does not ALWAYS work, so we draw on the screen THEN see if it was scaled.
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = GraphicsEnvironment.getLocalGraphicsEnvironment().createGraphics(img);
        AffineTransform transform = g.getFontRenderContext()
                                     .getTransform();

        return transform.getScaleX();
    }


    public static
    int getTrayImageSize() {
        if (TRAY_SIZE == 0) {
            if (OS.isLinux()) {
                TRAY_SIZE = GtkTheme.getIndicatorSize();
            }
            else if (OS.isMacOsX()) {
                // these are the standard icon sizes. From what I can tell, they are Apple defined, and cannot be changed.
                if (SizeAndScalingUtil.getMacOSScaleFactor() == 2.0D) {
                    TRAY_SIZE = 44;
                } else {
                    TRAY_SIZE = 22;
                }
            }
            else if (OS.isWindows()) {
                TRAY_SIZE = User32.User32.GetSystemMetrics(SM_CYSMICON);
            } else {
                // reasonable default
                TRAY_SIZE = 32;
            }
        }

        if (TRAY_SIZE == 0) {
            // reasonable default
            TRAY_SIZE = 32;
        }

        return TRAY_SIZE;
}

    public static
    int getMenuImageSize(final Class<? extends Tray> trayType) {
        if (TRAY_MENU_SIZE == 0) {
            if (OS.isMacOsX()) {
                // Note: Mac (AWT) does not have images in the menu.
                // these are the standard icon sizes. From what I can tell, they are Apple defined, and cannot be changed.
                if (SizeAndScalingUtil.getMacOSScaleFactor() == 2.0D) {
                    TRAY_MENU_SIZE = 32;
                }
                else {
                    TRAY_MENU_SIZE = 16;
                }
            }
            else if ((trayType == _SwingTray.class) || (trayType == _WindowsNativeTray.class)) {
                // Java does not scale the menu item IMAGE **AT ALL**, we must provide the correct size to begin with

                if (OS.isWindows()) {
                    // http://kynosarges.org/WindowsDpi.html

                    //                     image-size/menu-height
                    //  96 DPI = 100% actual size: 14/17
                    // 144 DPI = 150% actual size: 24/29

                    // gets the height of the default checkmark size, adjusted
                    // This is the closest image size we can get to the actual size programmatically. This is a LOT closer that checking the
                    // largest size a JMenu image can be before the menu size changes.
                    TRAY_MENU_SIZE = User32.User32.GetSystemMetrics(SM_CYMENUCHECK) - 1;

                    //                   image-size/menu-height
                    //  96 DPI = 100% mark size: 14/20
                    // 144 DPI = 150% mark size: 24/30
                } else {
                    final AtomicInteger iconSize = new AtomicInteger();

                    SwingUtil.invokeAndWaitQuietly(new Runnable() {
                        @Override
                        public
                        void run() {
                            JMenuItem jMenuItem = new JMenuItem();

                            // do the same modifications that would also happen (if specified) for the actual displayed menu items
                            if (SystemTray.SWING_UI != null) {
                                jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                            }

                            // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
                            int height = SwingUtil.getLargestIconHeightForButton(jMenuItem);
                            iconSize.set(height);
                        }
                    });
                    TRAY_MENU_SIZE = iconSize.get();
                }
            }
            else if (OS.isLinux()) {
                // AppIndicator or GtkStatusIcon
                TRAY_MENU_SIZE = GtkTheme.getMenuEntryImageSize();
            } else {
                // reasonable default
                TRAY_MENU_SIZE = 16;
            }
        }

        return TRAY_MENU_SIZE;
    }
}
