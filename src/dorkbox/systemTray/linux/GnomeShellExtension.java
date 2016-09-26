/*
 * Copyright 2015 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.systemTray.linux;

import static dorkbox.systemTray.SystemTray.logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.IO;
import dorkbox.util.Property;
import dorkbox.util.process.ShellProcessBuilder;

@SuppressWarnings({"DanglingJavadoc", "WeakerAccess"})
public
class GnomeShellExtension {
    private static final String UID = "SystemTray@Dorkbox";

    @Property
    /** Permit the gnome-shell to be restarted when the extension is installed. */
    public static boolean ENABLE_SHELL_RESTART = true;

    @Property
    /** Command to restart the gnome-shell. It is recommended to start it in the background (hence '&') */
    public static String SHELL_RESTART_COMMAND = "gnome-shell --replace &";

    public static void install(final String shellVersionString) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

        // gsettings get org.gnome.shell enabled-extensions
        final ShellProcessBuilder gsettings = new ShellProcessBuilder(outputStream);
        gsettings.setExecutable("gsettings");
        gsettings.addArgument("get");
        gsettings.addArgument("org.gnome.shell");
        gsettings.addArgument("enabled-extensions");
        gsettings.start();

        String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);

        boolean hasTopIcons = output.contains("topIcons@adel.gadllah@gmail.com");
        boolean hasSystemTray = output.contains(UID);

        if (hasTopIcons) {
            // topIcons will convert ALL icons to be at the top of the screen, so there is no reason to have both installed
            return;
        }

        // have to copy the extension over and enable it.
        String userHome = System.getProperty("user.home");

        // where the extension is saved
        final File file = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        final File metaDatafile = new File(file, "metadata.json");
        final File extensionFile = new File(file, "extension.js");


        // have to create the metadata.json file (and make it so that it's **always** current).
        // we do this via getting the shell version

        // GNOME Shell 3.14.1
        String versionOutput = shellVersionString.replaceAll("[^\\d.]", ""); // should just be 3.14.1 or 3.20 or similar

        // We want "3.14" or "3.20" or whatever the latest version is (excluding the patch version info).
        final int indexOf = versionOutput.indexOf('.');
        final int nextIndexOf = versionOutput.indexOf('.', indexOf + 1);
        if (indexOf < nextIndexOf) {
            versionOutput = versionOutput.substring(0, nextIndexOf);  // will be 3.14 (without the trailing '.1'), for example
        }

        String metadata = "{\n" +
                          "  \"description\": \"Shows a java tray icon on the top notification tray\",\n" +
                          "  \"name\": \"Dorkbox SystemTray\",\n" +
                          "  \"shell-version\": [\n" +
                          "    \"" + versionOutput + "\"\n" +
                          "  ],\n" +
                          "  \"url\": \"https://github.com/dorkbox/SystemTray\",\n" +
                          "  \"uuid\": \"" + UID + "\",\n" +
                          "  \"version\": " + SystemTray.getVersion() + "\n" +
                          "}\n";


        if (hasSystemTray) {
            if (SystemTray.DEBUG) {
                logger.debug("Checking current version of extension for upgrade");
            }
            // have to check to see if the version is correct as well (otherwise we have to reinstall it)

            StringBuilder builder = new StringBuilder(256);
            BufferedReader bin = null;
            try {
                bin = new BufferedReader(new FileReader(metaDatafile));
                String line;
                while ((line = bin.readLine()) != null) {
                    builder.append(line)
                           .append("\n");
                }
            } finally {
                IO.close(bin, logger);
            }


            // the metadata string we CHECK should equal the metadata string we PROVIDE
            if (metadata.equals(builder.toString())) {
                // this means that our version info, etc. is the same - there is no need to update anything
                if (!SystemTray.DEBUG) {
                    // if we are DEBUG, then we ALWAYS want to copy over our extension. We will have to manually restart the shell to see it
                    return;
                }
            } else {
                // this means that we need to reinstall our extension, since either GNOME or US have changed versions since
                // we last installed the extension.
                logger.debug("Need to upgrade extension");
            }
        }

        // we get here if we are installed and our metadata is the same

        // need to make the extension location
        if (!file.isDirectory()) {
            final boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                final String msg = "Unable to create extension location: " + file;
                logger.error(msg);
                throw new RuntimeException(msg);
            }
        }

        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new FileWriter(metaDatafile, false));
            // FileWriter always assumes default encoding is OK
            outputWriter.write(metadata);
            outputWriter.flush();
            outputWriter.close();
        } finally {
            IO.close(outputWriter, logger);
        }

        // copies our provided extension.js file to the correct location on disk
        InputStream reader = null;
        FileOutputStream fileOutputStream = null;
        try {
            reader = GnomeShellExtension.class.getResourceAsStream("extension.js");
            fileOutputStream = new FileOutputStream(extensionFile);

            IO.copyStream(reader, fileOutputStream);
        } finally {
            IO.close(reader, logger);
            IO.close(fileOutputStream, logger);
        }


        if (!hasSystemTray) {
            logger.debug("Enabling extension in gnome-shell");
            // now we have to enable us if we aren't already enabled

            // gsettings get org.gnome.shell enabled-extensions
            // defaults are:
            //  - fedora 23:   ['background-logo@fedorahosted.org']  on
            //  - openSuse:
            //  - Ubuntu Gnome 16.04:   @as []
            final StringBuilder stringBuilder = new StringBuilder(output);

            // have to remove the end first, otherwise we would have to re-index the location of the ]

            // remove the last ]
            int extensionIndex = output.indexOf("]");
            if (extensionIndex > 0) {
                stringBuilder.delete(extensionIndex, stringBuilder.length());
            }

            // strip off UP-TO (but not) the leading  [
            extensionIndex = output.indexOf("[");
            if (extensionIndex > 0) {
                stringBuilder.delete(0, extensionIndex);
            }

            // should be   ['background-logo@fedorahosted.org', 'zyx', 'abs'
            // or will be  [  (if there is nothing)
            logger.info("Installed extensions (should have leading '[') are: {}", stringBuilder.toString());

            // add our extension to the list
            if (stringBuilder.length() > 2) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("'")
                         .append(UID)
                         .append("'");


            stringBuilder.append("]");

            // gsettings set org.gnome.shell enabled-extensions "['SystemTray@dorkbox']"
            // gsettings set org.gnome.shell enabled-extensions "['background-logo@fedorahosted.org', 'SystemTray@dorkbox']"
            final ShellProcessBuilder setGsettings = new ShellProcessBuilder(outputStream);
            setGsettings.setExecutable("gsettings");
            setGsettings.addArgument("set");
            setGsettings.addArgument("org.gnome.shell");
            setGsettings.addArgument("enabled-extensions");
            setGsettings.addArgument(stringBuilder.toString());
            setGsettings.start();
        }

        if (ENABLE_SHELL_RESTART) {
            if (SystemTray.DEBUG) {
                logger.debug("DEBUG mode enabled. You need to manually restart the shell via '{}'", SHELL_RESTART_COMMAND);
                return;
            }


            logger.info("Restarting gnome-shell so tray notification changes can be applied.");

            // now we have to restart the gnome shell via bash
            final ShellProcessBuilder restartShell = new ShellProcessBuilder();
            // restart shell in background process
            restartShell.addArgument(SHELL_RESTART_COMMAND);
            restartShell.start();

            // We don't care when the shell restarts, since WHEN IT DOES restart, our extension will show our icon.
        }
    }
}
