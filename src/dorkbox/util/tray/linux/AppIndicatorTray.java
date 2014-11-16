package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;


/**
 * Class for handling all system tray interactions.
 *
 * specialization for using app indicators in ubuntu unity
 *
 * Heavily modified from
 *
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public class AppIndicatorTray extends SystemTray {

  private static final AppIndicator libappindicator = AppIndicator.INSTANCE;
  private static final Gobject libgobject = Gobject.INSTANCE;
  private static final Gtk libgtk = Gtk.INSTANCE;

  private final CountDownLatch blockUntilStarted = new CountDownLatch(1);
  private final Map<String, MenuEntry> menuEntries = new HashMap<String, MenuEntry>(2);

  private volatile AppIndicator.AppIndicatorInstanceStruct appIndicator;
  private volatile Pointer menu;

  private volatile Pointer connectionStatusItem;

  // need to hang on to these to prevent gc
  private final List<Pointer> widgets = new ArrayList<Pointer>(4);


  public AppIndicatorTray() {
  }

  @Override
  public void createTray(String iconName) {
    this.appIndicator =
        libappindicator
            .app_indicator_new(this.appName, "indicator-messages-new", AppIndicator.CATEGORY_APPLICATION_STATUS);

        /* basically a hack -- we should subclass the AppIndicator
           type and override the fallback entry in the 'vtable', instead we just
           hack the app indicator class itself. Not an issue unless we need other
           appindicators.
        */
    AppIndicator.AppIndicatorClassStruct
        aiclass =
        new AppIndicator.AppIndicatorClassStruct(this.appIndicator.parent.g_type_instance.g_class);

    aiclass.fallback = new AppIndicator.Fallback() {
      @Override
      public Pointer callback(final AppIndicator.AppIndicatorInstanceStruct self) {
        AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
          @Override
          public void run() {
            logger.warn("Failed to create appindicator system tray.");

            if (AppIndicatorTray.this.failureCallback != null) {
              AppIndicatorTray.this.failureCallback.createTrayFailed();
            }
          }
        });
        return null;
      }
    };
    aiclass.write();

    this.menu = libgtk.gtk_menu_new();
    libappindicator.app_indicator_set_menu(this.appIndicator, this.menu);

    libappindicator.app_indicator_set_icon_full(this.appIndicator, iconPath(iconName), this.appName);
    libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_ACTIVE);

    if (!GtkSupport.usesSwtMainLoop) {
      Thread gtkUpdateThread = new Thread() {
        @Override
        public void run() {
          // notify our main thread to continue
          AppIndicatorTray.this.blockUntilStarted.countDown();

          try {
            libgtk.gtk_main();
          } catch (Throwable t) {
            logger.warn("Unable to run main loop", t);
          }
        }
      };
      gtkUpdateThread.setName("GTK event loop");
      gtkUpdateThread.setDaemon(true);
      gtkUpdateThread.start();
    }

    // we CANNOT continue until the GTK thread has started! (ignored if SWT is used)
    try {
      this.blockUntilStarted.await();
      this.active = true;
    } catch (InterruptedException ignored) {
    }
  }

  @Override
  public void removeTray() {
    for (Pointer widget : this.widgets) {
      libgtk.gtk_widget_destroy(widget);
    }

    // this hides the indicator
    libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_PASSIVE);
    this.appIndicator.write();
    Pointer p = this.appIndicator.getPointer();
    libgobject.g_object_unref(p);

    this.active = false;

    // GC it
    this.appIndicator = null;
    this.widgets.clear();

    // unrefs the children too
    libgobject.g_object_unref(this.menu);
    this.menu = null;

    synchronized (this.menuEntries) {
      this.menuEntries.clear();
    }

    this.connectionStatusItem = null;

    super.removeTray();
  }

  @Override
  public void setStatus(String infoString, String iconName) {
    if (this.connectionStatusItem == null) {
      this.connectionStatusItem = libgtk.gtk_menu_item_new_with_label(infoString);
      this.widgets.add(this.connectionStatusItem);
      libgtk.gtk_widget_set_sensitive(this.connectionStatusItem, Gtk.FALSE);
      libgtk.gtk_menu_shell_append(this.menu, this.connectionStatusItem);
    } else {
      libgtk.gtk_menu_item_set_label(this.connectionStatusItem, infoString);
    }

    libgtk.gtk_widget_show_all(this.connectionStatusItem);

    libappindicator.app_indicator_set_icon_full(this.appIndicator, iconPath(iconName), this.appName);
  }

  /**
   * Will add a new menu entry, or update one if it already exists
   */
  @Override
  public void addMenuEntry(String menuText, final SystemTrayMenuAction callback) {
    synchronized (this.menuEntries) {
      MenuEntry menuEntry = this.menuEntries.get(menuText);

      if (menuEntry == null) {
        Pointer dashboardItem = libgtk.gtk_menu_item_new_with_label(menuText);
        Gobject.GCallback gtkCallback = new Gobject.GCallback() {
          @Override
          public void callback(Pointer instance, Pointer data) {
            AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
              @Override
              public void run() {
                callback.onClick(AppIndicatorTray.this);
              }
            });
          }
        };

        libgobject.g_signal_connect_data(dashboardItem, "activate", gtkCallback, null, null, 0);
        libgtk.gtk_menu_shell_append(this.menu, dashboardItem);
        libgtk.gtk_widget_show_all(dashboardItem);

        menuEntry = new MenuEntry();
        menuEntry.dashboardItem = dashboardItem;

        this.menuEntries.put(menuText, menuEntry);
      } else {
        updateMenuEntry(menuText, menuText, callback);
      }
    }
  }

  /**
   * Will update an already existing menu entry (or add a new one, if it doesn't exist)
   */
  @Override
  public void updateMenuEntry(String origMenuText, String newMenuText, final SystemTrayMenuAction newCallback) {
    synchronized (this.menuEntries) {
      MenuEntry menuEntry = this.menuEntries.get(origMenuText);

      if (menuEntry != null) {
        libgtk.gtk_menu_item_set_label(menuEntry.dashboardItem, newMenuText);

        Gobject.GCallback gtkCallback = new Gobject.GCallback() {
          @Override
          public void callback(Pointer instance, Pointer data) {
            AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
              @Override
              public void run() {
                newCallback.onClick(AppIndicatorTray.this);
              }
            });
          }
        };

        libgobject.g_signal_connect_data(menuEntry.dashboardItem, "activate", gtkCallback, null, null, 0);

        libgtk.gtk_widget_show_all(menuEntry.dashboardItem);
      } else {
        addMenuEntry(origMenuText, newCallback);
      }
    }
  }
}
