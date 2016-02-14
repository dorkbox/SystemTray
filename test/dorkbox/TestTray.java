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

package dorkbox;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;

import java.io.File;
import java.net.URL;

/**
 * Icons from 'SJJB Icons', public domain/CC0 icon set
 */
public
class TestTray {

    // horribly hacky. ONLY FOR TESTING!
    public static final URL BLACK_MAIL = TestTray.class.getResource("mail.000000.24.png");
    public static final URL GREEN_MAIL = TestTray.class.getResource("mail.39AC39.24.png");
    public static final URL LT_GRAY_MAIL = TestTray.class.getResource("mail.999999.24.png");

    public static
    void main(String[] args) {
        // ONLY if manually loading JNA jars.
        //
        // Not necessary if using the official JNA downloaded from https://github.com/twall/jna AND THAT JAR is on the classpath
        //
        System.load(new File("../../resources/Dependencies/jna/linux_64/libjna.so").getAbsolutePath()); //64bit linux library

        new TestTray();
    }

    private SystemTray systemTray;
    private SystemTrayMenuAction callbackGreen;
    private SystemTrayMenuAction callbackGray;

    public
    TestTray() {
        this.systemTray = SystemTray.getSystemTray();
        if (systemTray == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        this.systemTray.setIcon(LT_GRAY_MAIL);
        systemTray.setStatus("No Mail");

        callbackGreen = new SystemTrayMenuAction() {
            @Override
            public
            void onClick(final SystemTray systemTray, final MenuEntry menuEntry) {
                systemTray.setStatus("Some Mail!");
                systemTray.setIcon(GREEN_MAIL);
                menuEntry.setCallback(callbackGray);
                menuEntry.setImage(BLACK_MAIL);
                menuEntry.setText("Delete Mail");
//                systemTray.removeMenuEntry(menuEntry);
            }
        };

        callbackGray = new SystemTrayMenuAction() {
            @Override
            public
            void onClick(final SystemTray systemTray, final MenuEntry menuEntry) {
                systemTray.setStatus(null);
                systemTray.setIcon(BLACK_MAIL);
                menuEntry.setCallback(null);
//                systemTray.setStatus("Mail Empty");
                systemTray.removeMenuEntry(menuEntry);
                System.err.println("POW");
            }
        };

        this.systemTray.addMenuEntry("Green Mail", GREEN_MAIL, callbackGreen);

        systemTray.addMenuEntry("Quit", new SystemTrayMenuAction() {
            @Override
            public
            void onClick(final SystemTray systemTray, final MenuEntry menuEntry) {
                systemTray.shutdown();
                //System.exit(0);  not necessary if all non-daemon threads have stopped.
            }
        });
    }
}
