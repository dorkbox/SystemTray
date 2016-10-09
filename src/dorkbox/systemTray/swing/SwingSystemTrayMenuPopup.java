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
package dorkbox.systemTray.swing;

import java.awt.Frame;
import java.awt.Image;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.OS;

/**
 * This custom popup is required if we want to be able to show images on the menu,
 *
 * This is our "golden standard" since we have 100% control over it.
 */
class SwingSystemTrayMenuPopup extends JPopupMenu {
    private static final long serialVersionUID = 1L;

    // NOTE: we can use the "hidden dialog" focus window trick...
    private JDialog hiddenDialog;
    private volatile File iconFile;

    @SuppressWarnings("unchecked")
    SwingSystemTrayMenuPopup() {
        super();
        setFocusable(true);
//        setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0)); // borderUI resource border type will get changed!
        setBorder(new EmptyBorder(1, 1, 1, 1));


        // Initialize the hidden dialog as a headless, title-less dialog window
        hiddenDialog = new JDialog((Frame)null, "Tray menu");
        hiddenDialog.setUndecorated(true);
        hiddenDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        hiddenDialog.setAlwaysOnTop(true);

        // on Linux, the following two entries will **MOST OF THE TIME** prevent the hidden dialog from showing in the task-bar
        // on MacOS, you need "special permission" to not have a hidden dialog show on the dock.
        hiddenDialog.getContentPane().setLayout(null);

        // this is java 1.7, so we have to use reflection. It's not critical for this to be set, but it helps hide the title in the taskbar
        // hiddenDialog.setType(Window.Type.POPUP);
        if (OS.javaVersion >= 7) {
            try {
                Class<? extends JDialog> hiddenDialogClass = hiddenDialog.getClass();
                Method[] methods = hiddenDialogClass.getMethods();
                for (Method method : methods) {
                    if (method.getName()
                              .equals("setType")) {

                        Class<Enum> cl = (Class<Enum>) Class.forName("java.awt.Window$Type");
                        method.invoke(hiddenDialog, Enum.valueOf(cl, "POPUP"));
                        break;
                    }
                }
            } catch (Exception e) {
                SystemTray.logger.error("Error setting the tray popup menu type. The parent window might show on the task bar.");
            }
        }

        hiddenDialog.pack();
        hiddenDialog.setBounds(0,0,0,0);

        addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                hiddenDialog.setVisible(false);
                hiddenDialog.toBack();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        // Add the window focus listener to the hidden dialog
        hiddenDialog.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowLostFocus (WindowEvent we ) {
                SwingSystemTrayMenuPopup.this.setVisible(false);
            }
            @Override
            public void windowGainedFocus (WindowEvent we) {
            }
        });
    }

    /**
     * Sets the icon for the title-bar, so IF it shows in the task-bar, it will have the corresponding icon as the SystemTray icon
     */
    void setIcon(final File iconFile) {
        if (this.iconFile == null || !this.iconFile.equals(iconFile)) {
            this.iconFile = iconFile;

            try {
                Image image = new ImageIcon(ImageIO.read(iconFile)).getImage();
                image.flush();

                // we set the dialog window to have the same icon as what is on the system tray
                hiddenDialog.setIconImage(image);
            } catch (IOException e) {
                SystemTray.logger.error("Error setting the icon for the popup menu task tray dialog");
            }
        }
    }

    void doShow(final int x, final int y) {
        // critical to get the keyboard listeners working for the popup menu
        setInvoker(hiddenDialog.getContentPane());

        hiddenDialog.setLocation(x, y);
        hiddenDialog.setVisible(true);

        setLocation(x, y);
        setVisible(true);
        requestFocusInWindow();
    }

    void close() {
        hiddenDialog.setVisible(false);
        hiddenDialog.dispatchEvent(new WindowEvent(hiddenDialog, WindowEvent.WINDOW_CLOSING));
    }
}
