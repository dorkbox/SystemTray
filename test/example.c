// FROM: https://wiki.ubuntu.com/DesktopExperienceTeam/ApplicationIndicators
#include <gtk/gtk.h>
#include <libappindicator/app-indicator.h>


//  gcc example.c `pkg-config --cflags --libs gtk+-2.0 appindicator-0.1` -I/usr/include/libappindicator-0.1/ -o example && ./example

// apt libgtk-3-dev install libappindicator3-dev
// NOTE: there will be warnings, but the file will build and run. NOTE: this will not run as root on ubuntu (no dbus connection back to the normal user)
//  gcc example.c `pkg-config --cflags --libs gtk+-3.0 appindicator3-0.1` -I/usr/include/libappindicator3-0.1/ -o example && ./example


static void activate_action (GtkAction *action);

static GtkActionEntry entries[] = {
  { "FileMenu", NULL, "_File" },
  { "New",      "document-new", "_New", "<control>N",
    "Create a new file", G_CALLBACK (activate_action) },
  { "Open",     "document-open", "_Open", "<control>O",
    "Open a file", G_CALLBACK (activate_action) },
  { "Save",     "document-save", "_Save", "<control>S",
    "Save file", G_CALLBACK (activate_action) },
  { "Quit",     "application-exit", "_Quit", "<control>Q",
    "Exit the application", G_CALLBACK (gtk_main_quit) },
};
static guint n_entries = G_N_ELEMENTS (entries);

static const gchar *ui_info =
"<ui>"
"  <menubar name='MenuBar'>"
"    <menu action='FileMenu'>"
"      <menuitem action='New'/>"
"      <menuitem action='Open'/>"
"      <menuitem action='Save'/>"
"      <separator/>"
"      <menuitem action='Quit'/>"
"    </menu>"
"  </menubar>"
"  <popup name='IndicatorPopup'>"
"    <menuitem action='New' />"
"    <menuitem action='Open' />"
"    <menuitem action='Save' />"
"    <menuitem action='Quit' />"
"  </popup>"
"</ui>";

static void
activate_action (GtkAction *action)
{
        const gchar *name = gtk_action_get_name (action);
        GtkWidget *dialog;

        dialog = gtk_message_dialog_new (NULL,
                                         GTK_DIALOG_DESTROY_WITH_PARENT,
                                         GTK_MESSAGE_INFO,
                                         GTK_BUTTONS_CLOSE,
                                         "You activated action: \"%s\"",
                                         name);

        g_signal_connect (dialog, "response",
                          G_CALLBACK (gtk_widget_destroy), NULL);

        gtk_widget_show (dialog);
}
//
//static void
//bus_acquired (GDBusConnection *conn, const gchar *name, gpointer data)
//{
//    GError *err = NULL;
//    StatusNotifier *sn = (StatusNotifier *) data;
//    StatusNotifierPrivate *priv = sn->priv;
//    GDBusInterfaceVTable interface_vtable = {
//        .method_call = method_call,
//        .get_property = get_prop,
//        .set_property = NULL
//    };
//    GDBusNodeInfo *info;
//
//    info = g_dbus_node_info_new_for_xml (item_xml, NULL);
//    priv->dbus_reg_id = g_dbus_connection_register_object (conn,
//            ITEM_OBJECT,
//            info->interfaces[0],
//            &interface_vtable,
//            sn, NULL,
//            &err);
//    g_dbus_node_info_unref (info);
//    if (priv->dbus_reg_id == 0)
//    {
//        dbus_failed (sn, err, TRUE);
//        return;
//    }
//
//    priv->dbus_conn = g_object_ref (conn);
//}
//
//
//static void
//dbus_reg_item (StatusNotifier *sn)
//{
//    StatusNotifierPrivate *priv = sn->priv;
//    gchar buf[64], *b = buf;
//
//    if (G_UNLIKELY (g_snprintf (buf, 64, "org.kde.StatusNotifierItem-%u-%u",
//                    getpid (), ++uniq_id) >= 64))
//        b = g_strdup_printf ("org.kde.StatusNotifierItem-%u-%u",
//            getpid (), uniq_id);
//    priv->dbus_owner_id = g_bus_own_name (G_BUS_TYPE_SESSION,
//            b,
//            G_BUS_NAME_OWNER_FLAGS_NONE,
//            bus_acquired,
//            name_acquired,
//            name_lost,
//            sn, NULL);
//    if (G_UNLIKELY (b != buf))
//        g_free (b);
//}




// ORIGINAL, working
//int main (int argc, char **argv)
//{
//  GtkWidget *window;
//  GtkWidget *menubar;
//  GtkWidget *table;
//  GtkWidget *sw;
//  GtkWidget *contents;
//  GtkWidget *statusbar;
//  GtkWidget *indicator_menu;
//  GtkActionGroup *action_group;
//  GtkUIManager *uim;
//  AppIndicator *indicator;
//  GError *error = NULL;
//
//  gtk_init (&argc, &argv);
//
//  /* main window */
//  window = gtk_window_new (GTK_WINDOW_TOPLEVEL);
//  gtk_window_set_title (GTK_WINDOW (window), "Indicator Demo");
//  gtk_window_set_icon_name (GTK_WINDOW (window), "indicator-messages-new");
//  g_signal_connect (G_OBJECT (window),
//                    "destroy",
//                    G_CALLBACK (gtk_main_quit),
//                    NULL);
//
//  table = gtk_table_new (1, 5, FALSE);
//  gtk_container_add (GTK_CONTAINER (window), table);
//
//  /* Menus */
//  action_group = gtk_action_group_new ("AppActions");
//  gtk_action_group_add_actions (action_group,
//                                entries, n_entries,
//                                window);
//
//  uim = gtk_ui_manager_new ();
//  g_object_set_data_full (G_OBJECT (window),
//                          "ui-manager", uim,
//                          g_object_unref);
//  gtk_ui_manager_insert_action_group (uim, action_group, 0);
//  gtk_window_add_accel_group (GTK_WINDOW (window),
//                              gtk_ui_manager_get_accel_group (uim));
//
//  if (!gtk_ui_manager_add_ui_from_string (uim, ui_info, -1, &error))
//    {
//      g_message ("Failed to build menus: %s\n", error->message);
//      g_error_free (error);
//      error = NULL;
//    }
//
//  menubar = gtk_ui_manager_get_widget (uim, "/ui/MenuBar");
//  gtk_widget_show (menubar);
//  gtk_table_attach (GTK_TABLE (table),
//                    menubar,
//                    0, 1,                    0, 1,
//                    GTK_EXPAND | GTK_FILL,   0,
//                    0,                       0);
//
//  /* Document */
//  sw = gtk_scrolled_window_new (NULL, NULL);
//
//  gtk_scrolled_window_set_policy (GTK_SCROLLED_WINDOW (sw),
//                                  GTK_POLICY_AUTOMATIC,
//                                  GTK_POLICY_AUTOMATIC);
//
//  gtk_scrolled_window_set_shadow_type (GTK_SCROLLED_WINDOW (sw),
//                                       GTK_SHADOW_IN);
//
//  gtk_table_attach (GTK_TABLE (table),
//                    sw,
//                    /* X direction */       /* Y direction */
//                    0, 1,                   3, 4,
//                    GTK_EXPAND | GTK_FILL,  GTK_EXPAND | GTK_FILL,
//                    0,                      0);
//
//  gtk_window_set_default_size (GTK_WINDOW (window),
//                               200, 200);
//
//  contents = gtk_text_view_new ();
//  gtk_widget_grab_focus (contents);
//
//  gtk_container_add (GTK_CONTAINER (sw),
//                     contents);
//
//  /* Create statusbar */
//  statusbar = gtk_statusbar_new ();
//  gtk_table_attach (GTK_TABLE (table),
//                    statusbar,
//                    /* X direction */       /* Y direction */
//                    0, 1,                   4, 5,
//                    GTK_EXPAND | GTK_FILL,  0,
//                    0,                      0);
//
//  /* Show the window */
//  gtk_widget_show_all (window);
//
//  /* Indicator */
//  indicator = app_indicator_new ("example-simple-client",
//                                 "indicator-messages",
//                                 APP_INDICATOR_CATEGORY_APPLICATION_STATUS);
//
//  indicator_menu = gtk_ui_manager_get_widget (uim, "/ui/IndicatorPopup");
//
//  app_indicator_set_status (indicator, APP_INDICATOR_STATUS_ACTIVE);
//  app_indicator_set_attention_icon (indicator, "indicator-messages-new");
//
//  app_indicator_set_menu (indicator, GTK_MENU (indicator_menu));
//
//  gtk_main ();
//
//  return 0;
//}



static void
gtkCallback (GtkAction *action)
{
        const gchar *name = gtk_action_get_name (action);
        GtkWidget *dialog;

        dialog = gtk_message_dialog_new (NULL,
                                         GTK_DIALOG_DESTROY_WITH_PARENT,
                                         GTK_MESSAGE_INFO,
                                         GTK_BUTTONS_CLOSE,
                                         "You activated action: \"%s\"",
                                         name);

        g_signal_connect (dialog, "response",
                          G_CALLBACK (gtk_widget_destroy), NULL);

        gtk_widget_show (dialog);
}


int main (int argc, char **argv)
{
  GtkWidget *indicator_menu;
  GtkWidget *menuItem1;
  GtkWidget *menuItem2;

  AppIndicator *indicator;
  GError *error = NULL;

  gtk_init (&argc, &argv);

  /* Indicator */
  indicator = app_indicator_new ("example-simple-client",
                                 "/home/user/SystemTray/test/transport_train_station.p.39AC39.32.png",
                                 APP_INDICATOR_CATEGORY_APPLICATION_STATUS);

  app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE);
//  app_indicator_set_attention_icon_full(indicator, "/home/user/SystemTray/test/dorkbox/mail.000000.24.png", "1 ATTN");
    app_indicator_set_icon(indicator, "/home/user/SystemTray/test/dorkbox/transport_train_station.p.39AC39.32.png");


   indicator_menu = gtk_menu_new();


    menuItem1 = gtk_image_menu_item_new_with_label("menu1");

    // double check color info
    GtkStyle *style;
    style = gtk_rc_get_style(gtk_image_menu_item_new_with_mnemonic("xxx"));

    GdkColor color = style->fg[GTK_STATE_NORMAL];

    fprintf(stderr, "COLOR %s\n", gdk_color_to_string(&color));

//    g_signal_connect(menuItem1, "button_press_event", G_CALLBACK (gtkCallback), NULL);
    gtk_menu_shell_insert(GTK_MENU_SHELL(indicator_menu), menuItem1, 0);
    gtk_widget_show(menuItem1);

    menuItem2 = gtk_image_menu_item_new_with_label("menu2");
    gtk_menu_shell_insert(GTK_MENU_SHELL(indicator_menu), menuItem2, 1);
    gtk_widget_show(menuItem2);


    app_indicator_set_menu(indicator, GTK_MENU(indicator_menu));

    gtk_menu_item_set_label(GTK_MENU_ITEM(menuItem2), "asdasdasda");


//    g_signal_connect(menuItem2,
//                        "child-added",
//                        NULL,
//                        indicator_menu);
//
    gtk_widget_show_all(indicator_menu);


  gtk_main ();

  return 0;
}
