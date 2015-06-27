SystemTray
==========

Cross-platform **SystemTray** and **AppIndicator** support for java applications.

This libraries only purpose is to show *reasonably* decent system-tray icons and app-indicators with a simple popup-menu.

There are a number of problems on Linux with the Swing (and SWT) system-tray icons, namely that:

1. Swing system-tray icons on linux **do not** support transparent backgrounds (they have a white background)
2. Swing/SWT **do not** support app-indicators, which are necessary on more recent versions of linux
3. Swing popup menus look like crap  
    - system-tray icons use a JMenuPopup, which looks nicer than the java 'regular' one.
    - app-indicators use native popups.


This is for cross-platform use, specifically - linux 32/64, mac 32/64, and windows 32/64. Java 6+


```
To customize the delay (for hiding the popup) when the cursor is "moused out" of the 
   popup menu, change the value of 'SystemTrayMenuPopup.hidePopupDelay'

Not all system tray icons are the same size (default is 22px), so to properly scale the icon 
   to fit, change the value of 'SystemTray.TRAY_SIZE'
   
You might want to specify the root location of the icons used (to make it easier when
   specifying icons), change the value of 'SystemTray.ICON_PATH'
   
   
A *simple* example is as follows:
   // if using provided JNA jars. Not necessary if
   //using JNA from https://github.com/twall/jna
   System.load("Path to OS specific JNA jar");


   this.systemTray = SystemTray.create("Dorkbox");

   this.systemTray.createTray("grey_icon.png");
   this.systemTray.setStatus("Not Running", "grey_icon.png");
   
   this.systemTray.addMenuEntry("Quit", new SystemTrayMenuAction() {
      @Override
      public void onClick(SystemTray systemTray) {
        systemTray.removeTray();
        // exit or something.
      }
   });
```
```
Note: This library does NOT use SWT for system-tray support, only for the purpose
      of lessening the jar dependencies. Changing it to be SWT-based is not 
      difficult, just remember that SWT on linux *already* starts up the GTK main 
      event loop.
```
```
Note: If you use the attached JNA libraries, you **MUST** load the respective
      native libraries yourself, especially with JNA (as the loading logic has
      been removed from the jar)
```
```
Note: This project was heavily influence by the excellent Lantern project,
      *Many* thanks to them for figuring out AppIndicators via JNA.
      https://github.com/getlantern/lantern
```

