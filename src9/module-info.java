module dorkbox.systemtray {
    exports dorkbox.systemTray;
    exports dorkbox.systemTray.peer;
    exports dorkbox.systemTray.util;

    requires transitive dorkbox.collections;
    requires transitive dorkbox.executor;
    requires transitive dorkbox.desktop;
    requires transitive dorkbox.jna;
    requires transitive dorkbox.updates;
    requires transitive dorkbox.utilities;
    requires transitive dorkbox.os;

    requires transitive org.javassist; // this is an automatic module name, and emits warnings when compiling. This is OK.
    requires transitive org.slf4j;

    requires transitive com.sun.jna;
    requires transitive com.sun.jna.platform;

    requires transitive kotlin.stdlib;
}
