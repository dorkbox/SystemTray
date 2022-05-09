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

import dorkbox.jna.rendering.ProviderType;
import dorkbox.jna.rendering.Renderer;
import dorkbox.os.OS;


public
class JavaFxProvider implements Renderer {
    @Override
    public
    boolean isSupported() {
        return true;
    }

    @Override
    public
    ProviderType getType() {
        return ProviderType.JAVAFX;
    }

    @Override
    public
    boolean alreadyRunning() {
        // this is only true for SWT. JavaFX running detection is elsewhere
        return false;
    }

    @Override
    public
    boolean isEventThread() {
        return javafx.application.Platform.isFxApplicationThread();
    }

    @Override
    public
    int getGtkVersion() {
        if (!OS.INSTANCE.isLinux()) {
            return 0;
        }

        // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
        // see
        // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
        // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

        // HILARIOUSLY enough, you can use JavaFX + SWT..... And the javaFX GTK version info SHOULD
        // be based on what SWT has loaded

        // https://github.com/teamfx/openjfx-9-dev-rt/blob/master/modules/javafx.graphics/src/main/java/com/sun/glass/ui/gtk/GtkApplication.java

        if (OS.INSTANCE.getJavaVersion() < 9) {
            // JavaFX from Oracle Java 8 is GTK2 only. Java9 can have it be GTK3 if -Djdk.gtk.version=3 is specified
            // see http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
            return 2;
        }


        // Only possible Java9+ (so our case, Java11+ since 9 is no longer available, 11 is officially LTS)
        String version = OS.INSTANCE.getProperty("jdk.gtk.version", "2");
        if ("3".equals(version) || version.startsWith("3.")) {
            return 3;
        }
        else {
            return 2;
        }
    }

    @Override
    public
    boolean dispatch(final Runnable runnable) {
        // JavaFX only
        if (isEventThread()) {
            // Run directly on the JavaFX event thread
            runnable.run();
        }
        else {
            javafx.application.Platform.runLater(runnable);
        }

        // javaFX always manages the runnable
        return true;
    }
}
