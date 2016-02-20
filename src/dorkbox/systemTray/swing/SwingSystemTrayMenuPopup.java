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

import dorkbox.util.DelayTimer;
import dorkbox.util.Property;
import dorkbox.util.SwingUtil;

import javax.swing.JPopupMenu;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class SwingSystemTrayMenuPopup extends JPopupMenu {
    private static final long serialVersionUID = 1L;

    @Property
    /** Customize the delay (for hiding the popup) when the cursor is "moused out" of the popup menu */
    public static long POPUP_HIDE_DELAY = 1000L;

    @Property
    /** Customize the minimum amount of movement needed to cause the popup-delay to hide the popup */
    public static int MOVEMENT_DELTA = 20;

    private DelayTimer timer;

    protected volatile Point previousLocation = null;

//    protected boolean mouseStillOnMenu;
//    private JDialog hiddenDialog;

    SwingSystemTrayMenuPopup() {
        super();
        setFocusable(true);
//        setBorder(new BorderUIResource.EmptyBorderUIResource(0,0,0,0));

        this.timer = new DelayTimer("PopupMenuHider", true, new Runnable() {


            @Override
            public
            void run() {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        Point location = MouseInfo.getPointerInfo().getLocation();
                        Point menuLocation = getLocationOnScreen();
                        Dimension size = getSize();

                        // is the mouse pointer still inside of the popup?
                        if (location.x >= menuLocation.x && location.x < menuLocation.x + size.width &&
                            location.y >= menuLocation.y && location.y < menuLocation.y + size.height) {

                            // restart the timer
                            SwingSystemTrayMenuPopup.this.timer.delay(POPUP_HIDE_DELAY);
                        }

                        // has the mouse pointer moved > delta pixels from it's original location (when the tray icon was clicked)?
                        else if (previousLocation != null &&
                            location.x >= previousLocation.x - MOVEMENT_DELTA && location.x < previousLocation.x + MOVEMENT_DELTA &&
                            location.y >= previousLocation.y - MOVEMENT_DELTA && location.y < previousLocation.y + MOVEMENT_DELTA) {

                            // restart the timer
                            SwingSystemTrayMenuPopup.this.timer.delay(POPUP_HIDE_DELAY);
                        }

                        else {
                            // else, we hide it
                            setVisible(false);
                        }
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
        this.timer.cancel();

        if (makeVisible) {
            previousLocation = MouseInfo.getPointerInfo().getLocation();

            // if the mouse isn't inside the popup in x seconds, close the popup
            this.timer.delay(POPUP_HIDE_DELAY);
        }

//        this.hiddenDialog.setVisible(makeVisible);
        super.setVisible(makeVisible);
    }
}
