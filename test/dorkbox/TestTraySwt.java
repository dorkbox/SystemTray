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

import java.net.URL;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import dorkbox.systemTray.Action;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.SystemTray;

/**
 * Icons from 'SJJB Icons', public domain/CC0 icon set
 *
 * Needs SWT to run
 */
public
class TestTraySwt {

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
        System.setProperty("SWT_GTK3", "0"); // Can change between GTK2 and GTK3

        new TestTraySwt();
    }

    private SystemTray systemTray;
    private Action callbackGreen;
    private Action callbackGray;

    public
    TestTraySwt() {
        final Display display = new Display ();
        final Shell shell = new Shell(display);

        Text helloWorldTest = new Text(shell, SWT.NONE);
        helloWorldTest.setText("Hello World SWT  .................  ");
        helloWorldTest.pack();


        this.systemTray = SystemTray.get();
        if (systemTray == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        systemTray.setImage(LT_GRAY_TRAIN);
        systemTray.setStatus("No Mail");

        callbackGreen = new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                systemTray.setStatus("Some Mail!");
                systemTray.setImage(GREEN_TRAIN);

                entry.setCallback(callbackGray);
                entry.setImage(BLACK_MAIL);
                entry.setText("Delete Mail");
//                systemTray.remove(menuEntry);
            }
        };

        callbackGray = new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                systemTray.setStatus(null);
                systemTray.setImage(BLACK_TRAIN);

                entry.setCallback(null);
//                systemTray.setStatus("Mail Empty");
                systemTray.remove(entry);
                System.err.println("POW");
            }
        };

        Entry menuEntry = this.systemTray.addEntry("Green Mail", GREEN_MAIL, callbackGreen);
        // case does not matter
        menuEntry.setShortcut('G');

        this.systemTray.addSeparator();

        final Menu submenu = this.systemTray.addMenu("Options", BLUE_CAMPING);
        submenu.setShortcut('t');
        submenu.addEntry("Disable menu", BLACK_BUS, new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                submenu.setEnabled(false);
            }
        });
// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
//        submenu.addEntry("Add widget", GREEN_BUS, new Action() {
//            @Override
//            public
//            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
//                JProgressBar progressBar = new JProgressBar(0, 100);
//                progressBar.setValue(new Random().nextInt(101));
//                progressBar.setStringPainted(true);
//                systemTray.addWidget(progressBar);
//            }
//        });
        submenu.addEntry("Hide tray", LT_GRAY_BUS, new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                systemTray.setEnabled(false);
            }
        });
        submenu.addEntry("Remove menu", BLACK_FIRE, new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                submenu.remove();
            }
        });

        systemTray.addEntry("Quit", new Action() {
            @Override
            public
            void onClick(final SystemTray systemTray, final Menu parent, final Entry entry) {
                systemTray.shutdown();

                display.asyncExec(new Runnable() {
                    public void run() {
                        shell.dispose();
                    }
                });

                //System.exit(0);  not necessary if all non-daemon threads have stopped.
            }
        }).setShortcut('q'); // case does not matter



        shell.pack();
        shell.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        display.dispose();
    }
}
