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
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;

/**
 * This custom popup is required, because we cannot close this popup by clicking OUTSIDE the popup. For whatever reason, that does not
 * work, so we must implement an "auto-hide" feature that checks if our mouse is still inside a menu every POPUP_HIDE_DELAY seconds
 */
public
class SwingSystemTrayMenuWindowsPopup extends JPopupMenu {
    private static final long serialVersionUID = 1L;

    // NOTE: we can use the "hidden dialog" focus window trick... only on windows and mac
    private JDialog hiddenDialog;

    SwingSystemTrayMenuWindowsPopup() {
        super();
        setFocusable(true);
//        setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0)); // borderUI resource border type will get changed!
        setBorder(new EmptyBorder(1, 1, 1, 1));


        // Does not work correctly on linux. a window in the taskbar still shows up
        // Initialize the hidden dialog as a headless, titleless dialog window
        this.hiddenDialog = new JDialog((Frame)null);
        this.hiddenDialog.setEnabled(false);
        this.hiddenDialog.setUndecorated(true);
        this.hiddenDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        this.hiddenDialog.setSize(1, 1);

        // Add the window focus listener to the hidden dialog
        this.hiddenDialog.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowLostFocus (WindowEvent we ) {
                SwingSystemTrayMenuWindowsPopup.this.setVisible(false);
            }
            @Override
            public void windowGainedFocus (WindowEvent we) {}
        });
    }

    @Override
    public
    void setVisible(boolean makeVisible) {
        this.hiddenDialog.setVisible(makeVisible);
        this.hiddenDialog.setEnabled(false);

        super.setVisible(makeVisible);
    }

    public
    void close() {
        this.hiddenDialog.setVisible(false);
        this.hiddenDialog.dispatchEvent(new WindowEvent(this.hiddenDialog, WindowEvent.WINDOW_CLOSING));
    }
}
