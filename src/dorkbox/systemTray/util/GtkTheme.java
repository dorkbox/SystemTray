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

import static dorkbox.jna.linux.Gtk.Gtk3;
import static dorkbox.jna.linux.Gtk2.Gtk2;
import static dorkbox.jna.linux.Gtk2.isGtk3;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dorkbox.jna.linux.GObject;
import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.jna.linux.GtkState;
import dorkbox.jna.linux.structs.GtkRequisition;
import dorkbox.jna.linux.structs.GtkStyle;
import dorkbox.jna.linux.structs.PangoRectangle;
import dorkbox.os.OS;

/**
 * Class to contain all the various methods needed to get information set by a GTK theme.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public
class GtkTheme {
    /** Fallback for an unknown tray image size. */
    public static volatile int TRAY_IMAGE_SIZE_FALLBACK = OS.INSTANCE.getInt(GtkTheme.class.getCanonicalName() + ".TRAY_IMAGE_SIZE_FALLBACK", 24);

    /** Fallback for an unknown tray menu image size. */
    public static volatile int TRAY_MENU_IMAGE_SIZE_FALLBACK = OS.INSTANCE.getInt(GtkTheme.class.getCanonicalName() + ".TRAY_MENU_IMAGE_SIZE_FALLBACK", 16);

    public static
    Rectangle getPixelTextHeight(String text) {
        // the following method requires an offscreen widget to get the size of text (for the checkmark size) via pango
        // don't forget to destroy everything!
        Pointer menu = null;
        Pointer item = null;

        try {
            menu = Gtk2.gtk_menu_new();
            item = Gtk2.gtk_image_menu_item_new_with_mnemonic(text);

            Gtk2.gtk_container_add(menu, item);

            Gtk2.gtk_widget_realize(menu);
            Gtk2.gtk_widget_realize(item);
            Gtk2.gtk_widget_show_all(menu);

            // get the text widget (GtkAccelLabel/GtkLabel) from inside the GtkMenuItem
            Pointer textLabel = Gtk2.gtk_bin_get_child(item);
            Pointer pangoLayout = Gtk2.gtk_label_get_layout(textLabel);

            // ink pixel size is how much exact space it takes on the screen
            PangoRectangle ink = new PangoRectangle();

            Gtk2.pango_layout_get_pixel_extents(pangoLayout, ink.getPointer(), null);
            ink.read();

            return new Rectangle(ink.width, ink.height);
        } finally {
            Gtk2.gtk_widget_destroy(item);
            Gtk2.gtk_widget_destroy(menu);
        }
    }

    /**
     * @return the size of the GTK menu entry's IMAGE, as best as we can tell, for determining how large of icons to use for the menu entry
     */
    public static
    int getMenuEntryImageSize() {
        final AtomicReference<Integer> imageHeight = new AtomicReference<>();

        GtkEventDispatch.dispatchAndWait(()->{
            Pointer offscreen = Gtk2.gtk_offscreen_window_new();

            // get the default icon size for the "paste" icon.
            Pointer item = null;

            try {
                item = Gtk2.gtk_image_menu_item_new_from_stock("gtk-paste", null);

                // make sure the image is shown (sometimes it's not always shown, then height is 0)
                Gtk2.gtk_image_menu_item_set_always_show_image(item, true);

                Gtk2.gtk_container_add(offscreen, item);
                Gtk2.gtk_widget_realize(offscreen);
                Gtk2.gtk_widget_realize(item);
                Gtk2.gtk_widget_show_all(item);

                PointerByReference r = new PointerByReference();
                GObject.g_object_get(item, "image", r.getPointer(), null);

                Pointer imageWidget = r.getValue();

                GtkRequisition gtkRequisition = new GtkRequisition();
                Gtk2.gtk_widget_size_request(imageWidget, gtkRequisition.getPointer());
                gtkRequisition.read();

                imageHeight.set(gtkRequisition.height);
            } finally {
                if (item != null) {
                    Gtk2.gtk_widget_destroy(item);
                    Gtk2.gtk_widget_destroy(offscreen);
                }
            }
        });


        int height = imageHeight.get();
        if (height > 0) {
            return height;
        }

        LoggerFactory.getLogger(GtkTheme.class).warn("Unable to get tray menu image size. Using fallback: " + TRAY_MENU_IMAGE_SIZE_FALLBACK);
        return TRAY_MENU_IMAGE_SIZE_FALLBACK;
    }

    /**
     * Gets the system tray indicator size.
     *  - AppIndicator:  will properly scale the image if it's not the correct size
     *  - GtkStatusIndicator:  ??
     */
    public static
    int getIndicatorSize() {
        // Linux is similar enough, that it just uses this method
        // https://wiki.archlinux.org/index.php/HiDPI

        // 96 DPI is the default
        final double defaultDPI = 96.0;

        final AtomicReference<Double> screenScale = new AtomicReference<>();
        final AtomicInteger screenDPI = new AtomicInteger();
        screenScale.set(0D);
        screenDPI.set(0);

        GtkEventDispatch.dispatchAndWait(()->{
            // screen DPI
            Pointer screen = Gtk2.gdk_screen_get_default();
            if (screen != null) {
                // this call makes NO SENSE, but reading the documentation shows it is the CORRECT call.
                screenDPI.set((int) Gtk2.gdk_screen_get_resolution(screen));
            }

            if (isGtk3) {
                Pointer window = Gtk2.gdk_get_default_root_window();
                if (window != null) {
                    double scale = Gtk3.gdk_window_get_scale_factor(window);
                    screenScale.set(scale);
                }
            }
        });


        // fallback
        if (screenDPI.get() <= 0) {
            // GET THE DPI IN LINUX
            // https://wiki.archlinux.org/index.php/Talk:GNOME
            Object detectedValue = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
            if (detectedValue instanceof Integer) {
                int asInteger = (Integer) detectedValue;
                // reasonably safe check
                if (asInteger > 1024) {
                    int dpi = asInteger / 1024;
                    screenDPI.set(dpi);
                }
            }
        }

        // 50 dpi is the minimum value gnome allows, and assume something screwed up. We apply this for ALL environments!
        if (screenDPI.get() < 50) {
            screenDPI.set((int) defaultDPI);
        }


        // check system ENV variables.
        if (screenScale.get() == 0) {
            String envVar = System.getenv("QT_AUTO_SCREEN_SCALE_FACTOR");
            if (envVar != null) {
                try {
                    screenScale.set(Double.parseDouble(envVar));
                } catch (Exception ignored) {
                }
            }
        }

        // check system ENV variables.
        if (screenScale.get() == 0) {
            String envVar = System.getenv("QT_SCALE_FACTOR");
            if (envVar != null) {
                try {
                    screenScale.set(Double.parseDouble(envVar));
                } catch (Exception ignored) {
                }
            }
        }

        // check system ENV variables.
        if (screenScale.get() == 0) {
            String envVar = System.getenv("GDK_SCALE");
            if (envVar != null) {
                try {
                    screenScale.set(Double.parseDouble(envVar));
                } catch (Exception ignored) {
                }
            }
        }

        // check system ENV variables.
        if (screenScale.get() == 0) {
            String envVar = System.getenv("ELM_SCALE");
            if (envVar != null) {
                try {
                    screenScale.set(Double.parseDouble(envVar));
                } catch (Exception ignored) {
                }
            }
        }



        OS.DesktopEnv.Env env = OS.DesktopEnv.INSTANCE.getEnv();

        // sometimes the scaling-factor is set. If we have gsettings, great! otherwise try KDE
        try {
            String output = KotlinUtils.INSTANCE.execute("/usr/bin/gsettings", "get", "org.gnome.desktop.interface", "scaling-factor");
            if (!output.isEmpty() && !output.endsWith("not found")) {
                // DEFAULT icon size is 16. HiDpi changes this scale, so we should use it as well.
                // should be: uint32 0  or something
                if (output.contains("uint32")) {
                    String value = output.substring(output.indexOf("uint") + 7);

                    // 0 is disabled (no scaling)
                    // 1 is enabled (default scale)
                    // 2 is 2x scale
                    // 3 is 3x scale
                    // etc

                    double scalingFactor = Double.parseDouble(value);
                    if (scalingFactor >= 1) {
                        screenScale.set(scalingFactor);
                    }

                    // A setting of 2, 3, etc, which is all you can do with scaling-factor
                    // To enable HiDPI, use gsettings:
                    // gsettings set org.gnome.desktop.interface scaling-factor 2
                }
            }
        } catch (Throwable ignore) {
        }

        if (OS.DesktopEnv.INSTANCE.isKDE()) {
            // check the custom KDE override file
            try {
                File customSettings = new File("/usr/bin/startkde-custom");
                if (customSettings.canRead()) {
                    List<String> lines = KotlinUtils.INSTANCE.readLines(customSettings);
                    for (String line : lines) {
                        String str = "export GDK_SCALE=";
                        int i = line.indexOf(str);
                        if (i > -1) {
                            String scale = line.substring(i + str.length());
                            double scalingFactor = Double.parseDouble(scale);
                            if (scalingFactor >= 1) {
                                screenScale.set(scalingFactor);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }


//        System.err.println("screen scale: " + screenScale.get());
//        System.err.println("screen DPI: " + screenDPI.get());

            if (screenScale.get() == 0) {
                /*
                 *
                 * Looking in  plasma-framework/src/declarativeimports/core/units.cpp:
                    // Scale the icon sizes up using the devicePixelRatio
                    // This function returns the next stepping icon size
                    // and multiplies the global settings with the dpi ratio.
                    const qreal ratio = devicePixelRatio();

                    if (ratio < 1.5) {
                        return size;
                    } else if (ratio < 2.0) {
                        return size * 1.5;
                    } else if (ratio < 2.5) {
                        return size * 2.0;
                    } else if (ratio < 3.0) {
                        return size * 2.5;
                    } else if (ratio < 3.5) {
                        return size * 3.0;
                    } else {
                        return size * ratio;
                    }
                    My ratio is 1.47674, that means I have no scaling at all when there is a 1.5 factor existing. Is it reasonable? Wouldn't it make more sense to use the factor the closest to the ratio rather than  what is done here?
                 */

                File mainFile = new File("/usr/share/plasma/plasmoids/org.kde.plasma.private.systemtray/contents/config/main.xml");
                if (mainFile.canRead()) {
                    List<String> lines = KotlinUtils.INSTANCE.readLines(mainFile);
                    boolean found = false;
                    int index;
                    for (final String line : lines) {
                        if (line.contains("<entry name=\"iconSize\" type=\"Int\">")) {
                            found = true;
                            // have to get the "default" line value
                        }

                        String str = "<default>";
                        if (found && (index = line.indexOf(str)) > -1) {
                            // this is our line. now get the value.
                            String substring = line.substring(index + str.length(), line.indexOf("</default>", index));

                            // Default icon size for the systray icons, it's an enum which values mean,
                            // Small, SmallMedium, Medium, Large, Huge, Enormous respectively.
                            // On low DPI systems they correspond to :
                            //    16, 22, 32, 48, 64, 128 pixels.
                            // On high DPI systems those values would be scaled up, depending on the DPI.
                            int imageSize = 0;
                            Integer imageSizeEnum = KotlinUtils.INSTANCE.toInteger(substring);
                            if (imageSizeEnum != null) {
                                switch (imageSizeEnum) {
                                    case 0:
                                        imageSize = 16;
                                        break;
                                    case 1:
                                        imageSize = 22;
                                        break;
                                    case 2:
                                        imageSize = 32;
                                        break;
                                    case 3:
                                        imageSize = 48;
                                        break;
                                    case 4:
                                        imageSize = 64;
                                        break;
                                    case 5:
                                        imageSize = 128;
                                        break;
                                }

                                if (imageSize > 0) {
                                    double scaleRatio = screenDPI.get() / defaultDPI;

                                    return (int) (scaleRatio * imageSize);
                                }
                            }
                        }
                    }
                }
            }
            
            // The following is a bit ugly, but these are the defaults for KDE-plasma
            String plasmaVersion = OS.DesktopEnv.INSTANCE.getPlasmaVersionFull();
            if (plasmaVersion != null) {
                // this shouldn't ever happen, because we have the KDE config file!

                String[] versionParts = plasmaVersion.split("\\.");
                int majorVersion = Integer.parseInt(versionParts[0]);
                int minorVersion = Integer.parseInt(versionParts[1]);
                if (majorVersion < 5 || (majorVersion == 5 && minorVersion < 5)) {
                    // older versions use GtkStatusIcon
                    return 22;
                } else {
                    // newer versions use appindicator, but the user MIGHT have to install libappindicator

                    // 128 is the max size possible, and it will AUTOMATICALLY scale down as necessary.
                    return 128;
                }
            }
        }

        if (OS.Linux.INSTANCE.isUbuntu() && OS.DesktopEnv.INSTANCE.isUnity(env)) {
            // if we measure on ubuntu unity using a screen shot (using swing, so....) , the max size was 24, HOWEVER this goes from
            // the top->bottom of the indicator bar -- and since it was swing, it uses a different rendering method and it (honestly)
            // looks weird, because there is no padding for the icon. The official AppIndicator size is hardcoded...
            // http://bazaar.launchpad.net/~indicator-applet-developers/libindicator/trunk.16.10/view/head:/libindicator/indicator-image-helper.c

            return 22;
        }

        if (env == OS.DesktopEnv.Env.XFCE) {
            // xfce is easy, because it's not a GTK setting for the size  (xfce notification area maximum icon size)
            String properties = OS.DesktopEnv.INSTANCE.queryXfce("xfce4-panel", null);
            String[] propertiesAsList = properties.split(OS.INSTANCE.getLINE_SEPARATOR());
            for (String prop : propertiesAsList) {
                if (prop.startsWith("/plugins/") && prop.endsWith("/size-max")) {
                    // this is the property we are looking for (we just don't know which panel it's on)
                    // note: trim() is required because it will strip new-line

                    // xfconf-query -c xfce4-panel -p /plugins/plugin-14  (this will say 'systray' or 'tasklist' or whatever)
                    // find the 'systray' plugin
                    String panelString = prop.substring(0, prop.indexOf("/size-max"));
                    String panelName = OS.DesktopEnv.INSTANCE.queryXfce("xfce4-panel", panelString).trim();
                    if (panelName.equals("systray")) {
                        String size = OS.DesktopEnv.INSTANCE.queryXfce("xfce4-panel", prop).trim();
                        try {
                            return Integer.parseInt(size);
                        } catch (Exception e) {
                            LoggerFactory.getLogger(GtkTheme.class)
                                         .error("Unable to get XFCE notification panel size for channel '{}', property '{}'",
                                                "xfce4-panel", prop, e);
                        }
                    }
                }
            }
        }

        // try to use GTK to get the tray icon size
        final AtomicInteger traySize = new AtomicInteger();
        GtkEventDispatch.dispatchAndWait(()->{
            Pointer screen = Gtk2.gdk_screen_get_default();
            Pointer settings = null;

            if (screen != null) {
                settings = Gtk2.gtk_settings_get_for_screen(screen);
            }

            if (settings != null) {
                PointerByReference pointer = new PointerByReference();

                // https://wiki.archlinux.org/index.php/GTK%2B
                // To use smaller icons, use a line like this:
                //    gtk-icon-sizes = "panel-menu=16,16:panel=16,16:gtk-menu=16,16:gtk-large-toolbar=16,16:gtk-small-toolbar=16,16:gtk-button=16,16"

                // this gets icon sizes. On XFCE, ubuntu, it returns "panel-menu-bar=24,24"
                // NOTE: gtk-icon-sizes is deprecated and ignored since GTK+ 3.10.

                // A list of icon sizes. The list is separated by colons, and item has the form: size-name = width , height
                GObject.g_object_get(settings, "gtk-icon-sizes", pointer.getPointer(), null);
                Pointer value = pointer.getValue();
                if (value != null) {
                    // this might be null for later versions of GTK!
                    String iconSizes = value.getString(0);
                    String[] strings = new String[] {"panel-menu-bar=", "panel=", "gtk-large-toolbar=", "gtk-small-toolbar="};
                    for (String var : strings) {
                        int i = iconSizes.indexOf(var);
                        if (i >= 0) {
                            String size = iconSizes.substring(i + var.length(), iconSizes.indexOf(",", i));

                            Integer sizeInt = KotlinUtils.INSTANCE.toInteger(size);
                            if (sizeInt != null) {
                                traySize.set(sizeInt);
                                return;
                            }
                        }
                    }
                }
            }
        });

        int i = traySize.get();
        if (i != 0) {
            return i;
        }

        // sane default
        LoggerFactory.getLogger(GtkTheme.class).warn("Unable to get tray image size. Using fallback: " + TRAY_IMAGE_SIZE_FALLBACK);
        return TRAY_IMAGE_SIZE_FALLBACK;
    }

    /**
     * @return the widget color of text for the current theme, or black. It is important that this is called AFTER GTK has been initialized.
     */
    public static
    Color getTextColor() {
        final AtomicReference<Color> color = new AtomicReference<>(null);
        GtkEventDispatch.dispatchAndWait(()->{
            Color c;

            // the following method requires an offscreen widget to get the style information from.
            // don't forget to destroy everything!
            Pointer menu = null;
            Pointer item = null;

            try {
                menu = Gtk2.gtk_menu_new();
                item = Gtk2.gtk_image_menu_item_new_with_mnemonic("a");

                Gtk2.gtk_container_add(menu, item);

                Gtk2.gtk_widget_realize(menu);
                Gtk2.gtk_widget_realize(item);
                Gtk2.gtk_widget_show_all(menu);

                GtkStyle style = Gtk2.gtk_rc_get_style(item);
                style.read();

                // this is the same color chromium uses (fg)
                // https://chromium.googlesource.com/chromium/src/+/b3ca230ddd7d1238ee96ed26ea23e369f10dd655/chrome/browser/ui/libgtk2ui/gtk2_ui.cc#873
                c = style.fg[GtkState.NORMAL].getColor();

                color.set(c);
            } finally {
                Gtk2.gtk_widget_destroy(item);
                Gtk2.gtk_widget_destroy(menu);
            }
        });

        return color.get();
    }


}
