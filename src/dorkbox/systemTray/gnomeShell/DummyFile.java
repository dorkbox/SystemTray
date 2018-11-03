package dorkbox.systemTray.gnomeShell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import dorkbox.systemTray.SystemTray;

public
class DummyFile {

    private static final File file = new File(System.getProperty("user.home") + "/.local/.SystemTray");

    /**
     * Installs a dummy file to indicate we've restarted the shell. This will restart the shell if necessary
     */
    public static
    void install() {
        if (!file.exists()) {
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("Creating marker file");
            }

            try {
                new FileOutputStream(file).close();
            } catch (IOException e) {
                SystemTray.logger.error("Error creating file", e);
            }

            file.setLastModified(System.currentTimeMillis());

            // just restarting the shell is enough to get the system tray to work
            LegacyExtension.restartShell();
        }

    }
}
