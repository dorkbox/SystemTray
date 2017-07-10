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
package dorkbox.systemTray.nativeUI;

import static com.sun.jna.platform.win32.WinDef.HMENU;
import static dorkbox.systemTray.jna.windows.User32.MF_BYPOSITION;
import static dorkbox.systemTray.nativeUI.WindowsMenu.MFT_OWNERDRAW;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.sun.jna.platform.win32.BaseTSD;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.windows.GDI32;
import dorkbox.systemTray.jna.windows.GetLastErrorException;
import dorkbox.systemTray.jna.windows.HBITMAPWrap;
import dorkbox.systemTray.jna.windows.User32;
import dorkbox.systemTray.jna.windows.structs.MENUITEMINFO;
import dorkbox.util.SwingUtil;

public class WindowsBaseMenuItem {

    private final int position;

    volatile HBITMAPWrap hbitmapWrapImage;
    volatile String text = "";
    volatile boolean enabled = true; // default is enabled

    // these have to be volatile, because they can be changed from any thread
    private volatile ActionListener callback;
    private volatile ActionEvent actionEvent;

    public
    WindowsBaseMenuItem(int position) {
        this.position = position;
    }

    void setText(final String text) {
        if (text != null) {
            this.text = text;
        } else {
            this.text = "";
        }
    }

    void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    void setImage(final File imageFile) {
        if (imageFile != null) {
            SwingUtil.invokeAndWaitQuietly(new Runnable() {
                @Override
                public
                void run() {
                    // has to run on swing EDT.
                    ImageIcon imageIcon = new ImageIcon(imageFile.getAbsolutePath());
                    // fully loads the image and returns when it's done loading the image
                    imageIcon = new ImageIcon(imageIcon.getImage());

                    hbitmapWrapImage = convertMenuImage(imageIcon);
                }
            });
        }
        else {
            hbitmapWrapImage = null;
        }
    }

    void setCallback(final ActionListener callback, final ActionEvent actionEvent) {
        this.callback = callback;
        this.actionEvent = actionEvent;
    }

    void fireCallback() {
        ActionEvent actionEvent = this.actionEvent;
        ActionListener callback = this.callback;

        if (callback != null) {
            try {
                callback.actionPerformed(actionEvent);
            } catch (Throwable throwable) {
                SystemTray.logger.error("Error calling menu entry {} click event.", this.text, throwable);
            }
        }
    }

    static
    BufferedImage createBitmap(Icon icon) {
        BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return bi;
    }

    private static HBITMAPWrap convertMenuImage(Icon icon) {
//        BufferedImage img = createBitmap(icon);

        int menubarHeight = WindowsMenu.getSystemMenuImageSize();

        BufferedImage scaledImage = new BufferedImage(menubarHeight, menubarHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

        icon.paintIcon(null, g, 0, 0);
//        g.drawImage(img, 0, 0, menubarHeight, menubarHeight, null);
        g.dispose();

        return new HBITMAPWrap(scaledImage);
    }

    void remove() {
        if (hbitmapWrapImage != null) {
            GDI32.DeleteObject(hbitmapWrapImage);
            hbitmapWrapImage = null;
        }
    }

    boolean hasImage() {
        return false;
    }

    void onCreateMenu(final HMENU parentNative, final boolean hasImages) {
//        setSpacerImage(hasImagesInMenu);

        if (!User32.IMPL.AppendMenu(parentNative, MFT_OWNERDRAW, position, null)) {
           throw new GetLastErrorException();
        }

        MENUITEMINFO mmi = new MENUITEMINFO();
        if (!User32.IMPL.GetMenuItemInfo(parentNative, position, false, mmi)) {
            throw new GetLastErrorException();
        }

        mmi.dwItemData = new BaseTSD.ULONG_PTR(position);
        mmi.fMask |= MENUITEMINFO.MIIM_DATA;

        if (!User32.IMPL.SetMenuItemInfo(parentNative, position, false, mmi)) {
            throw new GetLastErrorException();
        }
    }

    void onDeleteMenu(final HMENU parentNative) {
        remove();

        if (!User32.IMPL.DeleteMenu(parentNative, position, MF_BYPOSITION)) {
            throw new GetLastErrorException();
        }
    }
}
