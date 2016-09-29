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

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;

import dorkbox.util.DelayTimer;
import dorkbox.util.Property;
import dorkbox.util.SwingUtil;

public
class SwingSystemTrayMenuPopup extends JPopupMenu {
    private static final long serialVersionUID = 1L;

    @Property
    /** Customize the delay (for hiding the popup) when the cursor is "moused out" of the popup menu */
    public static long POPUP_HIDE_DELAY = 1000L;

    @Property
    /** Customize the minimum amount of movement needed to cause the popup-delay to hide the popup */
    public static int MOVEMENT_DELTA = 20;

    private DelayTimer timer;

    protected volatile Point mouseClickLocation = null;

    private final List<JPopupMenu> trackedMenus = new ArrayList<JPopupMenu>(4);

//    protected boolean mouseStillOnMenu;
//    private JDialog hiddenDialog;

    public
    SwingSystemTrayMenuPopup() {
        super();
        setFocusable(true);
//        setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0)); // borderUI resource border type will get changed!
        setBorder(new EmptyBorder(1, 1, 1, 1));
        trackedMenus.add(this);

        this.timer = new DelayTimer("PopupMenuHider", true, new Runnable() {
            @Override
            public
            void run() {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        Point location = MouseInfo.getPointerInfo()
                                                  .getLocation();

                        // are we inside one of our tracked menus (the root menu is included)
                        synchronized (trackedMenus) {
                            for (JPopupMenu trackedMenu : trackedMenus) {
                                Point menuLocation = trackedMenu.getLocationOnScreen();
                                Dimension size = trackedMenu.getSize();

                                if (location.x >= menuLocation.x && location.x < menuLocation.x + size.width &&
                                    location.y >= menuLocation.y && location.y < menuLocation.y + size.height
                                   ) {

                                    // restart the timer
                                    SwingSystemTrayMenuPopup.this.timer.delay(POPUP_HIDE_DELAY);
                                    return;
                                }
                            }
                        }


                        // has the mouse pointer moved > delta pixels from it's original location (when the tray icon was clicked)?
                        if (mouseClickLocation != null &&
                            location.x >= mouseClickLocation.x - MOVEMENT_DELTA && location.x < mouseClickLocation.x + MOVEMENT_DELTA &&
                            location.y >= mouseClickLocation.y - MOVEMENT_DELTA && location.y < mouseClickLocation.y + MOVEMENT_DELTA
                            ) {

                            // restart the timer
                            SwingSystemTrayMenuPopup.this.timer.delay(POPUP_HIDE_DELAY);
                            return;
                        }


                        // else, we hide it
                        setVisible(false);
                    }
                });
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public
            void mouseExited(MouseEvent event) {
                // wait before checking if mouse is still on the menu
                SwingSystemTrayMenuPopup.this.timer.delay(SwingSystemTrayMenuPopup.this.timer.getDelay());
            }
        });


        // Does not work correctly on linux. a window in the taskbar still shows up
        // Initialize the hidden dialog as a headless, titleless dialog window
//        this.hiddenDialog = new JDialog((Frame)null);
//        this.hiddenDialog.setEnabled(false);
//        this.hiddenDialog.setUndecorated(true);
//
//        this.hiddenDialog.setSize(5, 5);
//        /* Add the window focus listener to the hidden dialog */
//        this.hiddenDialog.addWindowFocusListener(new WindowFocusListener () {
//            @Override
//            public void windowLostFocus (WindowEvent we ) {
//                SystemTrayMenuPopup.this.setVisible(false);
//            }
//            @Override
//            public void windowGainedFocus (WindowEvent we) {}
//        });
    }

    @Override
    public
    void setVisible(boolean makeVisible) {
        // only allow java to close this popup if our timer closed it
        this.timer.cancel();

        if (makeVisible) {
            mouseClickLocation = MouseInfo.getPointerInfo().getLocation();

            // if the mouse isn't inside the popup in x seconds, close the popup
            this.timer.delay(POPUP_HIDE_DELAY);
        }

//        this.hiddenDialog.setVisible(makeVisible);
        super.setVisible(makeVisible);
    }


    public
    void track(final JPopupMenu menu, final boolean visible) {
        if (visible) {
            synchronized (trackedMenus) {
                trackedMenus.add(menu);
            }
        } else {
            synchronized (trackedMenus) {
                trackedMenus.remove(menu);
            }
        }

        // restart the timer
        SwingSystemTrayMenuPopup.this.timer.delay(POPUP_HIDE_DELAY);
    }
}
