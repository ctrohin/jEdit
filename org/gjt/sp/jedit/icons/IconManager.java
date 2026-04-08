/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.icons;

import io.vavr.control.Try;
import jiconfont.IconCode;
import jiconfont.icons.GoogleMaterialDesignIcons;
import jiconfont.swing.IconFontSwing;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.gui.components.CachedDynamicMultiResolution;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

record IconAndSize(IconCode icon, int size) {
}

public class IconManager {

    private static final String defaultIconPath = "jeditresource:/org/gjt/sp/jedit/icons/themes/";
    private static final Map<String, String> deprecatedIcons = new HashMap<>();
    private static final Map<String, IconAndSize> materialIcons = new HashMap<>();
    private static SoftReference<Map<String, Icon>> iconCache;
    private static String iconPath = "jeditresource:/org/gjt/sp/jedit/icons/themes/";


    //{{{ loadIcon() method
    /**
     * Loads an icon.
     * @param iconName The icon name
     * @return the icon
     * @since jEdit 2.6pre7
     */
    public static Icon loadIcon(String iconName)
    {
        if(iconName == null)
            return null;

        // * Enable old icon naming scheme support
        final var iconString = deprecatedIcons.getOrDefault(iconName, iconName);
        // TODO: Check if it is possible to render a multiresolution image properly
        final IconAndSize materialIcon = materialIcons.get(iconString);
        if (materialIcon == null) {
            Log.log(Log.ERROR, null, "Icon material mapping found: " + iconString);
        }

        // check if there is a cached version first
        Map<String, Icon> cache = null;
        if(iconCache != null)
        {
            cache = iconCache.get();
        }

        if(cache == null)
        {
            cache = new HashMap<>();
            iconCache = new SoftReference<>(cache);
        }

        Icon icon = cache.get(iconString);

        if(icon != null)
            return icon;

        final var finalCache = cache;

        if (materialIcon != null) {
            var imageIcon = new ImageIcon(getMultiRes(materialIcon));
            cache.put(iconString, imageIcon);
            return imageIcon;
        }
        return Try.of(() -> getUrl(iconString))
            .recoverAllAndTry(() -> new URL(defaultIconPath + iconString))
            .onFailure(e -> {
                Log.log(Log.ERROR,GUIUtilities.class, "Icon not found: " + iconString);
                Log.log(Log.ERROR,GUIUtilities.class, e);
            })
            .map(ImageIcon::new)
            .onSuccess((i) -> {
                finalCache.put(iconString, i);
            })
            .getOrElse((ImageIcon)null);

    } //}}}

    private static BaseMultiResolutionImage getMultiRes1(final IconAndSize icon) {
        return new CachedDynamicMultiResolution(icon.icon(), icon.size());
    }

    private static BaseMultiResolutionImage getMultiRes(final IconAndSize icon) {
        int[] res;
        if (resolutions.containsKey(icon.size())) {
            res = resolutions.get(icon.size());
        } else {
            res = getScaledResolutions(icon.size());
            resolutions.put(icon.size(), res);
        }
        Image[] imgs = new Image[res.length];
        for (int i = 0; i < res.length; i++) {
            imgs[i] = IconFontSwing.buildImage(icon.icon(), res[i], UIManager.getColor("ComboBox.selectionBackground"));
        }
        return new BaseMultiResolutionImage(0, imgs);
    }

    private static final HashMap<Integer, int[]> resolutions = new HashMap<>();
    private static final double[] SCALES = {1d, 1.25, 1.5, 2d, 2.5, 3};
    private static int[] getScaledResolutions(int baseSize) {
        return Arrays.stream(SCALES)
            .map(scale -> scale * ((double) baseSize))
            .map(Math::ceil)
            .mapToInt(v -> (int) v)
            .toArray();
    }

    private static IconAndSize ics(IconCode icon, int size) {
        return new IconAndSize(icon, size);
    }
    //{{{ setIconPath() method
    /**
     * Sets the path where jEdit looks for icons.
     * @param iconPath the icon path
     * @since jEdit 4.2pre5
     */
    public static void setIconPath(String iconPath)
    {
        IconManager.iconPath = iconPath;
        iconCache = null;
    } //}}}

    private static URL getUrl(final String iconName) throws Exception {
        if(MiscUtilities.isURL(iconName))
            return new URL(iconName);
        else
            return new URL(iconPath + iconName);
    }

    //{{{ init() method
    public static void init()
    {
        initializeDeprecatedIcons();

        // Load the icon theme but fallback on the old icons
        String theme = jEdit.getProperty("icon-theme", "tango");
        Log.log(Log.DEBUG, GUIUtilities.class, "Icon theme set to: "+theme);
        setIconPath("jeditresource:/org/gjt/sp/jedit/icons/themes/" + theme + '/');
        Log.log(Log.DEBUG, GUIUtilities.class, "Loading icon theme from: "+iconPath);
    } //}}}

    /**
     * Initializes a list of mappings between old icon names and new names
     */
    private static void initializeDeprecatedIcons()
    {
        IconFontSwing.register(GoogleMaterialDesignIcons.getIconFont());
        deprecatedIcons.put("File.png",       "16x16/mimetypes/text-x-generic.png");
        materialIcons.put("16x16/mimetypes/text-x-generic.png", ics(GoogleMaterialDesignIcons.CODE, 12));

        deprecatedIcons.put("Folder.png",     "16x16/places/folder.png");
        materialIcons.put("16x16/places/folder.png", ics(GoogleMaterialDesignIcons.FOLDER, 16));

        deprecatedIcons.put("OpenFolder.png", "16x16/status/folder-open.png");
        materialIcons.put("16x16/status/folder-open.png", ics(GoogleMaterialDesignIcons.FOLDER_OPEN, 16));

        deprecatedIcons.put("OpenFile.png",   "16x16/actions/edit-select-all.png");
        materialIcons.put("16x16/actions/edit-select-all.png", ics(GoogleMaterialDesignIcons.SELECT_ALL, 16));

        deprecatedIcons.put("ReloadSmall.png","16x16/actions/view-refresh.png");
        materialIcons.put("16x16/actions/view-refresh.png", ics(GoogleMaterialDesignIcons.REFRESH, 16));

        deprecatedIcons.put("DriveSmall.png", "16x16/devices/drive-harddisk.png");
        materialIcons.put("16x16/devices/drive-harddisk.png", ics(CustomIconCode.HARD_DISK, 16));

        deprecatedIcons.put("New.png",        "22x22/actions/document-new.png");
        materialIcons.put("22x22/actions/document-new.png", ics(CustomIconCode.NOTE_ADD, 22));
        materialIcons.put("16x16/actions/document-new.png", ics(CustomIconCode.NOTE_ADD, 16));

        deprecatedIcons.put("NewDir.png",     "22x22/actions/folder-new.png");
        materialIcons.put("22x22/actions/folder-new.png", ics(GoogleMaterialDesignIcons.CREATE_NEW_FOLDER, 22));

        deprecatedIcons.put("Reload.png",     "22x22/actions/view-refresh.png");
        materialIcons.put("22x22/actions/view-refresh.png", ics(GoogleMaterialDesignIcons.REFRESH, 22));

        deprecatedIcons.put("Load.png",       "22x22/places/plugins.png");
        materialIcons.put("22x22/places/plugins.png", ics(CustomIconCode.PLUGINS, 22));

        deprecatedIcons.put("Save.png",       "22x22/actions/document-save.png");
        materialIcons.put("22x22/actions/document-save.png", ics(GoogleMaterialDesignIcons.SAVE, 22));
        materialIcons.put("16x16/actions/document-save.png", ics(GoogleMaterialDesignIcons.SAVE, 16));

        deprecatedIcons.put("SaveAs.png",     "22x22/actions/document-save-as.png");
        materialIcons.put("22x22/actions/document-save-as.png", ics(GoogleMaterialDesignIcons.SAVE, 22));
        materialIcons.put("16x16/actions/document-save-as.png", ics(GoogleMaterialDesignIcons.SAVE, 16));

        deprecatedIcons.put("SaveAll.png",    "22x22/actions/document-save-all.png");
        materialIcons.put("22x22/actions/document-save-all.png", ics(GoogleMaterialDesignIcons.SAVE, 22));
        materialIcons.put("16x16/actions/document-save-all.png", ics(GoogleMaterialDesignIcons.SAVE, 16));

        deprecatedIcons.put("Open.png",       "22x22/actions/document-open.png");
        materialIcons.put("22x22/actions/document-open.png", ics(CustomIconCode.FILE_OPEN, 22));
        materialIcons.put("16x16/actions/document-open.png", ics(CustomIconCode.FILE_OPEN, 16));

        deprecatedIcons.put("Print.png",      "22x22/actions/document-print.png");
        materialIcons.put("22x22/actions/document-print.png", ics(GoogleMaterialDesignIcons.PRINT, 22));
        materialIcons.put("16x16/actions/document-print.png", ics(GoogleMaterialDesignIcons.PRINT, 16));

        deprecatedIcons.put("Drive.png",      "22x22/devices/drive-harddisk.png");
        materialIcons.put("22x22/devices/drive-harddisk.png", ics(CustomIconCode.HARD_DISK, 22));

        deprecatedIcons.put("Clear.png",      "22x22/actions/edit-clear.png");
        materialIcons.put("22x22/actions/edit-clear.png", ics(GoogleMaterialDesignIcons.CLEAR, 22));

        deprecatedIcons.put("Run.png",        "22x22/actions/application-run.png");
        deprecatedIcons.put("RunAgain.png",   "22x22/actions/application-run-again.png");
        deprecatedIcons.put("RunToBuffer.png",  "22x22/actions/run-to-buffer.png");
        deprecatedIcons.put("CopyToBuffer.png", "22x22/actions/copy-to-buffer.png");

        deprecatedIcons.put("Plus.png",       "22x22/actions/list-add.png");
        materialIcons.put("22x22/actions/list-add.png", ics(GoogleMaterialDesignIcons.ADD, 22));
        materialIcons.put("16x16/actions/list-add.png", ics(GoogleMaterialDesignIcons.ADD, 16));

        deprecatedIcons.put("Minus.png",      "22x22/actions/list-remove.png");
        materialIcons.put("22x22/actions/list-remove.png", ics(GoogleMaterialDesignIcons.REMOVE, 22));
        materialIcons.put("16x16/actions/list-remove.png", ics(GoogleMaterialDesignIcons.REMOVE, 16));

        deprecatedIcons.put("Find.png",       "22x22/actions/edit-find.png");
        materialIcons.put("22x22/actions/edit-find.png", ics(GoogleMaterialDesignIcons.SEARCH, 22));
        materialIcons.put("16x16/actions/edit-find.png", ics(GoogleMaterialDesignIcons.SEARCH, 16));

        deprecatedIcons.put("FindAgain.png",  "22x22/actions/edit-find-next.png");
        materialIcons.put("22x22/actions/edit-find-next.png", ics(GoogleMaterialDesignIcons.NAVIGATE_NEXT, 22));
        materialIcons.put("16x16/actions/edit-find-next.png", ics(GoogleMaterialDesignIcons.NAVIGATE_NEXT, 16));

        deprecatedIcons.put("FindInDir.png",  "22x22/actions/edit-find-in-folder.png");
        materialIcons.put("22x22/actions/edit-find-in-folder.png", ics(GoogleMaterialDesignIcons.SEARCH, 22));
        materialIcons.put("16x16/actions/edit-find-in-folder.png", ics(GoogleMaterialDesignIcons.SEARCH, 16));

        deprecatedIcons.put("Parse.png",      "22x22/actions/document-reload2.png");
        materialIcons.put("22x22/actions/document-reload2.png", ics(GoogleMaterialDesignIcons.REFRESH, 22));
        materialIcons.put("22x22/actions/document-reload.png", ics(GoogleMaterialDesignIcons.REFRESH, 22));
        materialIcons.put("16x16/actions/document-reload2.png", ics(GoogleMaterialDesignIcons.REFRESH, 16));

        deprecatedIcons.put("Delete.png",     "22x22/actions/edit-delete.png");
        materialIcons.put("22x22/actions/edit-delete.png", ics(GoogleMaterialDesignIcons.DELETE, 22));
        materialIcons.put("16x16/actions/edit-delete.png", ics(GoogleMaterialDesignIcons.DELETE, 16));

        deprecatedIcons.put("Paste.png",      "22x22/actions/edit-paste.png");
        materialIcons.put("22x22/actions/edit-paste.png", ics(GoogleMaterialDesignIcons.CONTENT_PASTE, 22));
        materialIcons.put("16x16/actions/edit-paste.png", ics(GoogleMaterialDesignIcons.CONTENT_PASTE, 16));

        deprecatedIcons.put("Cut.png",        "22x22/actions/edit-cut.png");
        materialIcons.put("22x22/actions/edit-cut.png", ics(GoogleMaterialDesignIcons.CONTENT_CUT, 22));
        materialIcons.put("16x16/actions/edit-cut.png", ics(GoogleMaterialDesignIcons.CONTENT_CUT, 16));

        deprecatedIcons.put("Copy.png",       "22x22/actions/edit-copy.png");
        materialIcons.put("22x22/actions/edit-copy.png", ics(GoogleMaterialDesignIcons.CONTENT_COPY, 22));
        materialIcons.put("16x16/actions/edit-copy.png", ics(GoogleMaterialDesignIcons.CONTENT_COPY, 16));

        deprecatedIcons.put("Undo.png",       "22x22/actions/edit-undo.png");
        materialIcons.put("22x22/actions/edit-undo.png", ics(GoogleMaterialDesignIcons.UNDO, 22));
        materialIcons.put("16x16/actions/edit-undo.png", ics(GoogleMaterialDesignIcons.UNDO, 16));

        deprecatedIcons.put("Redo.png",       "22x22/actions/edit-redo.png");
        materialIcons.put("22x22/actions/edit-redo.png", ics(GoogleMaterialDesignIcons.REDO, 22));
        materialIcons.put("16x16/actions/edit-redo.png", ics(GoogleMaterialDesignIcons.REDO, 16));

        deprecatedIcons.put("CurrentDir.png", "22x22/status/folder-visiting.png");
        materialIcons.put("22x22/status/folder-visiting.png", ics(GoogleMaterialDesignIcons.FOLDER, 22));
        materialIcons.put("16x16/status/folder-visiting.png", ics(GoogleMaterialDesignIcons.FOLDER, 16));

        deprecatedIcons.put("ParentDir.png",  "22x22/actions/go-parent.png");
        materialIcons.put("22x22/actions/go-parent.png", ics(GoogleMaterialDesignIcons.ARROW_UPWARD, 22));
        materialIcons.put("16x16/actions/go-parent.png", ics(GoogleMaterialDesignIcons.ARROW_UPWARD, 16));

        deprecatedIcons.put("PageSetup.png",  "22x22/actions/printer-setup.png");
        deprecatedIcons.put("Plugins.png",    "22x22/apps/system-installer.png");
        deprecatedIcons.put("Floppy.png",     "22x22/devices/media-floppy.png");

        deprecatedIcons.put("Stop.png",       "22x22/actions/process-stop.png");
        deprecatedIcons.put("Cancel.png",     "22x22/actions/process-stop.png");
        materialIcons.put("22x22/actions/process-stop.png", ics(CustomIconCode.STOP, 22));
        materialIcons.put("16x16/actions/process-stop.png", ics(CustomIconCode.STOP, 16));

        deprecatedIcons.put("Home.png",       "22x22/actions/go-home.png");
        materialIcons.put("22x22/actions/go-home.png", ics(GoogleMaterialDesignIcons.HOME, 22));
        materialIcons.put("16x16/actions/go-home.png", ics(GoogleMaterialDesignIcons.HOME, 16));

        deprecatedIcons.put("Help.png",       "22x22/apps/help-browser.png");
        materialIcons.put("22x22/apps/help-browser.png", ics(GoogleMaterialDesignIcons.HELP, 22));

        deprecatedIcons.put("Properties.png", "22x22/actions/document-properties.png");
        materialIcons.put("22x22/actions/document-properties.png", ics(CustomIconCode.WRENCH, 22));

        deprecatedIcons.put("Preferences.png","22x22/categories/preferences-system.png");
        materialIcons.put("22x22/categories/preferences-system.png", ics(GoogleMaterialDesignIcons.SETTINGS, 22));
        materialIcons.put("16x16/categories/preferences-system.png", ics(GoogleMaterialDesignIcons.SETTINGS, 16));

        deprecatedIcons.put("ZoomIn.png",     "22x22/actions/zoom-in.png");
        materialIcons.put("22x22/actions/zoom-in.png", ics(CustomIconCode.ZOOM_IN, 22));

        deprecatedIcons.put("ZoomOut.png",    "22x22/actions/zoom-out.png");
        materialIcons.put("22x22/actions/zoom-out.png", ics(CustomIconCode.ZOOM_OUT, 22));

        deprecatedIcons.put("BrokenImage.png","22x22/status/image-missing.png");

        deprecatedIcons.put("AdjustWidth.png","22x22/actions/resize-horisontal.png");
        materialIcons.put("22x22/actions/resize-horisontal.png", ics(CustomIconCode.RESIZE_HORIZONTAL, 22));

        deprecatedIcons.put("ToolbarMenu.gif","ToolbarMenu.gif");

        deprecatedIcons.put("Play.png","22x22/actions/media-playback-start.png");
        deprecatedIcons.put("Pause.png","22x22/actions/media-playback-pause.png");

        deprecatedIcons.put("MultipleResults.png", "22x22/actions/edit-find-multiple.png");
        materialIcons.put("22x22/actions/edit-find-multiple.png", ics(CustomIconCode.FIND_MULTIPLE, 22));

        deprecatedIcons.put("SingleResult.png",    "22x22/actions/edit-find-single.png");
        materialIcons.put("22x22/actions/edit-find-single.png", ics(CustomIconCode.TARGET, 22));

        deprecatedIcons.put("NextFile.png",    "22x22/go-last.png");
        deprecatedIcons.put("PreviousFile.png","22x22/go-first.png");
        materialIcons.put("22x22/go-first.png", ics(CustomIconCode.FIRST, 22));
        materialIcons.put("22x22/go-last.png", ics(CustomIconCode.LAST, 22));
        materialIcons.put("22x22/actions/go-first.png", ics(CustomIconCode.FIRST, 22));
        materialIcons.put("22x22/actions/go-last.png", ics(CustomIconCode.LAST, 22));

        deprecatedIcons.put("closebox.gif",   "10x10/actions/close.png");
        materialIcons.put("10x10/actions/close.png", ics(GoogleMaterialDesignIcons.CLOSE, 16));

        deprecatedIcons.put("normal.gif",   "10x10/status/document-unmodified.png");
        deprecatedIcons.put("readonly.gif",   "10x10/emblem/emblem-readonly.png");
        deprecatedIcons.put("dirty.gif",    "10x10/status/document-modified.png");
        deprecatedIcons.put("new.gif",    "10x10/status/document-new.png");

        materialIcons.put("16x16/actions/document-close.png", ics(GoogleMaterialDesignIcons.CLOSE, 16));

        deprecatedIcons.put("ArrowU.png", "22x22/actions/go-up.png");
        materialIcons.put("22x22/actions/go-up.png", ics(GoogleMaterialDesignIcons.ARROW_UPWARD, 22));

        deprecatedIcons.put("ArrowR.png", "22x22/actions/go-next.png");
        materialIcons.put("22x22/actions/go-next.png", ics(GoogleMaterialDesignIcons.ARROW_FORWARD, 22));

        deprecatedIcons.put("ArrowD.png", "22x22/actions/go-down.png");
        materialIcons.put("22x22/actions/go-down.png", ics(GoogleMaterialDesignIcons.ARROW_DOWNWARD, 22));

        deprecatedIcons.put("ArrowL.png", "22x22/actions/go-previous.png");
        materialIcons.put("22x22/actions/go-previous.png", ics(GoogleMaterialDesignIcons.ARROW_BACK, 22));

        deprecatedIcons.put("arrow1.png", "16x16/actions/group-expand.png");
        materialIcons.put("16x16/actions/group-expand.png", ics(CustomIconCode.CHEVRON_RIGHT, 16));

        deprecatedIcons.put("arrow2.png", "16x16/actions/group-collapse.png");
        materialIcons.put("16x16/actions/group-collapse.png", ics(CustomIconCode.CHEVRON_DOWN, 16));

        deprecatedIcons.put("NewView.png", "22x22/actions/window-new.png");
        materialIcons.put("22x22/actions/window-new.png", ics(GoogleMaterialDesignIcons.OPEN_IN_NEW, 22));

        deprecatedIcons.put("UnSplit.png", "22x22/actions/window-unsplit.png");
        materialIcons.put("22x22/actions/window-unsplit.png", ics(CustomIconCode.SPLIT_MERGE, 22));

        deprecatedIcons.put("SplitVertical.png", "22x22/actions/window-split-vertical.png");
        materialIcons.put("22x22/actions/window-split-vertical.png", ics(CustomIconCode.SPLIT_VERTICAL, 22));

        deprecatedIcons.put("SplitHorizontal.png", "22x22/actions/window-split-horizontal.png");
        materialIcons.put("22x22/actions/window-split-horizontal.png", ics(CustomIconCode.SPLIT_HORIZONTAL, 22));

        deprecatedIcons.put("ButtonProperties.png", "22x22/actions/document-properties.png");
        materialIcons.put("16x16/actions/document-properties.png", ics(CustomIconCode.PROPERTIES, 16));
        materialIcons.put("22x22/actions/document-properties.png", ics(CustomIconCode.PROPERTIES, 22));


        materialIcons.put("16x16/actions/close.png", ics(GoogleMaterialDesignIcons.CLOSE, 16));
        materialIcons.put("22x22/actions/edit-find-replace.png", ics(GoogleMaterialDesignIcons.FIND_REPLACE, 22));

        materialIcons.put("16x16/actions/media-record.png", ics(CustomIconCode.RECORD, 16));
        materialIcons.put("16x16/actions/media-playback-stop.png", ics(CustomIconCode.STOP, 16));
        materialIcons.put("22x22/actions/document-close.png", ics(GoogleMaterialDesignIcons.CLOSE, 22));
        materialIcons.put("22x22/actions/bookmark-new.png", ics(GoogleMaterialDesignIcons.BOOKMARK, 22));
        materialIcons.put("22x22/actions/go-jump.png", ics(CustomIconCode.JUMP, 22));
        materialIcons.put("16x16/actions/window-new.png", ics(CustomIconCode.WINDOW, 16));
        materialIcons.put("16x16/apps/system-file-manager.png", ics(CustomIconCode.FILE_MANAGER, 16));
        materialIcons.put("22x22/actions/system-search.png", ics(GoogleMaterialDesignIcons.SEARCH, 22));
        materialIcons.put("arrow-asc.png", ics(CustomIconCode.CHEVRON_UP, 16));
        materialIcons.put("arrow-desc.png", ics(CustomIconCode.CHEVRON_DOWN, 10));
        materialIcons.put("16x16/status/image-loading.png", ics(CustomIconCode.CLOCK, 16));
        //
    }
    //}}}

}
