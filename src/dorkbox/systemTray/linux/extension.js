/*
 * Copyright 2015 dorkbox, llc
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
 *
 *
 * This is heavily modified from an online email/pastebin by Vladimir Khrustalev.
 *
 * The source material was not licensed explicitly or implicitly, as such,
 * this is considered as released by the original sources as public domain.
 *
 * Vladimir's email address is unknown.
 *
 * Viewing the log messages can be performed by one of the following:
 *   gnome-shell --replace  (you will see the log messages in the console)
 *   journalctl
 *   .cache/gdm/session.log
 *   .xsession-errors
 *   /var/log/messages
 *   alt-F2 -> lg -> extensions
 *   ~/.cache/upstart/gnome-session.log
 *   journalctl /usr/bin/gnome-shell -f -o cat
 */

const Clutter = imports.gi.Clutter;
const Shell = imports.gi.Shell;
const St = imports.gi.St;
const Main = imports.ui.main;
const GLib = imports.gi.GLib;
const Lang = imports.lang;
const Panel = imports.ui.panel;
const PanelMenu = imports.ui.panelMenu;
const Meta = imports.gi.Meta;
const Mainloop = imports.mainloop;
const NotificationDaemon = imports.ui.notificationDaemon;
const Config = imports.misc.config
const MessageTray = imports.ui.messageTray;

let APP_NAME = "SystemTray";
let trayAddedId = 0;
let orig_onTrayIconAdded;

let trayRemovedId = 0;
let orig_onTrayIconRemoved;

let tray = null;
let orig_getSource = null;
let icons = [];

let DEBUG = false;


// this value is hardcoded into the display manager
let PANEL_ICON_SIZE = 24;

// Workarounds...
let currentArray = Config.PACKAGE_VERSION.split('.');
if (currentArray[0] == 3 && currentArray[1] < 5) {
    // Gnome Shell 3.3 or 3.4
    PANEL_ICON_SIZE = MessageTray.Source.prototype.ICON_SIZE;
} else if (currentArray[0] == 3 && currentArray[1] < 7) {
    // Gnome Shell 3.5 or 3.6
    PANEL_ICON_SIZE = MessageTray.NOTIFICATION_ICON_SIZE;
} else {
    // Gnome Shell 3.7 and higher
    PANEL_ICON_SIZE = MessageTray.Source.prototype.SOURCE_ICON_SIZE;
}



function init() {
    if (Main.legacyTray) {
        global.log("Legacy tray")
        tray = Main.legacyTray;
        tray.STANDARD_TRAY_ICON_IMPLEMENTATIONS = imports.ui.legacyTray.STANDARD_TRAY_ICON_IMPLEMENTATIONS;
    }
    else if (Main.notificationDaemon._fdoNotificationDaemon) {
        global.log("FDO Notification tray")
        tray = Main.notificationDaemon._fdoNotificationDaemon;
        orig_getSource = Lang.bind(tray, NotificationDaemon.FdoNotificationDaemon.prototype._getSource);
    }
    else {
        global.log("Notification tray")
        tray = Main.notificationDaemon;
        orig_getSource = Lang.bind(tray, NotificationDaemon.NotificationDaemon.prototype._getSource);
    }
}

function enable() {
    GLib.idle_add(GLib.PRIORITY_LOW, installHook);
}

function disable() {
    if (trayAddedId != 0) {
        tray._trayManager.disconnect(trayAddedId);
        trayAddedId = 0;
    }

    if (trayRemovedId != 0) {
        tray._trayManager.disconnect(trayRemovedId);
        trayRemovedId = 0;
    }

    tray._trayIconAddedId = tray._trayManager.connect('tray-icon-added', orig_onTrayIconAdded);
    tray._trayIconRemovedId = tray._trayManager.connect('tray-icon-removed', orig_onTrayIconRemoved);

    tray._getSource = orig_getSource;

    for (let i = 0; i < icons.length; i++) {
        let icon = icons[i];
        let parent = icon.get_parent();
        if (icon._clicked) {
            icon.disconnect(icon._clicked);
        }
        icon._clicked = undefined;

        if (icon._proxyAlloc) {
            Main.panel._rightBox.disconnect(icon._proxyAlloc);
        }

        icon._clickProxy.destroy();

        parent.remove_actor(icon);
        parent.destroy();
        tray._onTrayIconAdded(tray, icon);
    }

    icons = [];
}



function installHook() {
    if (DEBUG) {
        global.log("Installing hook")
    }

    // disable the "normal" method of adding icons
    if (tray._trayIconAddedId) {
        tray._trayManager.disconnect(tray._trayIconAddedId);
    }

    if (tray._trayIconRemovedId) {
        tray._trayManager.disconnect(tray._trayIconRemovedId);
    }


    // save the original method
    orig_onTrayIconAdded = Lang.bind(tray, tray._onTrayIconAdded);
    orig_onTrayIconRemoved = Lang.bind(tray, tray._onTrayIconRemoved)

    // add our hook. If our icon doesn't have our specific title, it calls the original method
    trayAddedId = tray._trayManager.connect('tray-icon-added', onTrayIconAdded);
    trayRemovedId = tray._trayManager.connect('tray-icon-removed', onTrayIconRemoved);

    tray._getSource = getSourceHook;

    // move icons to top
    let toDestroy = [];
    if (tray._sources) {
         if (DEBUG) {
             global.log("Adding to sources")
         }
         for (let i = 0; i < tray._sources.length; i++) {
             let source = tray._sources[i];

             if (!source.trayIcon) {
                 continue;
             }

             let icon = source.trayIcon;

             if (icon.title !== APP_NAME) {
                 continue;
             }

             let parent = icon.get_parent();
             parent.remove_actor(icon);

             onTrayIconAdded(this, icon);
             toDestroy.push(source);
         }
    }
    else {
        if (DEBUG) {
            global.log("Adding to children")
        }
        for (let i = 0; i < tray._iconBox.get_n_children(); i++) {
            let button = tray._iconBox.get_child_at_index(0);
            let icon = button.child;

            if (icon.title !== APP_NAME) {
                continue;
            }

            button.remove_actor(icon);
            onTrayIconAdded(this, icon);

            toDestroy.push(button);
            break;
        }
    }

    for (let i = 0; i < toDestroy.length; i++) {
        toDestroy[i].destroy();
    }
}

function getSourceHook (title, pid, ndata, sender, trayIcon) {
    if (trayIcon && title === APP_NAME) {
        if (DEBUG) {
            global.log("create source");
        }
        onTrayIconAdded(this, trayIcon);
        return null;
    }

    return orig_getSource(title, pid, ndata, sender, trayIcon);
}

// this is the hook that lets us only add ourselves.
function onTrayIconAdded(o, icon) {
    if (DEBUG) {
        global.log("adding tray icon 1 " + icon.title);
    }

    let wmClass = icon.wm_class ? icon.wm_class.toLowerCase() : '';
    if (tray.STANDARD_TRAY_ICON_IMPLEMENTATIONS[wmClass] !== undefined) {
        if (DEBUG) {
            global.log("Cannot add tray icon, invalid implementation");
        }
        return;
    }

    if (icon.title !== APP_NAME) {
        if (DEBUG) {
            global.log("Cannot add tray icon, wrong name: " + icon.title + "   Expecting: " + APP_NAME);
        }
        orig_onTrayIconAdded(o, icon);
        return;
    }

    if (DEBUG) {
        global.log("adding tray icon 2 " + icon.title);
    }

    // An empty ButtonBox will still display padding, so create it without visibility.
    let buttonBox = new PanelMenu.ButtonBox({visible: false});
    let boxActor = buttonBox.actor;
    let parent = boxActor.get_parent();


    // size
    let scaleFactor = St.ThemeContext.get_for_stage(global.stage).scale_factor;
    let iconSize = PANEL_ICON_SIZE * scaleFactor;
   // icon.get_parent().set_size(iconSize, iconSize);
    icon.set_size(iconSize, iconSize);
    boxActor.add_actor(icon);


    // Reactive actors will receive events.
    icon.set_reactive(true);
    icon.reactive = true;

    if (parent) {
        // remove from the (if present) "collapsy tab icon thing"
        parent.remove_actor(boxActor);
    }

    // setup proxy for handling click notifications, make it a little larger than the icon
    let clickProxy = new St.Bin({ width: iconSize + 4, height: iconSize + 4});
    clickProxy.set_reactive(true);
    clickProxy.reactive = true;

    icon._proxyAlloc = Main.panel._rightBox.connect('allocation-changed', function() {
        Meta.later_add(Meta.LaterType.BEFORE_REDRAW, function() {
            let [x, y] = icon.get_transformed_position();
            // need to offset the proxy, so the icon is centered inside the click handler
            clickProxy.set_position(x - 2, y);
        });
    });

    icon.connect("destroy", function() {
        Main.panel._rightBox.disconnect(icon._proxyAlloc);
        icon.clear_effects();
        clickProxy.destroy();
    });

    clickProxy.connect('button-release-event', function(actor, event) {
        icon.click(event);
    });

    icon._clickProxy = clickProxy;


    Main.uiGroup.add_actor(clickProxy);

    // add the box to the right panel, always at position 0
    Main.panel._rightBox.insert_child_at_index(boxActor, 0);

    GLib.timeout_add(GLib.PRIORITY_DEFAULT, 500, Lang.bind(this, function()
    {
        clickProxy.visible = true;
        boxActor.visible = true;
        return GLib.SOURCE_REMOVE;
    }));

    icons.push(icon);
}

function onTrayIconRemoved(o, icon) {
    if (DEBUG) {
        global.log("removing tray icon " + icon.title);
    }

    if (icon.title !== APP_NAME) {
        orig_onTrayIconRemoved(o, icon);
        return;
    }

    let parent = icon.get_parent();

    if (parent) {
        parent.destroy();
    }

    icon.destroy();
    icons.splice(icons.indexOf(icon), 1);
}

