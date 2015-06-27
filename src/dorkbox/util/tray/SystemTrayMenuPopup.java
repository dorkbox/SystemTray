package dorkbox.util.tray;

import dorkbox.util.DelayTimer;
import dorkbox.util.SwingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public
class SystemTrayMenuPopup extends JPopupMenu {
    private static final long serialVersionUID = 1L;

    /**
     * Allows you to customize the delay (for hiding the popup) when the cursor is "moused out" of the popup menu
     */
    public static long hidePopupDelay = 1000L;

    private DelayTimer timer;

//    protected boolean mouseStillOnMenu;
//    private JDialog hiddenDialog;

    public
    SystemTrayMenuPopup() {
        super();
        setFocusable(true);

        this.timer = new DelayTimer("PopupMenuHider", true, new DelayTimer.Callback() {
            @Override
            public
            void execute() {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        Point location = MouseInfo.getPointerInfo().getLocation();
                        Point locationOnScreen = getLocationOnScreen();
                        Dimension size = getSize();

                        if (location.x >= locationOnScreen.x && location.x < locationOnScreen.x + size.width &&
                            location.y >= locationOnScreen.y && location.y < locationOnScreen.y + size.height) {

                            SystemTrayMenuPopup.this.timer.delay(SystemTrayMenuPopup.this.timer.getDelay());
                        }
                        else {
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
                SystemTrayMenuPopup.this.timer.delay(SystemTrayMenuPopup.this.timer.getDelay());
            }
        });

        // Does not work correctly on linux. a window in the taskbar shows up.
        /* Initialize the hidden dialog as a headless, titleless dialog window */
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
            // if the mouse isn't inside the popup in x seconds, close the popup
            this.timer.delay(hidePopupDelay);
        }

//        this.hiddenDialog.setVisible(makeVisible);
        super.setVisible(makeVisible);
    }
}
