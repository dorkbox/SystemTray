/*
 * Copyright 2017 dorkbox, llc
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
package dorkbox.systemTray.nativeUI;


import static com.sun.jna.platform.win32.WinDef.HBITMAP;
import static com.sun.jna.platform.win32.WinDef.HDC;
import static com.sun.jna.platform.win32.WinDef.HFONT;
import static com.sun.jna.platform.win32.WinDef.HMENU;
import static com.sun.jna.platform.win32.WinDef.HWND;
import static com.sun.jna.platform.win32.WinDef.LPARAM;
import static com.sun.jna.platform.win32.WinDef.POINT;
import static com.sun.jna.platform.win32.WinDef.RECT;
import static com.sun.jna.platform.win32.WinDef.WPARAM;
import static com.sun.jna.platform.win32.WinNT.HANDLE;
import static com.sun.jna.platform.win32.WinUser.AC_SRC_ALPHA;
import static com.sun.jna.platform.win32.WinUser.AC_SRC_OVER;
import static com.sun.jna.platform.win32.WinUser.SIZE;
import static com.sun.jna.platform.win32.WinUser.SM_CYMENUCHECK;
import static dorkbox.systemTray.jna.windows.WindowsEventDispatch.MF_POPUP;
import static dorkbox.systemTray.jna.windows.WindowsEventDispatch.WM_COMMAND;
import static dorkbox.systemTray.jna.windows.WindowsEventDispatch.WM_DRAWITEM;
import static dorkbox.systemTray.jna.windows.WindowsEventDispatch.WM_MEASUREITEM;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.jna.windows.GDI32;
import dorkbox.systemTray.jna.windows.GetLastErrorException;
import dorkbox.systemTray.jna.windows.Listener;
import dorkbox.systemTray.jna.windows.MsImg32;
import dorkbox.systemTray.jna.windows.User32;
import dorkbox.systemTray.jna.windows.WindowsEventDispatch;
import dorkbox.systemTray.jna.windows.structs.BLENDFUNCTION;
import dorkbox.systemTray.jna.windows.structs.DRAWITEMSTRUCT;
import dorkbox.systemTray.jna.windows.structs.MEASUREITEMSTRUCT;
import dorkbox.systemTray.jna.windows.structs.NONCLIENTMETRICS;
import dorkbox.systemTray.peer.MenuPeer;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
class WindowsMenu extends WindowsBaseMenuItem implements MenuPeer {

    volatile HMENU _nativeMenu;  // must ONLY be created at the end of delete!
    private final WindowsMenu parent;

    private final Listener menuItemListener;
    private final Listener menuItemMeasureListener;
    private final Listener menuItemDrawListener;

    public static final int WM_NULL = 0x0000;

    public static final int VK_ESCAPE = 0x1B;
    public static final int WM_KEYDOWN = 0x0100;

    public static final int TPM_RECURSE = 0x0001;
    public static final int TPM_RIGHTBUTTON = 0x0002;

    public static final int MFT_OWNERDRAW = 256;


    private static final int SPACE_ICONS = 2;

    // have to make sure no other methods can call obliterate, delete, or create menu once it's already started
    private AtomicBoolean obliterateInProgress = new AtomicBoolean(false);

    // this is a list (that mirrors the actual list) BECAUSE we have to create/delete the entire menu in Windows every time something is changed
    private final List<WindowsBaseMenuItem> menuEntries = new ArrayList<WindowsBaseMenuItem>();


    // called by the system tray constructors
    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    WindowsMenu() {
        super(0);
        this.parent = null;

        // Register drawing menu items, etc
        menuItemListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                int position = wParam.intValue() & 0xff;
                WindowsBaseMenuItem item = menuEntries.get(position);
                item.fireCallback();
            }
        };

        menuItemMeasureListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                MEASUREITEMSTRUCT ms = new MEASUREITEMSTRUCT(new Pointer(lParam.longValue()));

                int position = ms.itemData.intValue();
                WindowsBaseMenuItem item = menuEntries.get(position);

                SIZE size = measureItem(hWnd, item);
                ms.itemWidth = size.cx;
                ms.itemHeight = size.cy;
                ms.write();
            }
        };


        menuItemDrawListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                DRAWITEMSTRUCT di = new DRAWITEMSTRUCT(new Pointer(lParam.longValue()));

                int position = di.itemData.intValue();
                WindowsBaseMenuItem item = menuEntries.get(position);

                drawItem(item, di.hDC, di.rcItem, di.itemState);
            }
        };

        WindowsEventDispatch.addListener(WM_COMMAND, menuItemListener);
        WindowsEventDispatch.addListener(WM_MEASUREITEM, menuItemMeasureListener);
        WindowsEventDispatch.addListener(WM_DRAWITEM, menuItemDrawListener);
    }

    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    private
    WindowsMenu(final WindowsMenu parent, final int index) {
        super(index); // is what is added to the parent menu (so images work)
        this.parent = parent;

        // Register drawing menu items, etc
        menuItemListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                int position = wParam.intValue() & 0xff;
                WindowsBaseMenuItem item = menuEntries.get(position);
                item.fireCallback();
            }
        };

        menuItemMeasureListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                MEASUREITEMSTRUCT ms = new MEASUREITEMSTRUCT(new Pointer(lParam.longValue()));

                int position = ms.itemData.intValue();
                WindowsBaseMenuItem item = menuEntries.get(position);

                SIZE size = measureItem(hWnd, item);
                ms.itemWidth = size.cx;
                ms.itemHeight = size.cy;
                ms.write();
            }
        };

        menuItemDrawListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                DRAWITEMSTRUCT di = new DRAWITEMSTRUCT(new Pointer(lParam.longValue()));

                int position = di.itemData.intValue();
                WindowsBaseMenuItem item = menuEntries.get(position);

                drawItem(item, di.hDC, di.rcItem, di.itemState);
            }
        };

        WindowsEventDispatch.addListener(WM_COMMAND, menuItemListener);
        WindowsEventDispatch.addListener(WM_MEASUREITEM, menuItemMeasureListener);
        WindowsEventDispatch.addListener(WM_DRAWITEM, menuItemDrawListener);
    }


    WindowsMenu getParent() {
        return parent;
    }

    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void deleteMenu() {
        if (obliterateInProgress.get()) {
            return;
        }

        if (_nativeMenu != null) {
            // have to work in reverse so the index is preserved
            for (int i = menuEntries.size()-1; i >= 0; i--) {
                final WindowsBaseMenuItem menuEntry__ = menuEntries.get(i);
                menuEntry__.onDeleteMenu(_nativeMenu);
            }

            if (!User32.IMPL.DestroyMenu(_nativeMenu)) {
                throw new GetLastErrorException();
            }
        }

        if (parent != null) {
            parent.deleteMenu();
        }

        // makes a new one
        this._nativeMenu = User32.IMPL.CreatePopupMenu();

        // binds sub-menu to entry (if it exists! it does not for the root menu)
        if (parent != null) {
            // get around windows crap (transfer handle to decimal)
            int handle = (int) Pointer.nativeValue(_nativeMenu.getPointer());

            if (!User32.IMPL.AppendMenu(getParent()._nativeMenu, MF_POPUP | MFT_OWNERDRAW, handle, null)) {
                throw new GetLastErrorException();
            }
        }
    }

    /**
     * some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
     *
     * To work around this issue, we destroy then recreate the menu every time something is changed.
     *
     * ALWAYS CALLED ON THE EDT
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void createMenu() {
        if (obliterateInProgress.get()) {
            return;
        }

        if (parent != null) {
            parent.createMenu();
        }

        // now add back other menu entries
        boolean hasImages = false;

        for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
            final WindowsBaseMenuItem menuEntry__ = menuEntries.get(i);
            hasImages |= menuEntry__.hasImage();
        }

        for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
            // the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
            final WindowsBaseMenuItem menuEntry__ = menuEntries.get(i);
            menuEntry__.onCreateMenu(_nativeMenu, hasImages);

            if (menuEntry__ instanceof WindowsMenu) {
                WindowsMenu subMenu = (WindowsMenu) menuEntry__;
                if (subMenu.getParent() != WindowsMenu.this) {
                    // we don't want to "createMenu" on our sub-menu that is assigned to us directly, as they are already doing it
                    subMenu.createMenu();
                }
            }
        }

//        Gtk.gtk_widget_show_all(_nativeMenu);    // necessary to guarantee widget is visible (doesn't always show_all for all children)
//        onMenuAdded(_nativeMenu); // not needed for windows
    }

    /**
     * Completely obliterates the menu, no possible way to reconstruct it.
     *
     * ALWAYS CALLED ON THE EDT
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void obliterateMenu() {
        if (_nativeMenu != null && !obliterateInProgress.get()) {
            obliterateInProgress.set(true);

            // have to remove all other menu entries

            // a copy is made because sub-menus remove themselves from parents when .remove() is called. If we don't
            // do this, errors will be had because indices don't line up anymore.
            ArrayList<WindowsBaseMenuItem> menuEntriesCopy = new ArrayList<WindowsBaseMenuItem>(menuEntries);
            menuEntries.clear();

            // have to work in reverse so the index is preserved
            for (int i = menuEntriesCopy.size()-1; i >= 0; i--) {
                final WindowsBaseMenuItem menuEntry__ = menuEntriesCopy.get(i);
                menuEntry__.onDeleteMenu(_nativeMenu);
            }
            menuEntriesCopy.clear();

            if (!User32.IMPL.DestroyMenu(_nativeMenu)) {
                throw new GetLastErrorException();
            }

            _nativeMenu = null;

            obliterateInProgress.set(false);
        }
    }


    @Override
    public
    void add(final Menu parentMenu, final Entry entry, int index) {
        deleteMenu();

        if (entry instanceof Menu) {
//            WindowsMenu item = new WindowsMenu(WindowsMenu.this, index);
//            menuEntries.add(index, item);
//            ((Menu) entry).bind(item, parentMenu, parentMenu.getSystemTray());
        }
        else if (entry instanceof Separator) {
//            WindowsMenuItemSeparator item = new WindowsMenuItemSeparator(WindowsMenu.this);
//            menuEntries.add(index, item);
//            entry.bind(item, parentMenu, parentMenu.getSystemTray());
        }
        else if (entry instanceof Checkbox) {
//            WindowsMenuItemCheckbox item = new WindowsMenuItemCheckbox(WindowsMenu.this);
//            menuEntries.add(index, item);
//            ((Checkbox) entry).bind(item, parentMenu, parentMenu.getSystemTray());
        }
        else if (entry instanceof Status) {
//            WindowsMenuItemStatus item = new WindowsMenuItemStatus(WindowsMenu.this);
//            menuEntries.add(index, item);
//            ((Status) entry).bind(item, parentMenu, parentMenu.getSystemTray());
        }
        else if (entry instanceof MenuItem) {
            WindowsMenuItem item = new WindowsMenuItem(WindowsMenu.this, index);
            menuEntries.add(index, item);
            ((MenuItem) entry).bind(item, parentMenu, parentMenu.getSystemTray());
        }

        createMenu();
    }

    // is overridden in tray impl
    @Override
    public
    void setImage(final MenuItem menuItem) {
        super.setImage(menuItem.getImage());
    }

    // is overridden in tray impl
    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        super.setEnabled(menuItem.getEnabled());
    }

    // is overridden in tray impl
    @Override
    public
    void setText(final MenuItem menuItem) {
        super.setText(menuItem.getText());
    }

    @Override
    public
    void setCallback(final MenuItem menuItem) {
        // can't have a callback for menus!
    }

    // is overridden in tray impl
    @Override
    public
    void setShortcut(final MenuItem menuItem) {
//        // yikes...
//        final int vKey = SwingUtil.getVirtualKey(menuItem.getShortcut());
//
//        SwingUtil.invokeLater(new Runnable() {
//            @Override
//            public
//            void run() {
//                _native.setShortcut(new MenuShortcut(vKey));
//            }
//        });
    }

    /**
     * called when a child removes itself from the parent menu. Does not work for sub-menus
     *
     * ALWAYS CALLED ON THE EDT
     */
    public
    void remove(final WindowsBaseMenuItem item) {
        menuEntries.remove(item);

        // have to rebuild the menu now...
        deleteMenu();  // must be on EDT
        createMenu();  // must be on EDT
    }

    // a child will always remove itself from the parent.
    @Override
    public
    void remove() {
        WindowsMenu parent = getParent();

        if (parent != null) {
            // have to remove from the  parent.menuEntries first
            parent.menuEntries.remove(WindowsMenu.this);
        }

        // delete all of the children of this submenu (must happen before the menuEntry is removed)
        obliterateMenu();

        if (parent != null) {
            // have to rebuild the menu now...
            parent.deleteMenu();  // must be on EDT
            parent.createMenu();  // must be on EDT
        }
    }


    void showContextMenu(final POINT position) {
        HWND mainHwnd = WindowsEventDispatch.get();
        User32.IMPL.SetForegroundWindow(mainHwnd);

        // TrackPopupMenu blocks until popup menu is closed
        if (!User32.IMPL.TrackPopupMenu(_nativeMenu, TPM_RIGHTBUTTON, position.x, position.y, 0, mainHwnd, null)) {
            // Popup menu already active
            if (com.sun.jna.platform.win32.Kernel32.INSTANCE.GetLastError() == 0x000005a6) {
                HWND hWnd = null;
                while (true) {
                    // "#32768" - Name of the popup menu class
                    hWnd = User32.IMPL.FindWindowEx(null, hWnd, "#32768", null);

                    if (hWnd == null) {
                        break;
                    }

                    // close the previous popup menu
                    User32.IMPL.SendMessage(hWnd, WM_KEYDOWN, new WPARAM(VK_ESCAPE), null);
                }

                return;
            } else {
                throw new GetLastErrorException();
            }
        }

        WPARAM wparam = new WPARAM(0);
        LPARAM lparam = new LPARAM(0);
        User32.IMPL.PostMessage(mainHwnd, WM_NULL, wparam, lparam);
    }


//    // menus have to be cleared before each render.
//    void clearMenus() {
//        if (_native != null) {
//            if (!User32.DestroyMenu(_native)) {
//                System.err.println("PROBLEM");
////            throw new GetLastErrorException();
//            }
//            _native = null;
//        }
//        for (WindowsBaseMenuItem m : menuEntries) {
//            m.close();
//        }
//
//        hMenusIDs.clear();
//    }

//    void updateMenus() {
//        clearMenus();
//
//        HMENU hmenu = User32.CreatePopupMenu();
//        this._native = hmenu;
//
//        for (int i = 0; i < menu.getComponentCount(); i++) {
//            Component e = menu.getComponent(i);
//
//            if (e instanceof JMenu) {
//                JMenu sub = (JMenu) e;
//                HMENU hsub = createSubmenu(sub);
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(sub));
//
//                // you know, the usual windows tricks (transfer handle to
//                // decimal)
//                int handle = (int) Pointer.nativeValue(hsub.getPointer());
//
//                if (!User32.AppendMenu(hmenu, MF_POPUP | MFT_OWNERDRAW, handle, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, handle, false, mi))
//                    throw new GetLastErrorException();
//
//                mi.dwItemData = new ULONG_PTR(nID);
//                mi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, handle, false, mi))
//                    throw new GetLastErrorException();
//
//
//            } else if (e instanceof JCheckBoxMenuItem) {
//                JCheckBoxMenuItem ch = (JCheckBoxMenuItem) e;
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(ch));
//
//                if (!User32.AppendMenu(hmenu, MFT_OWNERDRAW, nID, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mmi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//                mmi.dwItemData = new ULONG_PTR(nID);
//                mmi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//            } else if (e instanceof JMenuItem) {
//                JMenuItem mi = (JMenuItem) e;
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(mi));
//
//                if (!User32.AppendMenu(hmenu, MFT_OWNERDRAW, nID, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mmi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//                mmi.dwItemData = new ULONG_PTR(nID);
//                mmi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//            }
//
//            if (e instanceof JPopupMenu.Separator) {
//                if (!User32.AppendMenu(hmenu, MF_SEPARATOR, 0, null))
//                    throw new GetLastErrorException();
//            }
//        }
//    }

//    HMENU createSubmenu(JMenu menu) {
//        HMENU hmenu = User32.CreatePopupMenu();
//        // seems like you dont have to free this menu, since it already attached
//        // to main HMENU handler
//
//        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
//            Component e = menu.getMenuComponent(i);
//
//            if (e instanceof JMenu) {
//                JMenu sub = (JMenu) e;
//                HMENU hsub = createSubmenu(sub);
//
//                // you know, the usual windows tricks (transfer handle to
//                // decimal)
//                int handle = (int) Pointer.nativeValue(hsub.getPointer());
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(sub));
//
//                if (!User32.AppendMenu(hmenu, MF_POPUP | MFT_OWNERDRAW, handle, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, handle, false, mi))
//                    throw new GetLastErrorException();
//                mi.dwItemData = new ULONG_PTR(nID);
//                mi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, handle, false, mi))
//                    throw new GetLastErrorException();
//            } else if (e instanceof JCheckBoxMenuItem) {
//                JCheckBoxMenuItem ch = (JCheckBoxMenuItem) e;
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(ch));
//
//                if (!User32.AppendMenu(hmenu, MFT_OWNERDRAW, nID, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, nID, false, mi))
//                    throw new GetLastErrorException();
//                mi.dwItemData = new ULONG_PTR(nID);
//                mi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, nID, false, mi))
//                    throw new GetLastErrorException();
//            } else if (e instanceof JMenuItem) {
//                JMenuItem mi = (JMenuItem) e;
//
//                int nID = menuEntries.size();
//                menuEntries.add(new WindowsBaseMenuItem(mi));
//
//                if (!User32.AppendMenu(hmenu, MFT_OWNERDRAW, nID, null))
//                    throw new GetLastErrorException();
//
//                MENUITEMINFO mmi = new MENUITEMINFO();
//                if (!User32.GetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//                mmi.dwItemData = new ULONG_PTR(nID);
//                mmi.fMask |= MENUITEMINFO.MIIM_DATA;
//                if (!User32.SetMenuItemInfo(hmenu, nID, false, mmi))
//                    throw new GetLastErrorException();
//            }
//
//            if (e instanceof JPopupMenu.Separator) {
//                if (!User32.AppendMenu(hmenu, MF_SEPARATOR, 0, null))
//                    throw new GetLastErrorException();
//            }
//        }
//
//        return hmenu;
//    }


    private static
    void drawItem(WindowsBaseMenuItem item, HDC hDC, RECT rcItem, int itemState) {
        if (!item.enabled) {
            GDI32.SetTextColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_GRAYTEXT));
            GDI32.SetBkColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_MENU));
        }
        else if ((itemState & DRAWITEMSTRUCT.ODS_SELECTED) == DRAWITEMSTRUCT.ODS_SELECTED) {
            GDI32.SetTextColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_HIGHLIGHTTEXT));
            GDI32.SetBkColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_HIGHLIGHT));
        }
        else {
            GDI32.SetTextColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_MENUTEXT));
            GDI32.SetBkColor(hDC, User32.IMPL.GetSysColor(User32.COLOR_MENU));
        }

        int x = rcItem.left;
        int y = rcItem.top;

        x += (getSystemMenuImageSize() + SPACE_ICONS) * 2;

        GDI32.SelectObject(hDC, createSystemMenuFont());
        GDI32.ExtTextOut(hDC,
                         x,
                         y,
                         GDI32.ETO_OPAQUE,
                         rcItem,
                         item.text,
                         item.text.length(),
                         null);

        x = rcItem.left;

//        if (item.item instanceof JCheckBoxMenuItem) {
//            JCheckBoxMenuItem cc = (JCheckBoxMenuItem) item.item;
//            if (cc.getState()) {
//                // draw checkmark image
////                drawHBITMAP(hbitmapChecked, x, y, hbitmapChecked.getImage().getWidth(),
////                            hbitmapChecked.getImage().getHeight(), hDC);
//            }
//            else {
//                // draw blank checkmark image
////                drawHBITMAP(hbitmapUnchecked, x, y, hbitmapUnchecked.getImage().getWidth(),
////                            hbitmapUnchecked.getImage().getHeight(), hDC);
//            }
//        }

        x += getSystemMenuImageSize() + SPACE_ICONS;

        if (item.hbitmapWrapImage != null) {
            drawHBITMAP(item.hbitmapWrapImage,
                        x,
                        y,
                        item.hbitmapWrapImage.getImage().getWidth(),
                        item.hbitmapWrapImage.getImage().getHeight(),
                        hDC);
        }
    }

    public static
    int getSystemMenuImageSize() {
        // get's the height of the default (small) checkmark
        return User32.IMPL.GetSystemMetrics(SM_CYMENUCHECK);
    }

    static
    HFONT createSystemMenuFont() {
        NONCLIENTMETRICS nm = new NONCLIENTMETRICS();

        User32.IMPL.SystemParametersInfo(User32.SPI_GETNONCLIENTMETRICS, 0, nm, 0);
        return GDI32.CreateFontIndirect(nm.lfMenuFont);
    }

    private static
    void drawHBITMAP(HBITMAP hbm, int x, int y, int cx, int cy, HDC hdcDst) {
        HDC hdcSrc = GDI32.CreateCompatibleDC(hdcDst);
        HANDLE old = GDI32.SelectObject(hdcSrc, hbm);

        BLENDFUNCTION.ByValue bld = new BLENDFUNCTION.ByValue();
        bld.BlendOp = AC_SRC_OVER;
        bld.BlendFlags = 0;
        bld.SourceConstantAlpha = (byte) 255;
        bld.AlphaFormat = AC_SRC_ALPHA;

        if (!MsImg32.AlphaBlend(hdcDst, x, y, cx, cy, hdcSrc, 0, 0, cx, cy, bld)) {
            throw new GetLastErrorException();
        }

        GDI32.SelectObject(hdcSrc, old);

        if (!GDI32.DeleteDC(hdcSrc)) {
            throw new GetLastErrorException();
        }
    }

    private static
    SIZE measureItem(HWND hWnd, WindowsBaseMenuItem item) {
        HDC hdc = User32.IMPL.GetDC(hWnd);
        HANDLE hfntOld = GDI32.SelectObject(hdc, createSystemMenuFont());
        SIZE size = new SIZE();
        if (!GDI32.GetTextExtentPoint32(hdc,
                                        item.text,
                                        item.text.length(),
                                        size)) {
            throw new GetLastErrorException();
        }
        GDI32.SelectObject(hdc, hfntOld);
        User32.IMPL.ReleaseDC(hWnd, hdc);

        size.cx += (getSystemMenuImageSize() + SPACE_ICONS) * 2;

        return size;
    }
}
