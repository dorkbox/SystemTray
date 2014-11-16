package dorkbox.util.tray;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

import dorkbox.util.DelayTimer;

public class SystemTrayMenuPopup extends JPopupMenu {

  private static final long serialVersionUID = 1L;

  private DelayTimer timer;

  public SystemTrayMenuPopup() {
    super();

    this.timer = new DelayTimer("PopupMenuHider", true, new DelayTimer.Callback() {
      @Override
      public void execute() {
        SwingUtilities.invokeLater(new Runnable() {

          @Override
          public void run() {
            Point location = MouseInfo.getPointerInfo().getLocation();
            Point locationOnScreen = getLocationOnScreen();
            Dimension size = getSize();

            if (location.x >= locationOnScreen.x && location.x < locationOnScreen.x + size.width &&
                location.y >= locationOnScreen.y && location.y < locationOnScreen.y + size.height) {
              SystemTrayMenuPopup.this.timer.delay(SystemTrayMenuPopup.this.timer.getDelay());
            } else {
              setVisible(false);
            }
          }
        });
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent event) {
        // wait before checking if mouse is still on the menu
        SystemTrayMenuPopup.this.timer.delay(SystemTrayMenuPopup.this.timer.getDelay());
      }
    });
  }


  @Override
  public void setVisible(boolean b) {
    this.timer.cancel();

    if (b) {
      // if the mouse isn't inside the popup in 5 seconds, close the popup
      this.timer.delay(5000L);
    }

    super.setVisible(b);
  }
}
