SystemTray
==========

Cross-platform **SystemTray** and **AppIndicator** support for java applications.

This libraries only purpose is to show *reasonably* decent system-tray icons and app-indicators with a simple popup-menu.

There are a number of problems on Linux with the Swing (and SWT) system-tray icons, namely that:

1. Swing system-tray icons on linux **do not** support transparent backgrounds (they have a white background)
2. Swing/SWT **do not** support app-indicators, which are necessary on more recent versions of gnu/linux distros.
3. Swing popup menus look like crap
    - swing-based system-tray uses a JMenuPopup, which looks nicer than the java 'regular' one.
    - app-indicators use native popups.
    - gtk-indicators use native popups.


This is for cross-platform use, specifically - linux 32/64, mac 32/64, and windows 32/64. Java 6+


```
Customization parameters:

SystemTrayMenuPopup.hidePopupDelay  (type long, default value '1000L')
 - Allows you to customize the delay (for hiding the popup) when the cursor is "moused out" of the popup menu


GnomeShellExtension.ENABLE_SHELL_RESTART    (type boolean, default value 'true')
 - Permit the gnome-shell to be restarted when the extension is installed.


GnomeShellExtension.SHELL_RESTART_TIMEOUT_MILLIS   (type long, default value '5000L')
 - Default timeout to wait for the gnome-shell to completely restart. This is a best-guess estimate.


GnomeShellExtension.SHELL_RESTART_COMMAND   (type String, default value 'gnome-shell --replace &')
 - Command to restart the gnome-shell. It is recommended to start it in the background (hence '&')


SystemTray.TRAY_SIZE   (type int, default value '24')
 - Size of the tray, so that the icon can properly scale based on OS. (if it's not exact). This only applies for Swing tray icons.
 - NOTE: Must be set after any other customization options, as a static call to SystemTray will cause initialization of the library.
 

SystemTray.ICON_PATH    (type String, default value '')
 - Location of the icon (to make it easier when specifying icons)
 - NOTE: Must be set after any other customization options, as a static call to SystemTray will cause initialization of the library.
of the library.
```
   
   
   
The test application is [on GitHub](https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java), and a *simple* example is as follows:
```
   // if using provided JNA jars. Not necessary if
   //using JNA from https://github.com/twall/jna
   System.load("Path to OS specific JNA jar");


   this.systemTray = SystemTray.create("grey_icon.png");

   this.systemTray.setStatus("Not Running");
   
   this.systemTray.addMenuEntry("Quit", new SystemTrayMenuAction() {
        @Override
        public
        void onClick(final SystemTray systemTray, final MenuEntry menuEntry) {
            System.exit(0);
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
Note: This project was heavily influenced by the excellent Lantern project,
      *Many* thanks to them for figuring out AppIndicators via JNA.
      https://github.com/getlantern/lantern
```
```
Note: Gnome-shell users will experience an extension install to support this
      functionality. Additionally, a shell restart is necessary for the extension
      to be registered by the shell. You can disable the restart behavior if you like,
      and the 'system tray' functionality will be picked up on log out/in, or a
      system restart.
   
      Also, screw you gnome-project leads, for making it such a pain-in-the-ass
      to do something so incredibly simple and basic.
      
Note: Some desktop environments might use a dated version of libappindicator, when 
      icon support in menus was removed, then put back. This happened in version 3.
      This library will try to load a GTK indicator instead when it can, or will try
      to load libappindicator1 first. Thank you RedHat for putting it back.
```

