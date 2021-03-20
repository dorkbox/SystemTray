/*
 * Copyright 2021 dorkbox, llc
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

import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.util.Random;

import dorkbox.javaFx.JavaFx;
import dorkbox.os.OS;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.CacheUtil;
import dorkbox.util.Desktop;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Icons from 'SJJB Icons', public domain/CC0 icon set
 *
 * Needs JavaFX to run. NOTE: JavaFX on Mac (Java7) has many bugs when also used with AWT. This class does NOT extend 'Application'
 * (javafx) class on purpose, so that we can work around those issues
 */
public
class TestTrayJavaFX {

    public static final URL BLUE_CAMPING = TestTrayJavaFX.class.getResource("accommodation_camping.glow.0092DA.32.png");
    public static final URL BLACK_FIRE = TestTrayJavaFX.class.getResource("amenity_firestation.p.000000.32.png");

    public static final URL BLACK_MAIL = TestTrayJavaFX.class.getResource("amenity_post_box.p.000000.32.png");
    public static final URL GREEN_MAIL = TestTrayJavaFX.class.getResource("amenity_post_box.p.39AC39.32.png");

    public static final URL BLACK_BUS = TestTrayJavaFX.class.getResource("transport_bus_station.p.000000.32.png");
    public static final URL LT_GRAY_BUS = TestTrayJavaFX.class.getResource("transport_bus_station.p.999999.32.png");

    public static final URL BLACK_TRAIN = TestTrayJavaFX.class.getResource("transport_train_station.p.000000.32.png");
    public static final URL GREEN_TRAIN = TestTrayJavaFX.class.getResource("transport_train_station.p.39AC39.32.png");
    public static final URL LT_GRAY_TRAIN = TestTrayJavaFX.class.getResource("transport_train_station.p.666666.32.png");

    // from issue 123
    public static final URL NOTIFY_IMAGE = TestTrayJavaFX.class.getResource("RemoteNotifications.png");

    private static TestTrayJavaFX testTrayJavaFX;

    public static
    class MyApplication extends Application {
        public
        MyApplication() {
        }

        @Override
        public
        void start(final Stage stage) {
            if (testTrayJavaFX == null) {
                testTrayJavaFX = new TestTrayJavaFX();
            }

            testTrayJavaFX.doJavaFxStuff(stage);
        }
    }

    public static
    void main(String[] args) {
        testTrayJavaFX = new TestTrayJavaFX();

        // NOTE: make sure JNA jar is on the classpath!
        Application.launch(MyApplication.class);
    }

    private SystemTray systemTray;
    private ActionListener callbackGray;

    public
    TestTrayJavaFX() {

    }

    public
    void doJavaFxStuff(final Stage primaryStage) {
        primaryStage.setTitle("Hello World JavaFx!");
        Button btn = new Button();
        btn.setText("Say 'Hello World JavaFx'");
        btn.setOnAction(event->System.out.println("Hello World JavaFx!"));

        StackPane root = new StackPane();
        root.getChildren().add(btn);
        primaryStage.setScene(new Scene(root, 300, 250));
        primaryStage.show();

        SystemTray.DEBUG = true; // for test apps, we always want to run in debug mode

        // for test apps, make sure the cache is always reset. These are the ones used, and you should never do this in production.
        CacheUtil.clear("SysTrayExample");

        // SwingUtil.setLookAndFeel(null); // set Native L&F (this is the System L&F instead of CrossPlatform L&F)
        // SystemTray.SWING_UI = new CustomSwingUI();

        this.systemTray = SystemTray.get("SysTrayExample");
        if (systemTray == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        systemTray.setTooltip("Mail Checker");
        systemTray.setImage(LT_GRAY_TRAIN);
        systemTray.setStatus("No Mail");

        callbackGray = e->{
            final MenuItem entry = (MenuItem) e.getSource();
            systemTray.setStatus(null);
            systemTray.setImage(BLACK_TRAIN);

            entry.setCallback(null);
//                systemTray.setStatus("Mail Empty");
            systemTray.getMenu().remove(entry);
            System.err.println("POW");
        };


        Menu mainMenu = systemTray.getMenu();

        MenuItem greenEntry = new MenuItem("Green Mail", e->{
            final MenuItem entry = (MenuItem) e.getSource();
            systemTray.setStatus("Some Mail!");
            systemTray.setImage(GREEN_TRAIN);

            entry.setCallback(callbackGray);
            entry.setImage(BLACK_MAIL);
            entry.setText("Delete Mail");
            entry.setTooltip(null); // remove the tooltip
//                systemTray.remove(menuEntry);
        });
        greenEntry.setImage(GREEN_MAIL);
        // case does not matter
        greenEntry.setShortcut('G');
        greenEntry.setTooltip("This means you have green mail!");
        mainMenu.add(greenEntry);


        Checkbox checkbox = new Checkbox("Euro € Mail", e->System.err.println("Am i checked? " + ((Checkbox) e.getSource()).getChecked()));
        checkbox.setShortcut('€');
        mainMenu.add(checkbox);

        MenuItem removeTest = new MenuItem("This should not be here", e->{
            try {
                Desktop.browseURL("https://git.dorkbox.com/dorkbox/SystemTray");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });
        mainMenu.add(removeTest);
        mainMenu.remove(removeTest);

        mainMenu.add(new Separator());

        mainMenu.add(new MenuItem("About", e->{
            try {
                Desktop.browseURL("https://git.dorkbox.com/dorkbox/SystemTray");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }));

        mainMenu.add(new MenuItem("Temp Directory", e->{
            try {
                Desktop.browseDirectory(OS.TEMP_DIR.getAbsolutePath());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }));

        mainMenu.add(new MenuItem("Notify", e->{
            final MenuItem entry = (MenuItem) e.getSource();
            systemTray.setStatus("Notification!");
            systemTray.setImage(NOTIFY_IMAGE);

            entry.setImage(NOTIFY_IMAGE);
            entry.setText("Did notify");
            System.err.println("NOTIFICATION!");
        }));

        Menu submenu = new Menu("Options", BLUE_CAMPING);
        submenu.setShortcut('t');


        MenuItem disableMenu = new MenuItem("Disable menu", BLACK_BUS, e->{
            MenuItem source = (MenuItem) e.getSource();
            source.getParent().setEnabled(false);
        });
        submenu.add(disableMenu);


        submenu.add(new MenuItem("Hide tray", LT_GRAY_BUS, e->systemTray.setEnabled(false)));
        submenu.add(new MenuItem("Remove menu", BLACK_FIRE, e->{
            MenuItem source = (MenuItem) e.getSource();
            source.getParent().remove();
        }));
        submenu.add(new MenuItem("Add new entry to tray",
                                 e->systemTray.getMenu().add(new MenuItem("Random " + new Random().nextInt(10)))));
        mainMenu.add(submenu);

        MenuItem entry = new MenuItem("Type: " + systemTray.getType().toString());
        entry.setEnabled(false);
        systemTray.getMenu().add(entry);

        systemTray.getMenu().add(new MenuItem("Quit", e->{
            systemTray.shutdown();

            if (!JavaFx.isEventThread()) {
                JavaFx.dispatch(()->{
                    primaryStage.hide(); // must do this BEFORE Platform.exit() otherwise odd errors show up
                    Platform.exit();  // necessary to close javaFx
                });
            } else {
                primaryStage.hide(); // must do this BEFORE Platform.exit() otherwise odd errors show up
                Platform.exit();  // necessary to close javaFx
            }

            //System.exit(0);  not necessary if all non-daemon threads have stopped.
        })).setShortcut('q'); // case does not matter
    }
}
