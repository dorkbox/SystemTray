// -*- mode: js; js-indent-level: 4; indent-tabs-mode: nil -*-
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

let APP_NAME = "SystemTray@Dorkbox";
let trayAddedId = 0;
let orig_onTrayIconAdded;

let trayRemovedId = 0;
let orig_onTrayIconRemoved;

let orig_getSource = null;
let icons = [];

let notificationDaemon;


// this value is hardcoded into the display manager
const PANEL_ICON_SIZE = 24;

function init() {
    if (Main.legacyTray) {
        notificationDaemon = Main.legacyTray;
        NotificationDaemon.STANDARD_TRAY_ICON_IMPLEMENTATIONS = imports.ui.legacyTray.STANDARD_TRAY_ICON_IMPLEMENTATIONS;
    }
    else if (Main.notificationDaemon._fdoNotificationDaemon) {
        notificationDaemon = Main.notificationDaemon._fdoNotificationDaemon;
        orig_getSource = Lang.bind(notificationDaemon, NotificationDaemon.FdoNotificationDaemon.prototype._getSource);
    }
    else {
        notificationDaemon = Main.notificationDaemon;
        orig_getSource = Lang.bind(notificationDaemon, NotificationDaemon.NotificationDaemon.prototype._getSource);
    }
}

function enable() {
    GLib.idle_add(GLib.PRIORITY_LOW, installHook);
}

function disable() {
    if (trayAddedId != 0) {
        notificationDaemon._trayManager.disconnect(trayAddedId);
        trayAddedId = 0;
    }

    if (trayRemovedId != 0) {
        notificationDaemon._trayManager.disconnect(trayRemovedId);
        trayRemovedId = 0;
    }

    notificationDaemon._trayIconAddedId = notificationDaemon._trayManager.connect('tray-icon-added', orig_onTrayIconAdded);
    notificationDaemon._trayIconRemovedId = notificationDaemon._trayManager.connect('tray-icon-removed', orig_onTrayIconRemoved);

    notificationDaemon._getSource = orig_getSource;

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
        notificationDaemon._onTrayIconAdded(notificationDaemon, icon);
    }

    icons = [];
}



function installHook() {
    //global.log("Installing hook")

    // disable the "normal" method of adding icons
    notificationDaemon._trayManager.disconnect(notificationDaemon._trayIconAddedId);
    notificationDaemon._trayManager.disconnect(notificationDaemon._trayIconRemovedId);

    // save the original method
    orig_onTrayIconAdded = Lang.bind(notificationDaemon, notificationDaemon._onTrayIconAdded);
    orig_onTrayIconRemoved = Lang.bind(notificationDaemon, notificationDaemon._onTrayIconRemoved)

    // add our hook. If our icon doesn't have our specific title, it calls the original method
    trayAddedId = notificationDaemon._trayManager.connect('tray-icon-added', onTrayIconAdded);
    trayRemovedId = notificationDaemon._trayManager.connect('tray-icon-removed', onTrayIconRemoved);

    notificationDaemon._getSource = getSourceHook;

    // move icons to top
    let toDestroy = [];
    if (notificationDaemon._sources) {
        for (let i = 0; i < notificationDaemon._sources.length; i++) {
            let source = notificationDaemon._sources[i];

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
        for (let i = 0; i < notificationDaemon._iconBox.get_n_children(); i++) {
            let button = notificationDaemon._iconBox.get_child_at_index(i);
            let icon = button.child;

            if (icon.title !== APP_NAME) {
                continue;
            }

            button.remove_actor(icon);
            onTrayIconAdded(this, icon);

            toDestroy.push(button);
        }
    }

    for (let i = 0; i < toDestroy.length; i++) {
        toDestroy[i].destroy();
    }
}

function getSourceHook (title, pid, ndata, sender, trayIcon) {
    if (trayIcon && title === APP_NAME) {
        //global.log("create source");
        onTrayIconAdded(this, trayIcon);
        return null;
    }

    return orig_getSource(title, pid, ndata, sender, trayIcon);
}

// this is the hook that lets us only add ourselves.
function onTrayIconAdded(o, icon) {
    //global.log("adding tray icon 1 " + icon.title);

    let wmClass = icon.wm_class ? icon.wm_class.toLowerCase() : '';
    if (NotificationDaemon.STANDARD_TRAY_ICON_IMPLEMENTATIONS[wmClass] !== undefined) {
        return;
    }

    if (icon.title !== APP_NAME) {
        orig_onTrayIconAdded(o, icon);
        return;
    }

    //global.log("adding tray icon 2 " + icon.title);

    let buttonBox = new PanelMenu.ButtonBox();
    let box = buttonBox.actor;
    let parent = box.get_parent();

    let scaleFactor = St.ThemeContext.get_for_stage(global.stage).scale_factor;
    let iconSize = PANEL_ICON_SIZE * scaleFactor;

    icon.set_size(iconSize, iconSize);
    box.add_actor(icon);

    // Reactive actors will receive events.
    icon.set_reactive(true);

    if (parent) {
        // remove from the (if present) "collapsy tab icon thing"
        parent.remove_actor(box);
    }

    // setup proxy for handling click notifications, make it a little larger than the icon
    let clickProxy = new St.Bin({ width: iconSize + 4, height: iconSize + 4});
    clickProxy.set_reactive(true);

    icon._proxyAlloc = Main.panel._rightBox.connect('allocation-changed', function() {
        Meta.later_add(Meta.LaterType.BEFORE_REDRAW, function() {
            let [x, y] = icon.get_transformed_position();
            // need to offset the proxy, so the icon is centered inside the click handler
            clickProxy.set_position(x - 2, y);
        });
    });

    icon.connect("destroy", function() {
        Main.panel._rightBox.disconnect(icon._proxyAlloc);
        clickProxy.destroy();
    });

    clickProxy.connect('button-release-event', function(actor, event) {
        icon.click(event);
    });

    icon._clickProxy = clickProxy;


    Main.uiGroup.add_actor(clickProxy);

    // add the box to the right panel, always at position 0
    Main.panel._rightBox.insert_child_at_index(box, 0);

    icons.push(icon);
}

function onTrayIconRemoved(o, icon) {
    //global.log("removing tray icon " + icon.title);

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

