module dorkbox.systemtray {
    exports dorkbox.systemTray;
    exports dorkbox.systemTray.peer;
    exports dorkbox.systemTray.util;

    requires dorkbox.executor;
    requires dorkbox.updates;
    requires dorkbox.utilities;

    requires org.slf4j;

    requires com.sun.jna;
    requires com.sun.jna.platform;

    // when running javaFX
    // requires static javafx.graphics;

    // when running SWT
    // 32-bit support was dropped by eclipse since 4.10 (3.108.0 is the oldest that is 32 bit)
    // requires static org.eclipse.swt.gtk.linux.x86_64;
    // requires static org.eclipse.swt.win32.win32.x86_64;
    // requires static org.eclipse.swt.cocoa.macosx.x86_64;

    requires java.desktop;
    requires java.base;
    requires kotlin.stdlib;
}
