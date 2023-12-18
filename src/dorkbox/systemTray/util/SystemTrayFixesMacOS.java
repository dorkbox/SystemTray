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

import java.util.concurrent.atomic.AtomicBoolean;

import dorkbox.jna.ClassUtils;
import dorkbox.systemTray.SystemTray;
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
    private static AtomicBoolean loaded = new AtomicBoolean(false);

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
    void fix(final SystemTray.TrayType trayType) {
        if (trayType != SystemTray.TrayType.Awt && trayType != SystemTray.TrayType.Osx) {
            // Swing on macOS is pretty bland. AWT (with fixes) looks fantastic (and is native)
            // AWT on macosx doesn't respond to all buttons (but should)
            return;
        }


        if (loaded.getAndSet(true)) {
            // already loaded, no need to fix again in the same JVM.
            return;
        }

        if (SystemTrayFixes.isSwingTrayLoaded("sun.lwawt.macosx.CTrayIcon")) {
            // we have to throw a significant error.
            throw new RuntimeException("Unable to initialize the AWT System Tray, it has already been created!");
        }

        ClassPool pool = ClassPool.getDefault();

        try {
            {
                try {
                    // have to make the peer field public
                    CtClass trayIconClass = pool.get("java.awt.TrayIcon");
                    CtField peer = trayIconClass.getField("peer");
                    peer.setModifiers(peer.getModifiers() & Modifier.PUBLIC);
                    ClassUtils.defineClass(null, trayIconClass.toBytecode());
                } catch (LinkageError e) {
                    logger.error("Linkage error making the java.awt.TrayIcon peer field public.", e);
                }

                CtClass trayClass = pool.get("sun.lwawt.macosx.CTrayIcon");
                {
                    CtMethod method2 = CtNewMethod.make("public java.awt.geom.Point2D getIconLocation(long ptr) {" +
                                                            "return nativeGetIconLocation(ptr);" +
                                                        "}",
                                                        trayClass);
                    trayClass.addMethod(method2);

                    // javassist cannot create ANONYMOUS inner classes, but can create normal classes. Such a pain to do it this way
                    CtClass dynamicClass = pool.makeClass("sun.lwawt.macosx.CTrayIconLocationAccessory");
                    dynamicClass.addInterface(pool.get("sun.lwawt.macosx.CFRetainedResource$CFNativeAction"));

                    CtField ctField = new CtField(pool.get("java.util.concurrent.atomic.AtomicReference"), "ref", dynamicClass);
                    dynamicClass.addField(ctField, "new java.util.concurrent.atomic.AtomicReference();");

                    ctField = new CtField(pool.get("sun.lwawt.macosx.CTrayIcon"), "icon", dynamicClass);
                    dynamicClass.addField(ctField);

                    CtMethod method3 = CtNewMethod.make("public void run(long ptr){" +
                                                            "ref.set(icon.getIconLocation(ptr));" +
                                                        "}", dynamicClass);
                    dynamicClass.addMethod(method3);

                    ClassUtils.defineClass(null, dynamicClass.toBytecode());
                }


                CtMethod method = CtNewMethod.make(
                        "public java.awt.geom.Point2D getLocation() { " +
                            "sun.lwawt.macosx.CTrayIconLocationAccessory refAccess = new sun.lwawt.macosx.CTrayIconLocationAccessory();" +
                            "refAccess.icon = this;" +
                            "execute(refAccess);" +
                            "return refAccess.ref.get();" +
                        "}", trayClass);

                trayClass.addMethod(method);
                ClassUtils.defineClass(null, trayClass.toBytecode());

                if (SystemTray.DEBUG) {
                    logger.debug("Successfully added getLocation() to macOS AWT tray menus");
                }
            }

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

                method = CtNewMethod.make(
                        "public static void showPopup(java.awt.Component component, java.awt.Menu nativeComponent) { " +
                            "java.awt.peer.PopupMenuPeer peer = (java.awt.peer.PopupMenuPeer) getPeer(nativeComponent);" +
                            //"java.lang.System.err.println(\"showing popup peer!\");" +
                            "peer.show(new java.awt.Event(component, 0L, java.awt.Event.MOUSE_DOWN, 0, 0, 0, 0));" +
                        "}", dynamicClass);
                dynamicClass.addMethod(method);

                method = CtNewMethod.make(
                        "public static java.awt.geom.Point2D getLocation(java.awt.TrayIcon trayIcon) { " +
                            "return ((sun.lwawt.macosx.CTrayIcon) trayIcon.peer).getLocation();" +
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


                ctMethod = classFixer.getDeclaredMethod("showPopup");
                ctMethod.setBody("{" +
                                    "return java.awt.MenuComponentAccessory.showPopup($1, $2);" +
                                 "}");
                // perform pre-verification for the modified method
                ctMethod.getMethodInfo().rebuildStackMapForME(pool);

                ctMethod = classFixer.getDeclaredMethod("getLocation");
                ctMethod.setBody("{" +
                                 "return java.awt.MenuComponentAccessory.getLocation($1);" +
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
    }
}
