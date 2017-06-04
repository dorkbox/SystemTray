package dorkbox.systemTray.jna.linux;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.FileUtil;

/**
 * Class to contain all of the methods needed to get the text color from the AppIndicator/GtkStatusIcon menu entry. This is primarily
 * used to get the color needed for the checkmark icon. In GTK, the checkmark icon can be defined to be it's OWN color and
 * shape, however getting/parsing that would be even significantly more difficult -- so we decided to make the icon the same color
 * as the text.
 * <p>
 * Additionally, CUSTOM, user theme modifications in ~/.gtkrc-2.0 (for example), will be ignored.
 */
@SuppressWarnings("deprecation")
public
class GtkTheme {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SHOW_CSS = false;
    private static final boolean DEBUG_VERBOSE = false;

    // size of GTK checkbox
    //
    /*

     -GtkCheckMenuItem-indicator-size: 14;

.entry {
    padding: 3px;
    border-width: 1px;
    border-style: solid;
    border-top-color: shade(@theme_bg_color, 0.6);
    border-right-color: shade(@theme_bg_color, 0.7);
    border-left-color: shade(@theme_bg_color, 0.7);
    border-bottom-color: shade(@theme_bg_color, 0.72);
    border-radius: 3px;
    background-color: @theme_base_color;
    background-image: linear-gradient(to bottom,
                                      shade(@theme_base_color, 0.99),
                                      @theme_base_color
                                      );

    color: @theme_text_color;
}


.menuitem .entry {
    border-color: shade(@menu_bg_color, 0.7);
    background-color: @menu_bg_color;
    background-image: none;
    color: @menu_fg_color;
}

GtkPopover {
    margin: 10px;
    padding: 2px;
    border-radius: 3px;
    border-color: shade(@menu_bg_color, 0.8);
    border-width: 1px;
    border-style: solid;
    background-clip: border-box;
    background-image: none;
    background-color: @menu_bg_color;
    color: @menu_fg_color;
    box-shadow: 0 2px 3px alpha(black, 0.5);
}

GtkPopover .entry {
    border-color: mix(@menu_bg_color, @menu_fg_color, 0.12);
    background-color: @menu_bg_color;
    background-image: none;
    color: @menu_fg_color;
}

     */
    public static
    int getTextHeight() {
        String css = getCss();
        if (css != null) {

        }



        return 9;
    }


    public static
    int getTextPadding() {
        return 9;
    }






    /**
     * @return the widget color of text for the current theme, or black. It is important that this is called AFTER GTK has been initialized.
     */
    public static
    Color getTextColor() {
        // NOTE: when getting CSS, we redirect STDERR to null (via GTK), so that we won't spam the console if there are parse errors.
        //   this is a horrid hack, but the only way to work around the errors we have no control over. The parse errors, if bad enough
        //   just mean that we are unable to get the CSS as we want.

        // these methods are from most accurate (but limited in support) to compatible across Linux OSes.. Strangely enough, GTK makes
        // this information insanely difficult to get.
        final AtomicReference<Color> color = new AtomicReference<Color>(null);
        Gtk.dispatchAndWait(new Runnable() {

            @Override
            public
            void run() {
                // see if we can get the info via CSS properties (> GTK+ 3.2 uses an API, GTK2 gets it from disk)
                Color c = getFromCss();
                if (c != null) {
                    if (DEBUG) {
                        System.err.println("Got from CSS");
                    }
                    color.set(c);
                    return;
                }

                // we got here because it's not possible to get the info via raw-CSS

                // try to get via the color scheme. A bit more accurate than parsing the raw theme file
                c = getFromColorScheme();
                if (c != null) {
                    if (DEBUG) {
                        System.err.println("Got from color scheme");
                    }
                    color.set(c);
                    return;
                }


                // if we get here, this means that there was NO "gtk-color-scheme" value in the theme file.
                // This usually happens when the theme does not have @fg_color (for example), but instead has each color explicitly
                // defined for every widget instance in the theme file. Old/bizzare themes tend to do it this way...
                if (Gtk.isGtk2) {
                    c = getFromGtk2ThemeText();
                    if (c != null) {
                        if (DEBUG) {
                            System.err.println("Got from gtk2 color theme file");
                        }
                        color.set(c);
                        return;
                    }
                }


                // the following methods all require an offscreen widget to get the style information from. This rarely is correct for
                // some bizzare reason.


                // create an off-screen widget (don't forget to destroy everything!)
                Pointer menu = Gtk.gtk_menu_new();
                Pointer item = Gtk.gtk_image_menu_item_new_with_mnemonic("a");

                Gtk.gtk_container_add(menu, item);
                Gtk.gtk_widget_show_all(item);

                // Try to get via RC style... Sometimes this works (sometimes it does not...)
                {
                    Pointer style = Gtk.gtk_rc_get_style(item);

                    GdkColor gdkColor = new GdkColor();
                    boolean success;

                    success = Gtk.gtk_style_lookup_color(style, "menu_fg_color", gdkColor.getPointer());
                    if (!success) {
                        success = Gtk.gtk_style_lookup_color(style, "text_color", gdkColor.getPointer());
                    }
                    if (!success) {
                        success = Gtk.gtk_style_lookup_color(style, "theme_text_color", gdkColor.getPointer());
                    }
                    if (success) {
                        color.set(gdkColor.getColor());

                        Gtk.gtk_widget_destroy(item);
                        return;
                    }
                }

                if (Gtk.isGtk3) {
                    Pointer context = Gtk3.gtk_widget_get_style_context(item);
                    int state = Gtk3.gtk_style_context_get_state(context);

                    GdkRGBAColor gdkColor = new GdkRGBAColor();
                    boolean success = Gtk3.gtk_style_context_lookup_color(context, "fg_color", gdkColor.getPointer());
                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "text_color", gdkColor.getPointer());
                    }
                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "menu_fg_color", gdkColor.getPointer());
                    }

                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "color", gdkColor.getPointer());
                    }

                    if (success) {
                        color.set(new Color((float) gdkColor.red, (float) gdkColor.green, (float) gdkColor.blue, (float) gdkColor.alpha));
                    }
                    else {
                        // fall back in case nothing else works
                        Gtk3.gtk_style_context_get_color(context, state, gdkColor.getPointer());
                        if (gdkColor.red == 0.0 && gdkColor.green == 0.0 && gdkColor.blue == 0.0 && gdkColor.alpha == 0.0) {
                            // have nothing here, check something else...
                            if (DEBUG) {
                                System.err.println("No valid output from gtk_style_context_get_color");
                            }
                        }
                        else {
                            // if we have something that is not all 0's
                            color.set(new Color((float) gdkColor.red,
                                                (float) gdkColor.green,
                                                (float) gdkColor.blue,
                                                (float) gdkColor.alpha));
                        }
                    }
                }

                // this also doesn't always work...
                GtkStyle.ByReference style = Gtk.gtk_widget_get_style(item);
                color.set(style.text[Gtk.State.NORMAL].getColor());

                Gtk.gtk_widget_destroy(item);
            }
        });


        Color color1 = color.get();
        if (color1 != null) {
            if (DEBUG) {
                System.err.println("COLOR FOUND: " + color1);
            }
            return color1;
        }

        SystemTray.logger.error("Unable to determine the text color in use by your system. Please create an issue and include your " +
                                "full OS configuration and desktop environment, including theme details, such as the theme name, color " +
                                "variant, and custom theme options (if any).");

        // who knows WHAT the color is supposed to be. This is just a "best guess" default value.
        return Color.BLACK;
    }

    /**
     * get the color we are interested in via raw CSS parsing. This is specifically to get the color of the text of the
     * appindicator/gtk-status-icon menu.
     * <p>
     * > GTK+ 3.2 uses an API, GTK2 gets it from disk
     *
     * @return the color string, parsed from CSS/
     */
    private static
    Color getFromCss() {
        String css = getCss();
        if (css != null) {
            if (DEBUG_SHOW_CSS) {
                System.err.println(css);
            }

            // in order from left to right, try to get the color value
            String[] nodes = new String[] {"GtkPopover", "unity-panel", "gnome-panel-menu-bar", "PanelMenuBar", ".menuitem", ".entry"};


            // collect a list of all of the sections that have what we are interested in.
            List<CssNode> sections = getSections(css, nodes);

            //
            String colorString = getAttributeFromSections(sections, nodes, "color");

            // hopefully we found it.
            if (colorString != null) {
                if (colorString.startsWith("@")) {
                    // it's a color definition
                    colorString = colorString.substring(1);

                    // have to setup the "define color" section
                    String colorDefine = "@define-color";
                    int start = css.indexOf(colorDefine);
                    int end = css.lastIndexOf(colorDefine);
                    end = css.lastIndexOf(";", end) + 1; // include the ;
                    String colorDefines = css.substring(start, end);

                    if (DEBUG_VERBOSE) {
                        System.err.println("+++++++++++++++++++++++");
                        System.err.println(colorDefines);
                        System.err.println("+++++++++++++++++++++++");
                    }

                    // since it's a color definition, it will start a very specific way.
                    String newColorString = colorDefine + " " + colorString;

                    int i = 0;
                    while (i != -1) {
                        i = colorDefines.indexOf(newColorString);

                        if (i >= 0) {
                            try {
                                int startIndex = i + newColorString.length();
                                int endIndex = colorDefines.indexOf(";", i);

                                String colorSubString = colorDefines.substring(startIndex, endIndex)
                                                                    .trim();

                                if (colorSubString.startsWith("@")) {
                                    // have to recursively get the defined color
                                    newColorString = colorDefine + " " + colorSubString.substring(1);
                                    i = 0;
                                    continue;
                                }

                                return parseColor(colorSubString);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                else {
                    return parseColor(colorString);
                }
            }
        }

        return null;
    }


    /**
     * @return the CSS for the current theme or null. It is important that this is called AFTER GTK has been initialized.
     */
    public static
    String getCss() {
        if (Gtk.isGtk3) {
            final AtomicReference<String> css = new AtomicReference<String>(null);

            Gtk.dispatchAndWait(new Runnable() {
                @Override
                public
                void run() {
                    String themeName = getThemeName();

                    if (themeName != null) {
                        Pointer value = Gtk3.gtk_css_provider_get_named(themeName, null);
                        if (value != null) {
                            // we have the css provider!

                            // NOTE: This can output warnings if the theme doesn't parse correctly by GTK, so we suppress them
                            Glib.GLogFunc orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);

                            css.set(Gtk3.gtk_css_provider_to_string(value));

                            Glib.g_log_set_default_handler(orig, null);
                        }
                    }
                    else {
                        Pointer value = Gtk3.gtk_css_provider_get_default();
                        if (value != null) {
                            // we have the css provider!

                            // NOTE: This can output warnings if the theme doesn't parse correctly by GTK, so we suppress them
                            Glib.GLogFunc orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);

                            css.set(Gtk3.gtk_css_provider_to_string(value));

                            Glib.g_log_set_default_handler(orig, null);
                        }
                    }
                }
            });

            // will be either the string, or null.
            return css.get();
        }
        else {
            // GTK2 has to get the GTK3 theme text a different way (parsing it from disk). SOMETIMES, the app must be GTK2, even though
            // the system is GTK3. This works around the API restriction if we are an APP in GTK2 mode. This is not done ALL the time,
            // because this is not as accurate as using the GTK3 API.
            return getGtk3ThemeCssViaFile();
        }
    }

    /**
     * this works for GtkStatusIcon menus.
     *
     * @return the menu_fg/fg/text color from gtk-color-scheme or null
     */
    public static
    Color getFromColorScheme() {
        Pointer settings = Gtk.gtk_settings_get_default();
        if (settings != null) {
            // see if we can get the info we want the EASY way (likely only when GTK+ 2 is used, but can be < GTK+ 3.2)...

            //  been deprecated since version 3.8
            PointerByReference pointer = new PointerByReference();
            Gobject.g_object_get(settings, "gtk-color-scheme", pointer, null);


            // A palette of named colors for use in themes. The format of the string is
            //      name1: color1
            //      name2: color2
            //
            //  Color names must be acceptable as identifiers in the gtkrc syntax, and color specifications must be in the format
            //      accepted by gdk_color_parse().
            //
            // Note that due to the way the color tables from different sources are merged, color specifications will be converted
            // to hexadecimal form when getting this property.
            //
            //  Starting with GTK+ 2.12, the entries can alternatively be separated by ';' instead of newlines:
            //      name1: color1; name2: color2; ...
            //
            // GtkSettings:gtk-color-scheme has been deprecated since version 3.8 and should not be used in newly-written code.
            //  Color scheme support was dropped and is no longer supported. You can still set this property, but it will be ignored.


            Pointer value = pointer.getValue();
            if (value != null) {
                String s = value.getString(0);
                if (!s.isEmpty()) {
                    if (DEBUG) {
                        System.out.println("\t string: " + s);
                    }

                    // Note: these are the values on my system when forcing GTK+ 2 (XUbuntu 16.04) with GtkStatusIcon and Aidwata theme
                    // bg_color_dark: #686868686868
                    // fg_color: #3c3c3c3c3c3c
                    // fm_color: #f7f7f7f7f7f7
                    // selected_fg_color: #ffffffffffff
                    // panel_bg: #686868686868
                    // text_color: #212121212121
                    // text_color_dark: #ffffffffffff
                    // tooltip_bg_color: #000000000000
                    // link_color: #2d2d7171b8b8
                    // tooltip_fg_color: #e1e1e1e1e1e1
                    // base_color: #fcfcfcfcfcfc
                    // bg_color: #cececececece
                    // selected_bg_color: #39398e8ee7e7

                    // list of colors, in order of importance, that we want to parse.
                    String colors[] = new String[] {"menu_fg_color", "fg_color", "text_color"};

                    for (String colorName : colors) {
                        int i = 0;
                        while (i != -1) {
                            i = s.indexOf(colorName, i);
                            if (i >= 0) {
                                try {
                                    // the color will ALWAYS be in hex notation

                                    // it is also possible to be separated by ; instead of newline
                                    int endIndex = s.indexOf(';', i);
                                    if (endIndex == -1) {
                                        endIndex = s.indexOf('\n', i);
                                    }

                                    if (s.charAt(i - 1) == '_') {
                                        i = endIndex;
                                        continue;
                                    }

                                    int startIndex = s.indexOf('#', i);
                                    String colorString = s.substring(startIndex, endIndex)
                                                          .trim();

                                    if (DEBUG) {
                                        System.out.println("Color string: " + colorString);
                                    }
                                    return parseColor(colorString);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks in the following locations for the current GTK3 theme.
     * <p>
     * /usr/share/themes
     * /opt/gnome/share/themes
     */
    private static
    String getGtk3ThemeCssViaFile() {
        File themeDirectory = getThemeDirectory(true);

        if (themeDirectory == null) {
            return null;
        }

        File gtkFile = new File(themeDirectory, "gtk.css");

        try {
            StringBuilder stringBuilder = new StringBuilder((int) (gtkFile.length()));
            FileUtil.read(gtkFile, stringBuilder);

            removeComments(stringBuilder);

            // only comments in the file
            if (stringBuilder.length() < 2) {
                return null;
            }

            injectAdditionalCss(themeDirectory, stringBuilder);

            return stringBuilder.toString();
        } catch (IOException ignored) {
            // cant read the file or something else.
        }

        return null;
    }

    /**
     * @return the discovered fg[NORMAL] or text[NORMAL] color for this theme or null
     */
    public static
    Color getFromGtk2ThemeText() {
        String gtk2ThemeText = getGtk2ThemeText();

        if (gtk2ThemeText != null) {
            String[] colorText = new String[] {"fg[NORMAL]", "text[NORMAL]"};
            for (String text : colorText) {
                int i = 0;
                while (i != -1) {
                    i = gtk2ThemeText.indexOf(text, i);
                    if (i != -1) {
                        if (i > 0 && gtk2ThemeText.charAt(i - 1) != '_') {
                            i += text.length();
                            continue;
                        }


                        int j = gtk2ThemeText.indexOf("=", i);
                        if (j != -1) {
                            int lineEnd = gtk2ThemeText.indexOf('\n', j);

                            if (lineEnd != -1) {
                                String colorName = gtk2ThemeText.substring(j + 1, lineEnd)
                                                                .trim();

                                colorName = colorName.replaceAll("\"", "");
                                return parseColor(colorName);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }


    /**
     * Checks in the following locations for the current GTK2 theme.
     * <p>
     * /usr/share/themes
     * /opt/gnome/share/themes
     */
    private static
    String getGtk2ThemeText() {
        File themeDirectory = getThemeDirectory(false);

        if (themeDirectory == null) {
            return null;
        }


        // ie: /usr/share/themes/Numix/gtk-2.0/gtkrc
        File gtkFile = new File(themeDirectory, "gtkrc");

        try {
            StringBuilder stringBuilder = new StringBuilder((int) (gtkFile.length()));
            FileUtil.read(gtkFile, stringBuilder);

            removeComments(stringBuilder);

            // only comments in the file
            if (stringBuilder.length() < 2) {
                return null;
            }

            return stringBuilder.toString();
        } catch (IOException ignored) {
            // cant read the file or something else.
        }

        return null;
    }


    /**
     * Figures out what the directory is for the specified type of GTK theme files (css/gtkrc/etc)
     *
     * @param gtk3 true if you want to look for the GTK3 theme dir, false if you want the GTK2 theme dir
     *
     * @return the directory or null if it cannot be found
     */
    public static
    File getThemeDirectory(boolean gtk3) {
        String themeName = getThemeName();

        if (themeName == null) {
            return null;
        }

        String gtkType;
        if (gtk3) {
            gtkType = "gtk-3.0";
        }
        else {
            gtkType = "gtk-2.0";
        }


        String[] dirs = new String[] {"/usr/share/themes", "/opt/gnome/share/themes"};

        // ie: /usr/share/themes
        for (String dirName : dirs) {
            File themesDir = new File(dirName);

            File[] themeDirs = themesDir.listFiles();
            if (themeDirs != null) {
                // ie: /usr/share/themes/Numix
                for (File themeDir : themeDirs) {
                    File[] files1 = themeDir.listFiles();
                    if (files1 != null) {
                        boolean isCorrectTheme;

                        File themeIndexFile = new File(themeDir, "index.theme");
                        try {
                            List<String> read = FileUtil.read(themeIndexFile, false);
                            for (String s : read) {
                                if (s.startsWith("GtkTheme=")) {
                                    String calculatedThemeName = s.substring("GtkTheme=".length());

                                    isCorrectTheme = calculatedThemeName.equals(themeName);

                                    if (isCorrectTheme) {
                                        // ie: /usr/share/themes/Numix/gtk-3.0/gtk.css
                                        // the DARK variant is only used by some apps. The dark variant is NOT SYSTEM-WIDE!
                                        return new File(themeDir, gtkType);
                                    }

                                    break;
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Parses out the color from a color:
     * <p>
     * - the word "transparent"
     * - hex 12 digit  #ffffaaaaffff
     * - hex 6 digit   #ffaaff
     * - hex 3 digit   #faf
     * - rgb(r, g, b)  rgb(33, 33, 33)
     * - rgb(r, g, b)  rgb(.6, .3, .3)
     * - rgb(r%, g%, b%)  rgb(10%, 20%, 30%)
     * - rgba(r, g, b, a)  rgb(33, 33, 33, 0.53)
     * - rgba(r, g, b, a)  rgb(.33, .33, .33, 0.53)
     * - rgba(r, g, b, a)  rgb(10%, 20%, 30%, 0.53)
     * <p>
     * Notes:
     * - rgb(), when an int, is between 0-255
     * - rgb(), when a float, is between 0.0-1.0
     * - rgb(), when a percent, is between 0-100
     * - alpha is always a float
     *
     * @return the parsed color
     */
    @SuppressWarnings("Duplicates")
    private static
    Color parseColor(String colorString) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int alpha = 255;

        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);

            if (colorString.length() > 11) {
                red = Integer.parseInt(colorString.substring(0, 4), 16);
                green = Integer.parseInt(colorString.substring(4, 8), 16);
                blue = Integer.parseInt(colorString.substring(8), 16);

                // Have to convert to positive int (value between 0 and 65535, these are 16 bits per pixel) that is from 0-255
                red = red & 0x0000FFFF;
                green = green & 0x0000FFFF;
                blue = blue & 0x0000FFFF;

                red = (red >> 8) & 0xFF;
                green = (green >> 8) & 0xFF;
                blue = (blue >> 8) & 0xFF;
            }
            else if (colorString.length() > 5) {
                red = Integer.parseInt(colorString.substring(0, 2), 16);
                green = Integer.parseInt(colorString.substring(2, 4), 16);
                blue = Integer.parseInt(colorString.substring(4), 16);
            }
            else {
                red = Integer.parseInt(colorString.substring(0, 1), 16);
                green = Integer.parseInt(colorString.substring(1, 2), 16);
                blue = Integer.parseInt(colorString.substring(2), 16);
            }
        }
        else if (colorString.startsWith("rgba")) {
            colorString = colorString.substring(colorString.indexOf('(') + 1, colorString.indexOf(')'));
            String[] split = colorString.split(",");

            String trim1 = split[0].trim();
            String trim2 = split[1].trim();
            String trim3 = split[2].trim();
            String trim4 = split[3].trim();

            if (colorString.contains("%")) {
                trim1 = trim1.replace("%", "");
                trim2 = trim2.replace("%", "");
                trim3 = trim3.replace("%", "");

                red = Integer.parseInt(trim1) * 255;
                green = Integer.parseInt(trim2) * 255;
                blue = Integer.parseInt(trim3) * 255;
            }
            else if (colorString.contains(".")) {
                red = (int) (Float.parseFloat(trim1) * 255);
                green = (int) (Float.parseFloat(trim2) * 255);
                blue = (int) (Float.parseFloat(trim3) * 255);
            }
            else {
                red = Integer.parseInt(trim1);
                green = Integer.parseInt(trim2);
                blue = Integer.parseInt(trim3);
            }

            float alphaF = Float.parseFloat(trim4);
            alpha = (int) (alphaF * 255);
        }
        else if (colorString.startsWith("rgb")) {
            colorString = colorString.substring(colorString.indexOf('(') + 1, colorString.indexOf(')'));
            String[] split = colorString.split(",");

            String trim1 = split[0].trim();
            String trim2 = split[1].trim();
            String trim3 = split[2].trim();

            if (colorString.contains("%")) {
                trim1 = trim1.replace("%", "");
                trim2 = trim2.replace("%", "");
                trim3 = trim3.replace("%", "");

                red = Integer.parseInt(trim1) * 255;
                green = Integer.parseInt(trim2) * 255;
                blue = Integer.parseInt(trim3) * 255;
            }
            else if (colorString.contains(".")) {
                red = (int) (Float.parseFloat(trim1) * 255);
                green = (int) (Float.parseFloat(trim2) * 255);
                blue = (int) (Float.parseFloat(trim3) * 255);
            }
            else {
                red = Integer.parseInt(trim1);
                green = Integer.parseInt(trim2);
                blue = Integer.parseInt(trim3);
            }
        }
        else if (colorString.contains("transparent")) {
            alpha = 0;
        }
        else {
            int index = colorString.indexOf(";");
            if (index > 0) {
                colorString = colorString.substring(0, index);
            }
            colorString = colorString.replaceAll("\"", "");
            colorString = colorString.replaceAll("'", "");

            // maybe it's just a "color" description, such as "red"?
            try {
                return Color.decode(colorString);
            } catch (Exception e) {
                return null;
            }
        }

        return new Color(red, green, blue, alpha);
    }

    /**
     * https://wiki.archlinux.org/index.php/GTK%2B
     * <p>
     * gets the name of the currently loaded theme
     * GTK+ 2:
     * ~/.gtkrc-2.0
     * gtk-icon-theme-name = "Adwaita"
     * gtk-theme-name = "Adwaita"
     * gtk-font-name = "DejaVu Sans 11"
     * <p>
     * <p>
     * GTK+ 3:
     * $XDG_CONFIG_HOME/gtk-3.0/settings.ini
     * [Settings]
     * gtk-icon-theme-name = Adwaita
     * gtk-theme-name = Adwaita
     * gtk-font-name = DejaVu Sans 11
     * <p>
     * <p>
     * Note: The icon theme name is the name defined in the theme's index file, not the name of its directory.
     * <p>
     * directories:
     * /usr/share/themes
     * /opt/gnome/share/themes
     * <p>
     * GTK+ 2 user specific: ~/.gtkrc-2.0
     * GTK+ 2 system wide: /etc/gtk-2.0/gtkrc
     * <p>
     * GTK+ 3 user specific: $XDG_CONFIG_HOME/gtk-3.0/settings.ini, or $HOME/.config/gtk-3.0/settings.ini if $XDG_CONFIG_HOME is not set
     * GTK+ 3 system wide: /etc/gtk-3.0/settings.ini
     *
     * @return the theme name, or null if it cannot find it.
     */
    public static
    String getThemeName() {
        String themeName = null;

        Pointer settings = Gtk.gtk_settings_get_default();
        if (settings != null) {


            PointerByReference pointer = new PointerByReference();
            Gobject.g_object_get(settings, "gtk-theme-name", pointer, null);

            Pointer value = pointer.getValue();
            if (value != null) {
                themeName = value.getString(0);
            }
        }

        if (DEBUG) {
            System.err.println("Theme name: " + themeName);
        }

        return themeName;
    }

    private static class CssNode {
        String label;
        List<CssAttribute> attributes;

        CssNode(final String label, final List<CssAttribute> attributes) {
            this.label = label;
            this.attributes = attributes;
        }

        @Override
        public
        String toString() {
            return label + ' ' + Arrays.toString(attributes.toArray());
        }
    }

    private static class CssAttribute {
        String key;
        String value;

        public
        CssAttribute(final String key, final String value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Gets the sections of text, of the specified CSS nodes.
     */
    private static
    List<CssNode> getSections(String css, String[] nodes) {
        // collect a list of all of the sections that have what we are interested in
        List<CssNode> sections = new ArrayList<CssNode>();

        // now check the css nodes to see if they contain a combination of what we are looking for.
        colorCheck:
        for (String node : nodes) {
            int i = 0;
            while (i != -1) {
                i = css.indexOf(node, i);
                if (i > -1) {
                    int endOfNodeLabels = css.indexOf("{", i);
                    int endOfSection = css.indexOf("}", endOfNodeLabels + 1) + 1;
                    int endOfSectionTest = css.indexOf("}", i) + 1;

                    // this makes sure that weird parsing errors don't happen as a result of node keywords appearing in node sections
                    if (endOfSection != endOfSectionTest) {
                        // advance the index
                        i = endOfSection;
                        continue;
                    }

                    String nodeLabel = css.substring(i, endOfNodeLabels);

                    List<CssAttribute> attributes = new ArrayList<CssAttribute>();

                    // split the section into an arrayList, one per item. Split by attribute element
                    String nodeSection = css.substring(endOfNodeLabels, endOfSection);
                    int start = nodeSection.indexOf('{')+1;
                    while (start != -1) {
                        int end = nodeSection.indexOf(';', start);
                        if (end != -1) {
                            int seperator = nodeSection.indexOf(':', start);

                            if (seperator < end) {
                                String key = nodeSection.substring(start, seperator).trim();
                                String value = nodeSection.substring(seperator+1, end).trim();

                                attributes.add(new CssAttribute(key, value));
                            }
                            start = end+1;
                        } else {
                            break;
                        }
                    }

                    sections.add(new CssNode(nodeLabel, attributes));

                    // advance the index
                    i = endOfSection;
                }
            }
        }

        if (DEBUG_VERBOSE) {
            for (CssNode section : sections) {
                System.err.println("--------------");
                System.err.println(section);
                System.err.println("--------------");
            }
        }

        return sections;
    }

    // find an attribute name from the list of sections. The incoming sections will all be related to one of the nodes, we just have to
    // prioritize them on WHO has the attribute we are looking for.
    private static
    String getAttributeFromSections(final List<CssNode> sections, final String[] nodes, final String attributeName) {
        // a list of sections that contains the exact attribute we are looking for
        List<CssNode> sectionsWithAttribute = new ArrayList<CssNode>();


        for (CssNode cssNode : sections) {
            for (CssAttribute attribute : cssNode.attributes) {
                if (attribute.key.equals(attributeName)) {
                    sectionsWithAttribute.add(cssNode);
                }
            }
        }

        // if we only have 1, then return that one
        if (sectionsWithAttribute.size() == 1) {
            CssNode cssNode = sectionsWithAttribute.get(0);
            for (CssAttribute attribute : cssNode.attributes) {
                if (attribute.key.equals(attributeName)) {
                    return attribute.value;
                }
            }

            return null;
        }


        // now we need to narrow down which sections have the attribute.
        // This is because a section can override another, and we want to reflect that info.
        // If a section has more than one node as it's label, then it has a higher priority, and overrides ones that only have a single label.
        // IE:  "GtkPopover .entry"  overrides   ".entry"


        int maxValue = -1;
        CssNode maxNode = null; // guaranteed to be non-null

        // not the CLEANEST way to do this, but it works by only choosing css nodes that are important
        for (CssNode cssNode : sectionsWithAttribute) {
            int count = 0;
            for (String node : nodes) {
                String label = cssNode.label;
                boolean startsWith = label.startsWith(node);
                boolean contains = label.contains(node);

                if (startsWith) {
                    count+=1;
                } else if (contains) {
                    // if it has MORE than just one node label (that we care about), it's more important that one that is by itself.
                    count+=2;
                }
            }

            if (count > maxValue) {
                maxValue = count;
                maxNode = cssNode;
            }
        }


        // get the attribute from the highest scoring node
        //noinspection ConstantConditions
        for (CssAttribute attribute : maxNode.attributes) {
            if (attribute.key.equals(attributeName)) {
                return attribute.value;
            }
        }

        return null;
    }


    @SuppressWarnings("Duplicates")
    private static
    void removeComments(final StringBuilder stringBuilder) {
        // remove block comments, /* .... */  This can span multiple lines
        int start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("/*", start);

            if (start != -1) {
                // get the end of a comment
                int end = stringBuilder.indexOf("*/", start);
                if (end != -1) {
                    stringBuilder.delete(start, end + 2); // 2 is the size of */

                    // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                    if (stringBuilder.charAt(start) == '\n') {
                        stringBuilder.delete(start, start + 1);
                    }
                    else {
                        start++;
                    }
                }
            }
        }

        // now remove comments that start with // (line MUST start with //)
        start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("//", start);

            if (start != -1) {
                // the comment is at the start of a line
                if (start == 0 || stringBuilder.charAt(start-1) == '\n') {
                    // get the end of the comment (the end of the line)
                    int end = stringBuilder.indexOf("\n", start);
                    if (end != -1) {
                        stringBuilder.delete(start, end + 1); // 1 is the size of \n
                    }
                }

                // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                if (stringBuilder.charAt(start) == '\n') {
                    stringBuilder.delete(start, start + 1);
                }
                else if (start > 0){
                    start++;
                }
            }
        }

        // now remove comments that start with # (line MUST start with #)
        start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("#", start);

            if (start != -1) {
                // the comment is at the start of a line
                if (start == 0 || stringBuilder.charAt(start-1) == '\n') {
                    // get the end of the comment (the end of the line)
                    int end = stringBuilder.indexOf("\n", start);
                    if (end != -1) {
                        stringBuilder.delete(start, end + 1); // 1 is the size of \n
                    }
                }

                // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                if (stringBuilder.charAt(start) == '\n') {
                    stringBuilder.delete(start, start + 1);
                }
                else if (start > 0){
                    start++;
                }
            }
        }
    }

    private static
    void injectAdditionalCss(final File parent, final StringBuilder stringBuilder) {
        // not the BEST way to do this because duplicates are not merged at all.

        int start = 0;
        while (start != -1) {
            // now check if it says: @import url("gtk-main.css")
            start = stringBuilder.indexOf("@import url(", start);

            if (start != -1) {
                int end = stringBuilder.indexOf("\")", start);
                if (end != -1) {
                    String url = stringBuilder.substring(start + 13, end);
                    stringBuilder.delete(start, end + 2); // 2 is the size of ")

                    if (DEBUG_VERBOSE) {
                        System.err.println("import url: " + url);
                    }
                    try {
                        // now inject the new file where the import command was.
                        File file = new File(parent, url);
                        StringBuilder stringBuilder2 = new StringBuilder((int) (file.length()));
                        FileUtil.read(file, stringBuilder2);

                        removeComments(stringBuilder2);

                        stringBuilder.insert(start, stringBuilder2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
