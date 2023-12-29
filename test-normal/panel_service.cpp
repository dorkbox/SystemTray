// sudo apt-get install libgtk2.0-dev libappindicator-dev
// gcc example2.c `pkg-config --cflags gtk+-2.0` -I/usr/include/libappindicator-0.1/ -o example `pkg-config --libs gtk+-2.0` -L/usr/lib -lappindicator


// https://sourcecodebrowser.com/unity/3.2.12/panel-main_8c_source.html
// http://bazaar.launchpad.net/~unity-team/unity/trunk/view/head:/services/panel-main.c
#include <gtk/gtk.h>
#include <libappindicator/app-indicator.h>

typedef struct panel_entry_s {
    gchar* id;
    void (*on_activate)(gint x, gint y, guint w, guint h);
} PanelEntry;

void handle_resync(GDBusProxy *proxy, PanelEntry *panel_entry)
{
    GError *err = NULL;
    GVariant *appindicators = g_dbus_proxy_call_sync(
        proxy,
        "SyncOne",
        g_variant_new("(s)", "libapplication.so"),
        G_DBUS_CALL_FLAGS_NONE,
        -1,
        NULL,
        &err
    );

    if (err != NULL)
    {
        printf("Error syncing libapplication: %s\n", err->message);
        g_error_free(err);
        return;
    }

    GVariantIter *iter;
    gchar *signature;
    gchar *id;
    gchar *entry_name;
    g_variant_get(appindicators, "(a(sssusbbusbbi))", &iter);
    while (g_variant_iter_loop(iter, "(sssusbbusbbi)", &signature, &id, &entry_name, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL))
    {
        if (g_strcmp0(signature, "libapplication.so") == 0 && g_strcmp0(entry_name, "testing-123") == 0)
        {
            if (g_strcmp0(panel_entry->id, id) != 0)
            {
                if (panel_entry->id != NULL)
                    g_free(panel_entry->id);
                panel_entry->id = g_strdup(id);
            }
        }
    }
    g_variant_iter_free(iter);
    g_variant_unref(appindicators);
}

void on_unity_panel_signal(GDBusProxy *proxy, gchar *sender_name, gchar *signal_name, GVariant *parameters, gpointer user_data)
{
    PanelEntry *panel_entry = (PanelEntry *) user_data;

    if (g_strcmp0(signal_name, "ReSync") == 0)
    {
        gchar *to_sync = NULL;
        g_variant_get(parameters, "(s)", &to_sync);
        if (g_strcmp0(to_sync, "libapplication.so") == 0)
        {
            handle_resync(proxy, panel_entry);
        }
        g_free(to_sync);
    }
    else if (g_strcmp0(signal_name, "EntryActivated") == 0)
    {
        gchar* entry_id;
        gint x, y;
        guint w, h;
        g_variant_get(parameters, "(ss(iiuu))", NULL, &entry_id, &x, &y, &w, &h);

        if (g_strcmp0(panel_entry->id, entry_id) == 0)
        {
            panel_entry->on_activate(x, y, w, h);
        }

        g_free(entry_id);
    }
}

void unity_panel_proxy_ready(GObject *source_object, GAsyncResult *res, gpointer user_data)
{
    GError *err = NULL;
    GDBusProxy *unity_panel_proxy = g_dbus_proxy_new_for_bus_finish(res, &err);

    if (err != NULL)
    {
        printf("Error creating DBus proxy: %s\n", err->message);
        g_error_free(err);
        return;
    }

    g_signal_connect(unity_panel_proxy, "g-signal", G_CALLBACK(on_unity_panel_signal), user_data);
}

PanelEntry *panel_entry_new(void (*on_activate)(gint x, gint y, guint w, guint h))
{
    PanelEntry *entry = g_malloc(sizeof(PanelEntry));
    entry->id= NULL;
    entry->on_activate = on_activate;
}

void panel_entry_free(PanelEntry *entry)
{
    printf("Freeing panel entry\n");
    if (entry->id != NULL)
        g_free(entry->id);
    g_free(entry);
}

static void on_panel_activate(gint x, gint y, guint w, guint h)
{
    printf("Panel activated! x: %d, y: %d, w: %d, h: %d\n", x, y, w, h);
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

    PanelEntry *panel_entry = panel_entry_new(&on_panel_activate);
    g_dbus_proxy_new_for_bus(
        G_BUS_TYPE_SESSION,
        G_DBUS_PROXY_FLAGS_DO_NOT_LOAD_PROPERTIES | G_DBUS_PROXY_FLAGS_DO_NOT_AUTO_START,
        NULL,
        "com.canonical.Unity.Panel.Service.Desktop",
        "/com/canonical/Unity/Panel/Service",
        "com.canonical.Unity.Panel.Service",
        NULL,
        unity_panel_proxy_ready,
        panel_entry
    );

    gtk_main();

    panel_entry_free(panel_entry);
    return 0;
}
