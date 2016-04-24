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


We also cater to the *lowest-common-denominator* when it comes to system-tray/indicator functionality, and there are some features that we don't support. 
Specifically: **tooltips**. Rather a stupid decision, IMHO, but for more information why, ask Mark Shuttleworth. 
See: https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12



Please be aware if you run into problems: 
 - **JavaFX** uses **GTK2**. We try to autodetect this, but there might be *some* situations where it doesn't work. Please set `SystemTray.COMPATIBILITY_MODE=true;` to solve this.
 - **SWT** can use GTK2 or GTK3, and if it happens to use GTK3, there will be all sorts of problems (it is incompatible). You must force SWT into *GTK2 mode* via `System.setProperty("SWT_GTK3", "0");` before SWT is initialized. Also, if there are problems with the autodetection, you can also set `SystemTray.COMPATIBILITY_MODE=true;`.
 


```
Customization parameters:

SystemTrayMenuPopup.POPUP_HIDE_DELAY   (type long, default value '1000L')
 - Customize the delay (for hiding the popup) when the cursor is "moused out" of the popup menu (Windows/fallback mode only)


SystemTrayMenuPopup.MOVEMENT_DELTA   (type int, default value '20')
 - Customize the minimum amount of movement needed to cause the popup-delay to hide the popup (Windows/fallback mode only)


GnomeShellExtension.ENABLE_SHELL_RESTART    (type boolean, default value 'true')
 - Permit the gnome-shell to be restarted when the extension is installed.


GnomeShellExtension.SHELL_RESTART_TIMEOUT_MILLIS   (type long, default value '5000L')
 - Default timeout to wait for the gnome-shell to completely restart. This is a best-guess estimate.


GnomeShellExtension.SHELL_RESTART_COMMAND   (type String, default value 'gnome-shell --replace &')
 - Command to restart the gnome-shell. It is recommended to start it in the background (hence '&')


SystemTray.TIMEOUT   (type int, default value '2')
 - How long to wait when updating menu entries before the request times-out
 
 
SystemTray.TRAY_SIZE   (type int, default value '22')
 - Size of the tray, so that the icon can properly scale based on OS. (if it's not exact)
 
 
SystemTray.FORCE_GTK2    (type boolean, default value 'false')
 -  Forces the system tray to always choose GTK2 (even when GTK3 might be available).
 
 
SystemTray.COMPATIBILITY_MODE    (type boolean, default value 'false')
 -  Forces the system to enter into JavaFX/SWT compatibility mode, where it will use GTK2 AND will not start/stop the GTK main loop.
    This is only necessary if autodetection fails.
    
 
SystemTray.ENABLE_SHUTDOWN_HOOK    (type boolean, default value 'true')
 -  When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also closed.
    This property is available to disable this functionality in situations where you don't want this to happen.
 
 
SystemTray.DEBUG    (type boolean, default value 'false')
     -  This property is provided for debugging any errors in the logic used to determine the system-tray type.
```
   
   
   
The test application is [on GitHub](https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java), and a *simple* example is as follows:
```
   this.systemTray = SystemTray.getSystemTray();
   if (systemTray == null) {
       throw new RuntimeException("Unable to load SystemTray!");
   }

   try {
       this.systemTray.setIcon("grey_icon.png");
   } catch (IOException e) {
       e.printStackTrace();
   }

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
Note: This project was heavily influenced by the excellent Lantern project,
      *Many* thanks to them for figuring out AppIndicators via JNA.
      https://github.com/getlantern/lantern
```
```
Note: Gnome-shell users will experience an extension install to support this
      functionality. Additionally, a shell restart is necessary for the extension
      to be registered by the shell. You can disable the restart behavior if you 
      like, and the 'system tray' functionality will be picked up on log out/in,
      or a system restart.
   
      Also, screw you gnome-project leads, for making it such a pain-in-the-ass
      to do something so incredibly simple and basic.
      
Note: Some desktop environments might use a dated version of libappindicator3, when 
      icon support in menus was removed, then put back.
      This library will try to load a GTK indicator instead when it can, or will 
      try to load libappindicator1 first. Thank you RedHat for putting it back.
      
      
ISSUES:
      'Trying to remove a child that doesn't believe we're it's parent.'
      
      This is a known appindicator bug, and is rather old. Some distributions use 
      an OLD version of libappindicator, and will see this error. 
         See: https://github.com/ValveSoftware/steam-for-linux/issues/1077
         
         
      'gsignal.c: signal 'child-added' is invalid for instance 'xyz' of type 'GtkMenu''
      This is a known appindicator bug, and is rather old. Some distributions use an 
      OLD version of libappindicator, and will see this error. 
      
      The fallout from this issue (ie: menu entries not displaying) has been 
      *worked around*, so the menus should still show correctly.
         See: https://askubuntu.com/questions/364594/has-the-appindicator-or-gtkmenu-api-changed-in-saucy
  
```


<h4>We now release to maven!</h4> 

There is a hard dependency in the POM file for the utilities library, which is an extremely small subset of a much larger library; including only what is *necessary* for this particular project to function.

This project is **kept in sync** with the utilities library, so "jar hell" is not an issue. Please note that the util library (in it's entirety) is not added since there are **many** dependencies that are not *necessary* for this project. No reason to require a massive amount of dependencies for one or two classes/methods. 
```
<dependency>
  <groupId>com.dorkbox</groupId>
  <artifactId>SystemTray</artifactId>
  <version>2.17</version>
</dependency>
```

Or if you don't want to use Maven, you can access the files directly here:  
https://oss.sonatype.org/content/repositories/releases/com/dorkbox/SystemTray/  
https://oss.sonatype.org/content/repositories/releases/com/dorkbox/SystemTray-Dorkbox-Util/  


https://repo1.maven.org/maven2/net/java/dev/jna/jna/  
https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/  


https://repo1.maven.org/maven2/org/slf4j/slf4j-api/  


<h2>License</h2>

This project is © 2014 dorkbox llc, and is distributed under the terms of the Apache v2.0 License. See file "LICENSE" for further references.

