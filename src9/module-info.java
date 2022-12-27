module dorkbox.systemtray {
    exports dorkbox.systemTray;
    exports dorkbox.systemTray.peer;
    exports dorkbox.systemTray.util;

    requires transitive dorkbox.collections;
    requires transitive dorkbox.executor;
    requires transitive dorkbox.updates;
    requires transitive dorkbox.utilities;
    requires transitive dorkbox.os;

    requires transitive org.slf4j;

    requires transitive com.sun.jna;
    requires transitive com.sun.jna.platform;

    requires transitive java.desktop;
    requires kotlin.stdlib;

    requires java.base;
    requires org.javassist;
}
