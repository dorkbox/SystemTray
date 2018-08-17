SystemTray
==========
Professional, cross-platform **SystemTray** support for *Swing/AWT*, *GtkStatusIcon*, and *AppIndicator* on Java 6+.  


This library provides **OS Native** menus and **Swing/AWT** menus, depending on the OS and Desktop Environment and if AutoDetect (the default) is enabled. 

 - Linux/Unix will automatically choose *Native* (*GtkStatusIcon*, and *AppIndicator*) menus, Windows will choose *Swing*, and MacOS will choose *AWT*.
  
 - Please note that the *Native* and *AWT* menus follow the specified look and feel of that OS and are limited by what is supported on the OS. Consequently they are not consistent across all platforms and environments.
 
 - In most cases on Linux/Unix, *Native* menus are used. In cases where libraries are missing or there are un-resolvable GTK version conflicts, we try to fallback to using *Swing*.

&nbsp;  

The following unique problems are also solved by this library:  
 1. *Sun/Oracle* system-tray icons on Linux/Unix **do not** support images with transparent backgrounds  
 1. *Sun/Oracle* system-tray and *SWT* system-tray implementations **do not** support app-indicators, which are necessary on different distributions of Linux/Unix
 1. *GtkStatusIcons* on GNOME3 desktop environments are hidden by default  
 1. *Sun/Oracle* system-tray menus on Windows **look absolutely horrid**  
 1. *Sun/Oracle* system-tray icons on Windows are **hard-coded** to a max size of 24x24 (it was last updated in *2006*)  
 1. *Sun/Oracle* system-tray menus on MacOS **do not** always respond to both mouse buttons, where Apple menus do  
 1. Windows *native* menus **do not** support images attached to menu entries  
 1. Windows menus **do not** support a different L&F from the running application  
 1. Windows, Linux, and MacOSX menus (native or otherwise) do not support HiDPI configurations
 1. java.awt.Desktop.getDesktop() is **broken** when using GTK3 or on MacOS.



This is for cross-platform use, specifically - linux 32/64, mac 32/64, and windows 32/64. Java 6+

&nbsp;  
&nbsp;  


Problems and Restrictions
---------
 - **JavaFX** uses *GTK2* for Java <8, and *GTK2* or *GTK3* for Java 9+. We try to autodetect this, and are mostly successful. In *some* situations where it doesn't work. Please set `SystemTray.FORCE_GTK2=true;`, or to change JavaFX (9+), use `-Djdk.gtk.version=3` to solve this.
 
 - **SWT** can use *GTK2* or *GTK3*. If you want to use *GTK2* you must force SWT into *GTK2 mode* via `System.setProperty("SWT_GTK3", "0");` before SWT is initialized and only if there are problems with the autodetection, you can also set `SystemTray.FORCE_GTK2=true;`.
 
 - **AppIndicators** under Ubuntu 16.04 (and possibly other distro's) **will not** work as a different user (ie: as a sudo'd user to `root`), since AppIndicators require a dbus connection to the current user's window manager -- and this cannot happen between different user accounts. We attempt to detect this and fallback to using Swing.
 
 - **MacOSX** is a *special snowflake* in how it handles GUI events, and so there are some bizzaro combinations of SWT, JavaFX, and Swing that do not work together (see the `Notes` below for the details.)
 
  - **MacOSX** *native* menus cannot display images attached to menu entries. If desired, one could override the default for MacOSX so that it uses *Swing* instead of *AWT*, however this will result the SystemTray no-longer supporting the OS theme and transparency. The default of *AWT* was chosen because it looks much, much better than *Swing*. 
 
 - **Gnome3** (Fedora, Manjaro, Arch, etc) environments by default **do not** allow the SystemTray icon to be shown. This has been worked around (it will be placed next to the clock) for most Gnome environments, except for Arch Linux. Another workaround is to install the [Top Icons plugin](https://extensions.gnome.org/extension/1031/topicons/) plugin which moves icons from the *notification drawer* (it is normally collapsed) at the bottom left corner of the screen to the menu panel next to the clock.
 
 - **ToolTips** The maximum length is 64 characters long, and it is not supported on all Operating Systems and Desktop Environments. Specifically, Swing and GtkStatusIcon types support tray tooltips and menu tooltips. AWT and AppIndicator types do not support tooltips of any kind. Please note that **Ubuntu** uses AppIndicators!
                     
 - **Linux/Unix Menus** Some Linux environments only support right-click to display the menu, and it is not possible to change the behavior.
 
 - **Linux/Unix and java.awt.Desktop.getDesktop()** Please use the `dorkbox.util.Desktop` class as a replacement, which will 
 intelligently call the correct OS API to open a folder/directory, email, or browser. (*Many thanks to QZ Tray for this*).

AutoDetect Compatibility List
------------------
     
OS | Supported 
--- | --- | 
Arch Linux + Gnome3 | ✓ |
 | |
ChromeOS | - |
 | |
Debian 8.5 + Gnome3 | ✓ |
Debian 8.6 + Gnome3 | ✓ |
 | | 
Elementary OS 0.3.2 | ✓ |
Elementary OS 0.4 | ✓ |
 | |
Fedora 23 | ✓ | 
Fedora 24 | ✓ | 
Fedora 25 | ✓ | 
Fedora 25 KDE | ✓ |
 | |
FreeBSD 11 + Gnome3 | ✓ |
 | |
Kali 2016 | ✓ | 
Kali 2017 | ✓ | 
 | |
LinuxMint 18 | ✓ |
 | | 
Ubuntu 12.04 | ✓ | 
Ubuntu 14.04 | ✓ | 
Ubuntu 16.04 | ✓ | 
Ubuntu 17.04 | ✓ | 
UbuntuGnome 16.04 | ✓ | 
UbuntuGnome 17.04 | ✓ |
 | | 
XUbuntu 16.04 | ✓ |
 | | 
MacOSx  | ✓ | 
 | |
Windows XP  | ✓ | 
Windows 7   | ✓ | 
Windows 8.1 | ✓ | 
Windows 10  | ✓ | 

Notes:
-------
 - The compatibility list only applies while the SystemTray is in `AutoDetect` mode. Not all OSes support forcing a custom tray type.
 
 - The menu item callbacks occur on **their own dispatch thread** (instead of being on whatever OS's event dispatch thread), in order to 
    provide consistent actions across all platforms. It is critical to make sure that access to Swing/etc that depend on running events
    inside their own EDT, are properly called. IE: `SwingUtilities.invokeLater()`. Do not use `invokeAndWait()` as weird GUI anomalies 
    can happen.
 
 - Ubuntu 16.04+ with JavaFX require `libappindicator1` because of JavaFX GTK and indicator panel incompatibilities. See [more details](https://github.com/dorkbox/SystemTray/issues/14#issuecomment-248853532). We attempt to fallback to using Swing in this situation.  

 - Ubuntu 17.04+ Java only supports the X11 backend. MIR and Wayland are not supported.

 - Debian + GNOME 3, SystemTray works, but will only show in a tray via pressing SUPER+M.  

 - MacOSX JavaFX (Java7) is incompatible with the SystemTray by default. See [issue details](https://bugs.openjdk.java.net/browse/JDK-8116017).
     - To fix this do one of the following
        - Upgrade to Java 8
        - Add : `-Djavafx.macosx.embedded=true` as a JVM parameter
        - Set the system property via `System.setProperty("javafx.macosx.embedded", "true");`  before JavaFX is initialized, used, or accessed. *NOTE*: You may need to change the class (that your main method is in) so it does NOT extend the JavaFX `Application` class.

  - SWT builds for FreeBSD do not exist.
  
  - Linux/Unix: If you want to run this library as a different user, you will need to launch your application via `sudo su username /bin/sh -c "DBUS_SESSION_BUS_ADDRESS='unix:abstract=/tmp/dbus-cLtEoBPmgC' XDG_CURRENT_DESKTOP=$XDG_CURRENT_DESKTOP program-name"`, where `unix:abstract=/tmp/dbus-cLtEoBPmgC` from `/run/user/{uid}/dbus-session`. You will also want to disable the root check + warnings via `SystemTray.ENABLE_ROOT_CHECK=false;` See [issue](https://github.com/dorkbox/SystemTray/issues/63) for more details.
  
  
&nbsp;  
&nbsp;  

```
Customization parameters:

SystemTray.AUTO_SIZE   (type boolean, default value 'true')
 - Enables auto-detection for the system tray. This should be mostly successful.
   
 
SystemTray.FORCE_GTK2    (type boolean, default value 'false')
 - Forces the system tray to always choose GTK2 (even when GTK3 might be available).
 
 
SystemTray.PREFER_GTK3    (type boolean, default value 'true') 
 - Prefer to load GTK3 before trying to load GTK2.
    

SystemTray.FORCE_TRAY_TYPE   (type SystemTray.TrayType, default value 'AutoDetect')
 - Forces the system tray detection to be AutoDetect, GtkStatusIcon, AppIndicator, Swing, or AWT.
   This is an advanced feature, and it is recommended to leave it at AutoDetect.

 
SystemTray.ENABLE_SHUTDOWN_HOOK    (type boolean, default value 'true')
 -  When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also 
    closed.  Additionally, when using the Swing tray type, Windows does not always remove the tray icon if the JVM is stopped, 
    and this makes sure that the tray is also removed from the notification area. 
    This property is available to disable this functionality in situations where you do not want this to happen.
    This is an advanced feature, and it is recommended to leave as true.
 
 
SystemTray.AUTO_FIX_INCONSISTENCIES    (type boolean, default value 'true')
 -  Allows the SystemTray logic to resolve various OS inconsistencies for the SystemTray in different combinations

 
SystemTray.ENABLE_ROOT_CHECK    (type boolean, default value 'true')
 -  Allows the SystemTray logic to ignore if root is detected. Usually when running as root it will not work (because of 
    how DBUS operates), but in rare situations, it might work.
    This is an advanced feature, and it is recommended to leave as true 
 
 
SystemTray.SWING_UI    (type SwingUIFactory, default value 'null')
 - Allows the developer to provide a custom look and feel for the Swing UI, if defined. See the test example for specific use.
      
 
SystemTray.DEBUG    (type boolean, default value 'false')
 -  This property is provided for debugging any errors in the logic used to determine the system-tray type and initialization feedback.
  
  
Extension.ENABLE_EXTENSION_INSTALL    (type boolean, default value 'true')
 - Permit the StatusTray icon to be displayed next to the clock by installing an extension. By default, gnome 
   places the icon in the "notification drawer", which is a collapsible menu (usually) at the bottom left corner 
   of the screen.  This should be set to false if you want to preserve the default Desktop Environment UI preferences.
   Additionally, Arch Linux is the only exception to this rule where it does not install the extension, so TopIcons is
   necessary for placing the icon near the clock.
  
  
Extension.ENABLE_SHELL_RESTART    (type boolean, default value 'true')
 - Permit the gnome-shell to be restarted when the extension is installed.
  
  
Extension.SHELL_RESTART_COMMAND    (type String, default value 'gnome-shell --replace &')
 - Command to restart the gnome-shell. It is recommended to start it in the background (hence '&')
  
  
```
   
   
   
The test application is [on GitHub](https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java), and a *simple* example is as follows:
```
    SystemTray.SWING_UI = new CustomSwingUI();

    SystemTray systemTray = SystemTray.get();
    if (systemTray == null) {
        throw new RuntimeException("Unable to load SystemTray!");
    }
 
    try {
        systemTray.setImage("grey_icon.png");
    } catch (IOException e) {
        e.printStackTrace();
    }
 
    systemTray.setStatus("Not Running");
    
    
    systemTray.getMenu().add(new MenuItem("Quit", new ActionListener() {
        @Override
        public
        void actionPerformed(final ActionEvent e) {
            systemTray.shutdown();
            //System.exit(0);  not necessary if all non-daemon threads have stopped.
        }
    })).setShortcut('q'); // case does not matter
```
&nbsp;  
&nbsp;  

``` 
Note: This project was heavily influenced by the excellent Lantern project (when it was Java based),
      *Many* thanks to them for figuring out AppIndicators via JNA.
      https://github.com/getlantern/lantern
```
```
Note: Gnome-shell users can install an extension to support placing the tray icon next to all 
      of other OS tray icons. By default, all tray icons go to a "Notification drawer" which 
      is initially hidden. 
````
````
Note: We have fixed the Swing notification tray on Linux (it no longer has a greyish background), however
      to facilitate this, a screen-shot is grabbed where the icon is. Because this must happen after the
      icon is placed, *sometimes* you can see this happen. Unfortunately this is the only way to fix
      this problem, and there are no other known workarounds outside of writing an X11 wrapper from scratch.
````
&nbsp;  
&nbsp;  
````      
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
  
````
&nbsp; 
&nbsp; 

Release Notes 
---------

This project includes some utility classes that are a small subset of a much larger library. These classes are **kept in sync** with the main utilities library, so "jar hell" is not an issue, and the latest release will always include the same version of utility files as all of the other projects in the dorkbox repository at that time. 
  
  Please note that the utility source code is included in the release and on our [Git Server](https://git.dorkbox.com/dorkbox/Utilities) repository.
  
  
Maven Info
---------
````
<dependencies>
    ...
    <dependency>
      <groupId>com.dorkbox</groupId>
      <artifactId>SystemTray</artifactId>
      <version>3.14</version>
    </dependency>
</dependencies>
````

  
Gradle Info
---------
````
dependencies {
    ...
    compile 'com.dorkbox:SystemTray:3.14'
}
````




Or if you don't want to use Maven, you can access the latest files and source-code directly from here:  
https://github.com/dorkbox/SystemTray/releases  

https://oss.sonatype.org/content/repositories/releases/com/dorkbox/SystemTray/  
https://oss.sonatype.org/content/repositories/releases/com/dorkbox/ShellExecutor/    



https://repo1.maven.org/maven2/net/java/dev/jna/jna/  
https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/  
https://repo1.maven.org/maven2/org/slf4j/slf4j-api/  
https://repo1.maven.org/maven2/org/javassist/javassist/  


License
---------
This project is © 2014 dorkbox llc, and is distributed under the terms of the Apache v2.0 License. See file "LICENSE" for further references.

