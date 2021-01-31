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

import java.net.URL;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.CacheUtil;

/**
 * Icons from 'SJJB Icons', public domain/CC0 icon set
 */
public
class TestMultiTray {

    public static final URL LT_GRAY_TRAIN = TestMultiTray.class.getResource("transport_train_station.p.666666.32.png");
    public static final URL GREEN_TRAIN = TestMultiTray.class.getResource("transport_train_station.p.39AC39.32.png");

    public static
    void main(String[] args) {
        // make sure JNA jar is on the classpath!
        new TestMultiTray();
    }

    private SystemTray systemTray1;
    private SystemTray systemTray2;

    public
    TestMultiTray() {
        SystemTray.DEBUG = true; // for test apps, we always want to run in debug mode

        // for test apps, make sure the cache is always reset. These are the ones used, and you should never do this in production.
        CacheUtil.clear("SysTrayExample1");
        CacheUtil.clear("SysTrayExample2");

        // SwingUtil.setLookAndFeel(null); // set Native L&F (this is the System L&F instead of CrossPlatform L&F)
        // SystemTray.SWING_UI = new CustomSwingUI();

        this.systemTray1 = SystemTray.get("SysTrayExample1");
        if (systemTray1 == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        systemTray1.setTooltip("Mail Checker");
        systemTray1.setImage(LT_GRAY_TRAIN);
        systemTray1.setStatus("No Mail");

        systemTray1.getMenu().add(new MenuItem("Quit", e->{
            systemTray1.shutdown();
            //System.exit(0);  not necessary if all non-daemon threads have stopped.
        })).setShortcut('q'); // case does not matter


        System.err.println("Creating another tray");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        systemTray2 = SystemTray.get("SysTrayExample2");
        if (systemTray2 == null) {
            throw new RuntimeException("Unable to load SystemTray!");
        }

        systemTray2.setTooltip("Choo-Choo!");
        systemTray2.setImage(GREEN_TRAIN);
        systemTray2.setStatus("HONK");

        systemTray2.getMenu().add(new MenuItem("Quit", e->{
            systemTray2.shutdown();
            //System.exit(0);  not necessary if all non-daemon threads have stopped.
        })).setShortcut('q'); // case does not matter
    }
}
