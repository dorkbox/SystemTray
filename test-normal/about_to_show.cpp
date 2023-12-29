// from: https://github.com/seppestas/test-libappindicator

// sudo apt-get install libgtk2.0-dev libappindicator-dev
// gcc example2.c `pkg-config --cflags gtk+-2.0` -I/usr/include/libappindicator-0.1/ -o example `pkg-config --libs gtk+-2.0` -L/usr/lib -lappindicator

#include <libdbusmenu-glib/server.h>
#include <libappindicator/app-indicator.h>


void on_about_to_show(DbusmenuMenuitem *menuitem, gpointer user_data)
{
    printf("About to show menu\n");
}

int main( int   argc,
          char *argv[] )
{
    gtk_init (&argc, &argv);

    gchar *cwd = g_get_current_dir();
    AppIndicator *indicator = app_indicator_new_with_path(
        "testing-123",
        "icon",
        APP_INDICATOR_CATEGORY_APPLICATION_STATUS,
        cwd
    );
    g_free(cwd);

    app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE);

    GtkWidget *menu_widget = gtk_menu_new();
    GtkMenu* menu = GTK_MENU(menu_widget);
    GtkMenuShell* menu_shell = GTK_MENU_SHELL(menu);

    GtkWidget *menu_item = gtk_menu_item_new_with_mnemonic("");
    gtk_widget_show(menu_item);
    gtk_menu_shell_append(menu_shell, menu_item);

    app_indicator_set_menu(indicator, menu);

    DbusmenuServer *menuServer;
    DbusmenuMenuitem *rootMenuItem;
    g_object_get(indicator, "dbus-menu-server", &menuServer, NULL);
    g_object_get(menuServer, "root-node", &rootMenuItem, NULL);

    g_signal_connect(rootMenuItem, "about-to-show", G_CALLBACK(on_about_to_show), NULL);

    gtk_main();

    return 0;
}
