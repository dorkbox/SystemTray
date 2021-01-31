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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.Random;

import dorkbox.os.OS;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.CacheUtil;
import dorkbox.util.Desktop;

/**
 * Icons from 'SJJB Icons', public domain/CC0 icon set
 */
public
class TestTray {

    public static final URL BLUE_CAMPING = TestTray.class.getResource("accommodation_camping.glow.0092DA.32.png");
    public static final URL BLACK_FIRE = TestTray.class.getResource("amenity_firestation.p.000000.32.png");

    public static final URL BLACK_MAIL = TestTray.class.getResource("amenity_post_box.p.000000.32.png");
    public static final URL GREEN_MAIL = TestTray.class.getResource("amenity_post_box.p.39AC39.32.png");

    public static final URL BLACK_BUS = TestTray.class.getResource("transport_bus_station.p.000000.32.png");
    public static final URL LT_GRAY_BUS = TestTray.class.getResource("transport_bus_station.p.999999.32.png");

    public static final URL BLACK_TRAIN = TestTray.class.getResource("transport_train_station.p.000000.32.png");
    public static final URL GREEN_TRAIN = TestTray.class.getResource("transport_train_station.p.39AC39.32.png");
    public static final URL LT_GRAY_TRAIN = TestTray.class.getResource("transport_train_station.p.666666.32.png");

    public static
    void main(String[] args) {
        // make sure JNA jar is on the classpath!
        new TestTray();
    }

    private SystemTray systemTray;
    private ActionListener callbackGray;

    public
    TestTray() {
        SystemTray.DEBUG = true; // for test apps, we always want to run in debug mode

        // for test apps, make sure the cache is always reset. These are the ones used, and you should never do this in production.
        new CacheUtil("SystemTrayImages").clear();
        new CacheUtil("CheckMarks").clear();

        // SwingUtil.setLookAndFeel(null); // set Native L&F (this is the System L&F instead of CrossPlatform L&F)
        // SystemTray.SWING_UI = new CustomSwingUI();

        this.systemTray = SystemTray.get("SysTrayExample");
        if (systemTray == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        systemTray.setTooltip("Mail Checker");
        systemTray.setImage(LT_GRAY_TRAIN);
        systemTray.setStatus("No Mail");

        callbackGray = new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                final MenuItem entry = (MenuItem) e.getSource();
                systemTray.setStatus(null);
                systemTray.setImage(BLACK_TRAIN);

                entry.setCallback(null);
//                systemTray.setStatus("Mail Empty");
                systemTray.getMenu().remove(entry);
                entry.remove();
                System.err.println("POW");
            }
        };


        Menu mainMenu = systemTray.getMenu();

        MenuItem greenEntry = new MenuItem("Green Mail", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                final MenuItem entry = (MenuItem) e.getSource();
                systemTray.setStatus("Some Mail!");
                systemTray.setImage(GREEN_TRAIN);

                entry.setCallback(callbackGray);
                entry.setImage(BLACK_MAIL);
                entry.setText("Delete Mail");
                entry.setTooltip(null); // remove the tooltip
//                systemTray.remove(menuEntry);
            }
        });
        greenEntry.setImage(GREEN_MAIL);
        // case does not matter
        greenEntry.setShortcut('G');
        greenEntry.setTooltip("This means you have green mail!");
        mainMenu.add(greenEntry);


        Checkbox checkbox = new Checkbox("Euro € Mail", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                System.err.println("Am i checked? " + ((Checkbox) e.getSource()).getChecked());
            }
        });
        checkbox.setShortcut('€');
        mainMenu.add(checkbox);

        MenuItem removeTest = new MenuItem("This should not be here", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                try {
                    Desktop.browseURL("https://git.dorkbox.com/dorkbox/SystemTray");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        mainMenu.add(removeTest);
        mainMenu.remove(removeTest);

        mainMenu.add(new Separator());

        mainMenu.add(new MenuItem("About", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                try {
                    Desktop.browseURL("https://git.dorkbox.com/dorkbox/SystemTray");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));


        mainMenu.add(new MenuItem("Temp Directory", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                try {
                    Desktop.browseDirectory(OS.TEMP_DIR.getAbsolutePath());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));


        Menu submenu = new Menu("Options", BLUE_CAMPING);
        submenu.setShortcut('t');


        MenuItem disableMenu = new MenuItem("Disable menu", BLACK_BUS, new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                MenuItem source = (MenuItem) e.getSource();
                source.getParent().setEnabled(false);
            }
        });
        submenu.add(disableMenu);


        submenu.add(new MenuItem("Hide tray", LT_GRAY_BUS, new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                systemTray.setEnabled(false);
            }
        }));
        submenu.add(new MenuItem("Remove menu", BLACK_FIRE, new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                MenuItem source = (MenuItem) e.getSource();
                source.getParent().remove();
            }
        }));

        submenu.add(new MenuItem("Add new entry to tray", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                systemTray.getMenu().add(new MenuItem("Random " + Integer.toString(new Random().nextInt(10))));
            }
        }));
        mainMenu.add(submenu);

        MenuItem entry = new MenuItem("Type: " + systemTray.getType().toString());
        entry.setEnabled(false);
        systemTray.getMenu().add(entry);

        systemTray.getMenu().add(new MenuItem("Quit", new ActionListener() {
            @Override
            public
            void actionPerformed(final ActionEvent e) {
                systemTray.shutdown();
                //System.exit(0);  not necessary if all non-daemon threads have stopped.
            }
        })).setShortcut('q'); // case does not matter
    }
}
