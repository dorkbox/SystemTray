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
package dorkbox.systemTray.gnomeShell;

import static dorkbox.systemTray.SystemTray.logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.IO;
import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.process.ShellProcessBuilder;

@SuppressWarnings({"DanglingJavadoc", "WeakerAccess"})
public
class Extension {
    private static final String UID = "SystemTray@Dorkbox";

    @Property
    /** Permit the StatusTray icon to be displayed next to the clock by installing an extension. By default, gnome places the icon in the
     * "notification drawer", which is a collapsible menu at (usually) bottom left corner of the screen.
     */
    public static boolean ENABLE_EXTENSION_INSTALL = true;

    @Property
    /** Permit the gnome-shell to be restarted when the extension is installed. */
    public static boolean ENABLE_SHELL_RESTART = true;

    @Property
    /** Command to restart the gnome-shell. It is recommended to start it in the background (hence '&') */
    public static String SHELL_RESTART_COMMAND = "gnome-shell --replace &";

    public static
    List<String> getEnabledExtensions() {
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
//        String output = "'background-logo@fedorahosted.org', 'zyx', 'abs'";

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

        // strip off UP-TO plus the leading  [
        extensionIndex = output.indexOf("[");
        if (extensionIndex >= 0) {
            stringBuilder.delete(0, extensionIndex+1);
        }

        // should be   'background-logo@fedorahosted.org', 'zyx', 'abs'
        // or nothing

        String installedExtensions = stringBuilder.toString();

        if (SystemTray.DEBUG) {
            logger.debug("Installed extensions are: {}", installedExtensions);
        }

        // now just split the extensions into a list so it is easier to manage

        String[] split = installedExtensions
                                      .split(", ");
        for (int i = 0; i < split.length; i++) {
            final String s = split[i];

            int i1 = s.indexOf("'");
            int i2 = s.lastIndexOf("'");

            if (i1 == 0 && i2 == s.length() - 1) {
                split[i] = s.substring(1, s.length() - 1);
            }
        }

        ArrayList<String> strings = new ArrayList<String>(Arrays.asList(split));
        for (Iterator<String> iterator = strings.iterator(); iterator.hasNext(); ) {
            final String string = iterator.next();
            if (string.trim()
                      .isEmpty()) {
                iterator.remove();
            }
        }

        return strings;
    }

    public static
    void setEnabledExtensions(List<String> extensions) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

        StringBuilder stringBuilder = new StringBuilder("[");

        for (int i = 0, extensionsSize = extensions.size(), limit = extensionsSize-1; i < extensionsSize; i++) {
            final String extension = extensions.get(i);
            if (extension.isEmpty()) {
                continue;
            }

            stringBuilder.append("'")
                         .append(extension)
                         .append("'");

            if (i < limit) {
                stringBuilder.append(",");
            }
        }
        stringBuilder.append("]");


        if (SystemTray.DEBUG) {
            logger.debug("Setting installed extensions to: {}", stringBuilder.toString());
        }

        // gsettings set org.gnome.shell enabled-extensions "['SystemTray@dorkbox']"
        // gsettings set org.gnome.shell enabled-extensions "['background-logo@fedorahosted.org']"
        // gsettings set org.gnome.shell enabled-extensions "['background-logo@fedorahosted.org', 'SystemTray@dorkbox']"
        final ShellProcessBuilder setGsettings = new ShellProcessBuilder(outputStream);
        setGsettings.setExecutable("gsettings");
        setGsettings.addArgument("set");
        setGsettings.addArgument("org.gnome.shell");
        setGsettings.addArgument("enabled-extensions");
        setGsettings.addArgument(stringBuilder.toString());
        setGsettings.start();
    }

    private static
    void restartShell() {
        if (ENABLE_SHELL_RESTART) {
            if (SystemTray.DEBUG) {
                logger.debug("DEBUG mode enabled. You need to manually restart the shell via '{}'", SHELL_RESTART_COMMAND);
                return;
            }

            if (SystemTray.DEBUG) {
                logger.debug("Restarting gnome-shell so tray notification changes can be applied.");
            }

            // now we have to restart the gnome shell via bash
            final ShellProcessBuilder restartShell = new ShellProcessBuilder();
            // restart shell in background process
            restartShell.addArgument(SHELL_RESTART_COMMAND);
            restartShell.start();

            // We don't care when the shell restarts, since WHEN IT DOES restart, our extension will show our icon.
        }
    }


    /**
     * topIcons will convert ALL icons to be at the top of the screen, so there is no reason to have both installed
     *
     * @return true if that extension is installed
     */
    public static
    boolean isTopIconsInstalled() {
        List<String> enabledExtensions = getEnabledExtensions();
        return enabledExtensions.contains("topIcons@adel.gadllah@gmail.com");
    }


    /**
     * Only install a version that specifically moves only our icon next to the clock
     */
    public static
    void install() {
        if (!ENABLE_EXTENSION_INSTALL || !OS.isGnome()) {
            return;
        }

        boolean hasTopIcons;
        boolean hasSystemTray;

        // should just be 3.14.1 or 3.20 or similar
        String gnomeVersion = OS.getGnomeVersion();
        if (gnomeVersion == null) {
            return;
        }

        List<String> enabledExtensions = getEnabledExtensions();
        hasTopIcons = enabledExtensions.contains("topIcons@adel.gadllah@gmail.com");
        hasSystemTray = enabledExtensions.contains(UID);

        if (hasTopIcons) {
            // topIcons will convert ALL icons to be at the top of the screen, so there is no reason to have both installed
            return;
        }

        // set a property so that GTK (if necessary) can set the name
        System.setProperty("SystemTray_GTK_SET_NAME", "true");

        // have to copy the extension over and enable it.
        String userHome = System.getProperty("user.home");

        // where the extension is saved
        final File file = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        final File metaDatafile = new File(file, "metadata.json");
        final File extensionFile = new File(file, "extension.js");


        // have to create the metadata.json file (and make it so that it's **always** current).
        // we do this via getting the shell version

        // We want "3.14" or "3.20" or whatever the latest version is (excluding the patch version info).
        final int indexOf = gnomeVersion.indexOf('.');
        final int nextIndexOf = gnomeVersion.indexOf('.', indexOf + 1);
        if (indexOf < nextIndexOf) {
            gnomeVersion = gnomeVersion.substring(0, nextIndexOf);  // will be 3.14 (without the trailing '.1'), for example
        }

        String metadata = "{\n" +
                          "  \"description\": \"Moves the java SystemTray icon from inside the notification drawer to alongside the " +
                                                "clock.\",\n" +
                          "  \"name\": \"Dorkbox SystemTray\",\n" +
                          "  \"shell-version\": [\n" +
                          "    \"" + gnomeVersion + "\"\n" +
                          "  ],\n" +
                          "  \"url\": \"https://github.com/dorkbox/SystemTray\",\n" +
                          "  \"uuid\": \"" + UID + "\",\n" +
                          "  \"version\": " + SystemTray.getVersion() + "\n" +
                          "}\n";


        logger.debug("Checking the gnome-shell extension");

        if (hasSystemTray) {
            if (SystemTray.DEBUG) {
                logger.debug("Checking current version of extension for upgrade");
            }
            // have to check to see if the version is correct as well (otherwise we have to reinstall it)
            // compat for java 1.6

            StringBuilder builder = new StringBuilder(256);
            BufferedReader bin = null;
            try {
                bin = new BufferedReader(new FileReader(metaDatafile));
                String line;
                while ((line = bin.readLine()) != null) {
                    builder.append(line)
                           .append("\n");
                }
            } catch (FileNotFoundException ignored) {
            } catch (IOException ignored) {
            } finally {
                IO.close(bin, logger);
            }


            // the metadata string we CHECK should equal the metadata string we PROVIDE
            if (metadata.equals(builder.toString())) {
                // this means that our version info, etc. is the same - there is no need to update anything
                if (!SystemTray.DEBUG) {
                    return;
                } else {
                    // if we are DEBUG, then we ALWAYS want to copy over our extension. We will have to manually restart the shell to see it
                    logger.debug("Always upgrading extension in DEBUG mode");
                    hasSystemTray = false;
                }
            }
            else {
                // this means that we need to reinstall our extension, since either GNOME or US have changed versions since
                // we last installed the extension.
                logger.debug("Need to upgrade extension");
            }
        }


        // we get here if we are NOT installed, or if we are installed and our metadata is NOT THE SAME.  (so we need to reinstall)
        logger.debug("Installing gnome-shell extension");


        // need to make the extension location
        if (!file.isDirectory()) {
            final boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                final String msg = "Unable to create extension location: " + file;
                logger.error(msg);
                return;
            }
        }

        // write out the metadata
        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new FileWriter(metaDatafile, false));
            // FileWriter always assumes default encoding is OK
            outputWriter.write(metadata);
            outputWriter.flush();
            outputWriter.close();
        } catch (IOException e) {
            logger.error("Error installing extension metadata file", e);
        } finally {
            IO.close(outputWriter, logger);
        }



        if (!hasSystemTray) {
            // copies our provided extension.js file to the correct location on disk
            InputStream reader = null;
            FileOutputStream fileOutputStream = null;
            try {
                reader = Extension.class.getResourceAsStream("extension.js");
                fileOutputStream = new FileOutputStream(extensionFile);

                if (reader == null) {
                    logger.error("The GnomeShell extension.js file cannot be found. Something is severely wrong.");
                    return;
                }

                IO.copyStream(reader, fileOutputStream);
            } catch (FileNotFoundException e) {
                logger.error("Cannot find gnome-shell extension", e);
            } catch (IOException e) {
                logger.error("Unable to get gnome-shell extension", e);
            } finally {
                IO.close(reader, logger);
                IO.close(fileOutputStream, logger);
            }

            logger.debug("Enabling extension in gnome-shell");


            enabledExtensions.add(UID);
            setEnabledExtensions(enabledExtensions);

            restartShell();
        }
    }

    public static
    void unInstall() {
        if (!ENABLE_EXTENSION_INSTALL || !OS.isGnome()) {
            return;
        }

        List<String> enabledExtensions = getEnabledExtensions();
        if (enabledExtensions.contains(UID)) {
            enabledExtensions.remove(UID);

            setEnabledExtensions(enabledExtensions);

            restartShell();
        }
    }
}
