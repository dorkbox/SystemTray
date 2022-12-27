module dorkbox.systemtray {
    exports dorkbox.systemTray;
    exports dorkbox.systemTray.peer;
    exports dorkbox.systemTray.util;

    requires transitive dorkbox.executor;
    requires transitive dorkbox.updates;
    requires transitive dorkbox.utilities;
    requires transitive dorkbox.os;

    requires transitive org.slf4j;

    requires transitive com.sun.jna;
    requires transitive com.sun.jna.platform;

    // when running javaFX
    // requires static javafx.graphics;

    // when running SWT
    // 32-bit support was dropped by eclipse since 4.10 (3.108.0 is the oldest that is 32 bit)
    // requires static org.eclipse.swt.gtk.linux.x86_64;
    // requires static org.eclipse.swt.win32.win32.x86_64;
    // requires static org.eclipse.swt.cocoa.macosx.x86_64;

    requires transitive java.desktop;
    requires kotlin.stdlib;

    requires java.base;
}
