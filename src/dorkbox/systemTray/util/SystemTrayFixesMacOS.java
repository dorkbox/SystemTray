/*
 * Copyright 2023 dorkbox, llc
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
package dorkbox.systemTray.util;

import static dorkbox.systemTray.SystemTray.logger;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.concurrent.atomic.AtomicReference;

import dorkbox.jna.ClassUtils;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.SwingUtil;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;


/*
 * When DISTRIBUTING the JRE/JDK by Sun/Oracle, the license agreement states that we cannot create/modify specific files.
 *
 ************* (when DISTRIBUTING the JRE/JDK...)
 * C. Java Technology Restrictions. You may not create, modify, or change the behavior of, or authorize your licensees to create, modify,
 * or change the behavior of, classes, interfaces, or subpackages that are in any way identified as "java", "javax", "sun" or similar
 * convention as specified by Oracle in any naming convention designation.
 *************
 *
 * Since we are not distributing a modified file, it does not apply to us.
 *
 * Again, just to be ABSOLUTELY CLEAR. This is for DISTRIBUTING the runtime.
 *
 * ************************************
 * To follow the license for DISTRIBUTION, these files themselves CANNOT BE MODIFIED in any way,
 * and if they are modified THEY CANNOT BE DISTRIBUTED.
 * ************************************
 *
 * Important distinction: We are not DISTRIBUTING java, nor modifying the distribution class files.
 *
 * What we are doing is modifying what is already present, post-distribution, and it is impossible to distribute what is modified
 */


/**
 * Fixes issues with some java runtimes
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public
class SystemTrayFixesMacOS {

    /**
     * NOTE: Only for SWING + AWT tray types
     * <p>
     * MacOS AWT is hardcoded to respond only to left-click for menus, where it should be ANY mouse button
     * <p>
     * https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
     * https://bugs.openjdk.java.net/browse/JDK-7158615
     * <p>
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/macosx/classes/sun/lwawt/macosx/CTrayIcon.java
     * <p>
     * The previous, native access we used to create menus NO LONGER works on any OS beyond Big Sur (macos 11), and now the *best* way
     * to access this (since I do not want to rewrite a LOT of code), is to use AWT hacks to access images + tooltips via reflection. This
     * has been possible since jdk8. While I don't like reflection, it is sadly the only way to do this.
     * <p>
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/7fcf35286d52/src/macosx/classes/sun/lwawt/macosx/CMenuItem.java
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/7fcf35286d52/src/macosx/native/sun/awt/CTrayIcon.m
     */
    public static
    void fix() {
        if (SystemTrayFixes.isSwingTrayLoaded("sun.lwawt.macosx.CTrayIcon")) {
            // we have to throw a significant error.
            throw new RuntimeException("Unable to initialize the AWT System Tray, it has already been created!");
        }

        ClassPool pool = ClassPool.getDefault();

        try {
            // allow non-reflection access to sun.awt.AWTAccessor...getPeer()
            {
                CtClass dynamicClass = pool.makeClass("java.awt.MenuComponentAccessory");
                CtMethod method = CtNewMethod.make(
                        "public static Object getPeer(java.awt.MenuComponent nativeComp) { " +
                            // "java.lang.System.err.println(\"Getting peer!\" + sun.awt.AWTAccessor.getMenuComponentAccessor().getPeer(nativeComp));" +
                            "return sun.awt.AWTAccessor.getMenuComponentAccessor().getPeer(nativeComp);" +
                        "}", dynamicClass);
                dynamicClass.addMethod(method);

                // CMenuItem can only PROPERLY be accessed from the java.awt package. Other locations might work within the JVM, but not
                // from a library
                method = CtNewMethod.make(
                        "public static void setImage(Object peerObj, java.awt.Image img) { " +
                            "((sun.lwawt.macosx.CMenuItem)peerObj).setImage(img);" +
                        "}", dynamicClass);
                dynamicClass.addMethod(method);

                method = CtNewMethod.make(
                        "public static void setToolTipText(Object peerObj, String text) { " +
                            "((sun.lwawt.macosx.CMenuItem)peerObj).setToolTipText(text);" +
                        "}", dynamicClass);
                dynamicClass.addMethod(method);


                dynamicClass.setModifiers(dynamicClass.getModifiers() & ~Modifier.STATIC);

                final byte[] dynamicClassBytes = dynamicClass.toBytecode();
                ClassUtils.defineClass(null, dynamicClassBytes);
            }

            {
                CtClass classFixer = pool.get("dorkbox.systemTray.util.AwtAccessor");

                CtMethod ctMethod = classFixer.getDeclaredMethod("getPeer");
                ctMethod.setBody("{" +
                                    "return java.awt.MenuComponentAccessory.getPeer($1);" +
                                 "}");

                // perform pre-verification for the modified method
                ctMethod.getMethodInfo().rebuildStackMapForME(pool);

                ctMethod = classFixer.getDeclaredMethod("setImage");
                ctMethod.setBody("{" +
                                    "java.awt.MenuComponentAccessory.setImage($1, $2);" +
                                 "}");

                // perform pre-verification for the modified method
                ctMethod.getMethodInfo().rebuildStackMapForME(pool);

                ctMethod = classFixer.getDeclaredMethod("setToolTipText");
                ctMethod.setBody("{" +
                                    "java.awt.MenuComponentAccessory.setToolTipText($1, $2);" +
                                 "}");

                // perform pre-verification for the modified method
                ctMethod.getMethodInfo().rebuildStackMapForME(pool);

                final byte[] classFixerBytes = classFixer.toBytecode();
                ClassUtils.defineClass(ClassLoader.getSystemClassLoader(), classFixerBytes);
            }

            if (SystemTray.DEBUG) {
                logger.debug("Successfully added images/tooltips to macOS AWT tray menus");
            }
        } catch (Exception e) {
            logger.error("Error adding SystemTray images/tooltips for macOS AWT tray menus.", e);
        }

        try {
            // must call this otherwise the robot call later on will crash.
            final Robot robot = new Robot();
            robot.waitForIdle();
            Point location = MouseInfo.getPointerInfo()
                                      .getLocation();
            final int x = location.x;
            final int y = location.y;

            robot.setAutoWaitForIdle(true);
            robot.mouseMove(x+1, y+1);

            location = MouseInfo.getPointerInfo().getLocation();

            final int x2 = location.x;
            final int y2 = location.y;

            if (x == x2 && y == y2) {
                // we cannot control the mouse, so we CANNOT rely on click emulation.
                logger.warn("Unable to control the mouse, please enable Accessibility permissions.");
                return;
            }


            // NOTE: This ONLY works if accessibility is granted via macOS permissions!
            byte[] mouseEventBytes;

            CtClass trayClass = pool.get("sun.lwawt.macosx.CTrayIcon");
            // now have to make a new "system tray" (that is null) in order to init/load this class completely
            // have to modify the SystemTray.getIconSize as well.
            trayClass.setModifiers(trayClass.getModifiers() & Modifier.PUBLIC);
            trayClass.getConstructors()[0].setModifiers(trayClass.getConstructors()[0].getModifiers() & Modifier.PUBLIC);

            CtField ctField = new CtField(CtClass.intType, "lastButton", trayClass);
            trayClass.addField(ctField);

            ctField = new CtField(pool.get("java.awt.Robot"), "robot", trayClass);
            trayClass.addField(ctField);

            CtMethod ctMethodGet = trayClass.getDeclaredMethod("handleMouseEvent");

            Class<?> nsEventClass = null;

            try {
                nsEventClass = Class.forName("sun.lwawt.macosx.event.NSEvent");
            } catch (Exception ignored) {
            }
            try {
                nsEventClass = Class.forName("sun.lwawt.macosx.NSEvent");
            } catch (Exception ignored) {
            }

            if (nsEventClass == null) {
                logger.error("Unable to properly check mouse trigger classes for macOS AWT tray menus");
                return;
            }

            String nsEventFQND = nsEventClass.getName();
            String mouseModInfo = "";
            String mousePressEventInfo = "";
            String mouseReleaseEventInfo = "";
            try {
                if (nsEventClass.getDeclaredMethod("nsToJavaMouseModifiers", int.class, int.class) != null) {
                    mouseModInfo = "int mouseMods = " + nsEventFQND + ".nsToJavaMouseModifiers(button, event.getModifierFlags());";
                    mousePressEventInfo = "java.awt.event.MouseEvent mEvent = new java.awt.event.MouseEvent(this.dummyFrame, eventType, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);";
                    mouseReleaseEventInfo = "java.awt.event.MouseEvent event7 = new java.awt.event.MouseEvent(this.dummyFrame, 500, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);";

                }
            } catch (Exception ignored) {
            }

            try {
                if (nsEventClass.getDeclaredMethod("nsToJavaModifiers", int.class) != null) {
                    mouseModInfo = "int mouseMods = " + nsEventFQND + ".nsToJavaModifiers(event.getModifierFlags());";
                    mousePressEventInfo = "java.awt.event.MouseEvent mEvent = new java.awt.event.MouseEvent(this.dummyFrame, eventType, event0, mouseMods, mouseX, mouseY, jClickCount, popupTrigger, jButton);";
                    mouseReleaseEventInfo = "java.awt.event.MouseEvent event7 = new java.awt.event.MouseEvent(this.dummyFrame, 500, event0, mouseMods, mouseX, mouseY, jClickCount, popupTrigger, jButton);";
                }
            } catch (Exception ignored) {
            }

            String mouseClickAction = "";
            if (OS.INSTANCE.getJavaVersion() > 8) {
                // java8 on macOS has problems where it doesn't properly assign mouse movement/clicks when emulating left-click behavior.
                // We aren't going to further support something that is also no longer supported.
                mouseClickAction =
                    "int maskButton1 = java.awt.event.InputEvent.getMaskForButton(java.awt.event.MouseEvent.BUTTON1);" +
                    "robot.mouseMove(mouseX, mouseY);" +
                    "robot.mousePress(maskButton1);";
            }


            ctMethodGet.setBody("{" +
                nsEventFQND + " event = $1;" +

                "sun.awt.SunToolkit toolKit = (sun.awt.SunToolkit)java.awt.Toolkit.getDefaultToolkit();" +
                "int button = event.getButtonNumber();" +
                "int mouseX = event.getAbsX();" +
                "int mouseY = event.getAbsY();" +

                // have to intercept to see if it was a button click redirect to preserve what button was used in the event
                "if (button > 0 && lastButton == 1) {" +
                    "int eventType = " + nsEventFQND + ".nsToJavaEventType(event.getType());" +
                    "if (eventType == 501) {" +
                        // "java.lang.System.err.println(\"Redefining button press to 1: \" + eventType);" +

                        "button = 1;" +
                        "lastButton = -1;" +
                    "}" +
                "}" +

                "if (button > 0 && (button <= 2 || toolKit.areExtraMouseButtonsEnabled()) && button <= toolKit.getNumberOfButtons() - 1) {" +
                    "int eventType = " + nsEventFQND + ".nsToJavaEventType(event.getType());" +
                    "int jButton = 0;" +
                    "int jClickCount = 0;" +

                    "if (eventType != 503) {" +
                        "jButton = " + nsEventFQND + ".nsToJavaButton(button);" +
                        "jClickCount = event.getClickCount();" +
                    "}" +


                    // "java.lang.System.err.println(\"Click \" + jButton + \" event: \" + eventType + \" x: \" + mouseX + \" y: \" + mouseY);" +


                    //"int mouseMods = " + nsEventFQND + ".nsToJavaMouseModifiers(button, event.getModifierFlags());" +
                    mouseModInfo +

                    // surprisingly, this is false when the popup is showing
                    "boolean popupTrigger = " + nsEventFQND + ".isPopupTrigger(mouseMods);" +

                    "int mouseMask = jButton > 0 ? java.awt.event.MouseEvent.getMaskForButton(jButton) : 0;" +
                    "long event0 = System.currentTimeMillis();" +

                    "if (eventType == 501) {" +
                        "mouseClickButtons |= mouseMask;" +
                    "} else if(eventType == 506) {" +
                        "mouseClickButtons = 0;" +
                    "}" +


                    // have to swallow + re-dispatch events in specific cases. (right click)
                    "if (eventType == 501 && popupTrigger && button != 0) {" +
                        // "java.lang.System.err.println(\"Redispatching mouse press. Has popupTrigger \" + " + "popupTrigger + \" event: \" + " + "eventType);" +

                        // we use Robot to left click where we right clicked, in order to "fool" the native part to show the popup
                        // For what it's worth, this is the only way to get the native bits to behave (since we cannot access the native parts).
                        "if (robot == null) {" +
                            "try {" +
                                "robot = new java.awt.Robot();" +
                                // the delay is necessary for this to work correctly.
                                "robot.setAutoDelay(10);" +
                                "robot.setAutoWaitForIdle(false);" +
                            "} " +
                            "catch (java.awt.AWTException e) {" +
                                "e.printStackTrace();" +
                            "}" +
                        "}" +

                        "lastButton = 1;" +

                        // "java.lang.System.err.println(\"Click \" + button + \" x: \" + mouseX + \" y: \" + mouseY);" +

                        // NOTE: This ONLY works if accessibility is granted via macOS permissions!
                        // Mouse release is not necessary.
                        // this simulates *just enough* of the default behavior so that right click behaves the same as left click.
                        // "int maskButton1 = java.awt.event.InputEvent.getMaskForButton(java.awt.event.MouseEvent.BUTTON1);" +
                        // "robot.mouseMove(mouseX, mouseY);" +
                        // "robot.mousePress(maskButton1);" +
                        mouseClickAction +

                        "return;" +
                     "}" +


                    //"java.awt.event.MouseEvent mEvent = new java.awt.event.MouseEvent(this.dummyFrame, eventType, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);" +
                    mousePressEventInfo +

                    "mEvent.setSource(this.target);" +
                    "this.postEvent(mEvent);" +

                    // mouse press
                    "if (eventType == 501) {" +
                        "if (popupTrigger) {" +
                            "String event5 = this.target.getActionCommand();" +
                            "java.awt.event.ActionEvent event6 = new java.awt.event.ActionEvent(this.target, 1001, event5);" +
                            "this.postEvent(event6);" +
                        "}" +
                    "}" +

                    // mouse release
                    "if (eventType == 502) {" +
                        "if ((mouseClickButtons & mouseMask) != 0) {" +
                            // "java.awt.event.MouseEvent event7 = new java.awt.event.MouseEvent(this.dummyFrame, 500, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);" +
                            mouseReleaseEventInfo +

                            "event7.setSource(this.target);" +
                            "this.postEvent(event7);" +
                        "}" +

                        "mouseClickButtons &= ~mouseMask;" +
                    "}" +
                "}" +
            "}");

            // perform pre-verification for the modified method
            ctMethodGet.getMethodInfo().rebuildStackMapForME(pool);
            mouseEventBytes = trayClass.toBytecode();

            // whoosh, past the classloader and directly into memory.
            // ClassUtils.defineClass(null, mouseEventBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed mouse trigger for macOS AWT tray menus");
            }
        } catch (Exception e) {
            logger.error("Error changing SystemTray mouse trigger for macOS AWT tray menus.", e);
        }
    }
}
