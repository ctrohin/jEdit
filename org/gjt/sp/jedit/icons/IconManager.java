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
import org.gjt.sp.jedit.options.IconTheme;
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
    private static final Map<String, IconAndSize> material = new HashMap<>();
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
        final IconAndSize fontIcon = material.get(iconString);
        if (fontIcon == null) {
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

        if (fontIcon != null) {
            var imageIcon = new ImageIcon(getMultiRes(fontIcon));
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
            imgs[i] = IconFontSwing.buildImage(icon.icon(), res[i], NEUTRAL_GREY);
//            Log.log(Log.ERROR,
//                "",
//                "Resolution scale " + res[i] + " initial size " + icon.size() + " Computed width " + imgs[i].getWidth(null) + " Computed height " + imgs[i].getHeight(null));
        }
        return new BaseMultiResolutionImage(0, imgs);
    }

    private static final Color NEUTRAL_GREY = new Color(128, 128, 128);

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
        initializeMaterialIcons();

        // Load the icon theme but fallback on the old icons
        String theme = IconTheme.get();
        Log.log(Log.DEBUG, GUIUtilities.class, "Icon theme set to: "+theme);
        setIconPath("jeditresource:/org/gjt/sp/jedit/icons/themes/" + theme + '/');
        Log.log(Log.DEBUG, GUIUtilities.class, "Loading icon theme from: "+iconPath);
    } //}}}

    /**
     * Initializes a list of mappings between old icon names and new names
     */
    private static void initializeDeprecatedIcons()
    {
        deprecatedIcons.put("File.png",       "16x16/mimetypes/text-x-generic.png");
        deprecatedIcons.put("Folder.png",     "16x16/places/folder.png");
        deprecatedIcons.put("OpenFolder.png", "16x16/status/folder-open.png");
        deprecatedIcons.put("OpenFile.png",   "16x16/actions/edit-select-all.png");
        deprecatedIcons.put("ReloadSmall.png","16x16/actions/view-refresh.png");
        deprecatedIcons.put("DriveSmall.png", "16x16/devices/drive-harddisk.png");
        deprecatedIcons.put("New.png",        "22x22/actions/document-new.png");
        deprecatedIcons.put("NewDir.png",     "22x22/actions/folder-new.png");
        deprecatedIcons.put("Reload.png",     "22x22/actions/view-refresh.png");
        deprecatedIcons.put("Load.png",       "22x22/places/plugins.png");
        deprecatedIcons.put("Save.png",       "22x22/actions/document-save.png");
        deprecatedIcons.put("SaveAs.png",     "22x22/actions/document-save-as.png");
        deprecatedIcons.put("SaveAll.png",    "22x22/actions/document-save-all.png");
        deprecatedIcons.put("Open.png",       "22x22/actions/document-open.png");
        deprecatedIcons.put("Print.png",      "22x22/actions/document-print.png");
        deprecatedIcons.put("Drive.png",      "22x22/devices/drive-harddisk.png");
        deprecatedIcons.put("Clear.png",      "22x22/actions/edit-clear.png");
        deprecatedIcons.put("Run.png",        "22x22/actions/application-run.png");
        deprecatedIcons.put("RunAgain.png",   "22x22/actions/application-run-again.png");
        deprecatedIcons.put("RunToBuffer.png",  "22x22/actions/run-to-buffer.png");
        deprecatedIcons.put("CopyToBuffer.png", "22x22/actions/copy-to-buffer.png");
        deprecatedIcons.put("Plus.png",       "22x22/actions/list-add.png");
        deprecatedIcons.put("Minus.png",      "22x22/actions/list-remove.png");
        deprecatedIcons.put("Find.png",       "22x22/actions/edit-find.png");
        deprecatedIcons.put("FindAgain.png",  "22x22/actions/edit-find-next.png");
        deprecatedIcons.put("FindInDir.png",  "22x22/actions/edit-find-in-folder.png");
        deprecatedIcons.put("Parse.png",      "22x22/actions/document-reload2.png");
        deprecatedIcons.put("Delete.png",     "22x22/actions/edit-delete.png");
        deprecatedIcons.put("Paste.png",      "22x22/actions/edit-paste.png");
        deprecatedIcons.put("Cut.png",        "22x22/actions/edit-cut.png");
        deprecatedIcons.put("Copy.png",       "22x22/actions/edit-copy.png");
        deprecatedIcons.put("Undo.png",       "22x22/actions/edit-undo.png");
        deprecatedIcons.put("Redo.png",       "22x22/actions/edit-redo.png");
        deprecatedIcons.put("CurrentDir.png", "22x22/status/folder-visiting.png");
        deprecatedIcons.put("ParentDir.png",  "22x22/actions/go-parent.png");
        deprecatedIcons.put("PageSetup.png",  "22x22/actions/printer-setup.png");
        deprecatedIcons.put("Plugins.png",    "22x22/apps/system-installer.png");
        deprecatedIcons.put("Floppy.png",     "22x22/devices/media-floppy.png");
        deprecatedIcons.put("Stop.png",       "22x22/actions/process-stop.png");
        deprecatedIcons.put("Cancel.png",     "22x22/actions/process-stop.png");
        deprecatedIcons.put("Home.png",       "22x22/actions/go-home.png");
        deprecatedIcons.put("Help.png",       "22x22/apps/help-browser.png");
        deprecatedIcons.put("Properties.png", "22x22/actions/document-properties.png");
        deprecatedIcons.put("Preferences.png","22x22/categories/preferences-system.png");
        deprecatedIcons.put("ZoomIn.png",     "22x22/actions/zoom-in.png");
        deprecatedIcons.put("ZoomOut.png",    "22x22/actions/zoom-out.png");
        deprecatedIcons.put("BrokenImage.png","22x22/status/image-missing.png");
        deprecatedIcons.put("AdjustWidth.png","22x22/actions/resize-horisontal.png");
        deprecatedIcons.put("ToolbarMenu.gif","ToolbarMenu.gif");
        deprecatedIcons.put("Play.png","22x22/actions/media-playback-start.png");
        deprecatedIcons.put("Pause.png","22x22/actions/media-playback-pause.png");
        deprecatedIcons.put("MultipleResults.png", "22x22/actions/edit-find-multiple.png");
        deprecatedIcons.put("SingleResult.png",    "22x22/actions/edit-find-single.png");
        deprecatedIcons.put("NextFile.png",    "22x22/go-last.png");
        deprecatedIcons.put("PreviousFile.png","22x22/go-first.png");
        deprecatedIcons.put("closebox.gif",   "10x10/actions/close.png");
        deprecatedIcons.put("normal.gif",   "10x10/status/document-unmodified.png");
        deprecatedIcons.put("readonly.gif",   "10x10/emblem/emblem-readonly.png");
        deprecatedIcons.put("dirty.gif",    "10x10/status/document-modified.png");
        deprecatedIcons.put("new.gif",    "10x10/status/document-new.png");
        deprecatedIcons.put("ArrowU.png", "22x22/actions/go-up.png");
        deprecatedIcons.put("ArrowR.png", "22x22/actions/go-next.png");
        deprecatedIcons.put("ArrowD.png", "22x22/actions/go-down.png");
        deprecatedIcons.put("ArrowL.png", "22x22/actions/go-previous.png");
        deprecatedIcons.put("arrow1.png", "16x16/actions/group-expand.png");
        deprecatedIcons.put("arrow2.png", "16x16/actions/group-collapse.png");
        deprecatedIcons.put("NewView.png", "22x22/actions/window-new.png");
        deprecatedIcons.put("UnSplit.png", "22x22/actions/window-unsplit.png");
        deprecatedIcons.put("SplitVertical.png", "22x22/actions/window-split-vertical.png");
        deprecatedIcons.put("SplitHorizontal.png", "22x22/actions/window-split-horizontal.png");
        deprecatedIcons.put("ButtonProperties.png", "22x22/actions/document-properties.png");


        //
    }

    private static void initializeMaterialIcons() {
        IconFontSwing.register(GoogleMaterialDesignIcons.getIconFont());
        material.put("22x22/actions/process-stop.png", ics(MatIcons.STOP, 22));
        material.put("16x16/actions/process-stop.png", ics(MatIcons.STOP, 16));
        material.put("22x22/actions/go-home.png", ics(MatIcons.HOME, 22));
        material.put("16x16/actions/go-home.png", ics(MatIcons.HOME, 16));
        material.put("22x22/apps/help-browser.png", ics(MatIcons.HELP, 22));
        material.put("22x22/categories/preferences-system.png", ics(MatIcons.SETTINGS, 22));
        material.put("16x16/categories/preferences-system.png", ics(MatIcons.SETTINGS, 16));
        material.put("22x22/actions/zoom-in.png", ics(MatIcons.ZOOM_IN, 22));
        material.put("22x22/actions/zoom-out.png", ics(MatIcons.ZOOM_OUT, 22));
        material.put("22x22/actions/edit-find-single.png", ics(MatIcons.TARGET, 22));
        material.put("22x22/actions/edit-find-multiple.png", ics(MatIcons.FIND_MULTIPLE, 22));
        material.put("22x22/actions/resize-horisontal.png", ics(MatIcons.RESIZE_HORIZONTAL, 22));
        material.put("22x22/go-first.png", ics(MatIcons.FIRST, 22));
        material.put("22x22/go-last.png", ics(MatIcons.LAST, 22));
        material.put("22x22/actions/go-first.png", ics(MatIcons.FIRST, 22));
        material.put("22x22/actions/go-last.png", ics(MatIcons.LAST, 22));
        material.put("10x10/actions/close.png", ics(MatIcons.CLOSE, 16));
        material.put("16x16/actions/document-close.png", ics(MatIcons.CLOSE, 16));
        material.put("22x22/actions/go-up.png", ics(MatIcons.ARROW_UPWARD, 22));
        material.put("22x22/actions/go-next.png", ics(MatIcons.ARROW_FORWARD, 22));
        material.put("22x22/actions/go-down.png", ics(MatIcons.ARROW_DOWNWARD, 22));
        material.put("22x22/actions/go-previous.png", ics(MatIcons.ARROW_BACK, 22));
        material.put("16x16/actions/group-expand.png", ics(MatIcons.CHEVRON_RIGHT, 16));
        material.put("16x16/actions/group-collapse.png", ics(MatIcons.CHEVRON_DOWN, 16));

        material.put("12x12/actions/group-expand.png", ics(MatIcons.CHEVRON_RIGHT, 10));
        material.put("12x12/actions/group-collapse.png", ics(MatIcons.CHEVRON_DOWN, 10));

        material.put("12x12/actions/opened-file.png", ics(MatIcons.PLAY_ARROW, 10));

        material.put("22x22/actions/window-new.png", ics(MatIcons.OPEN_IN_NEW, 22));
        material.put("22x22/actions/window-unsplit.png", ics(MatIcons.SPLIT_MERGE, 22));
        material.put("22x22/actions/window-split-vertical.png", ics(MatIcons.SPLIT_VERTICAL, 22));
        material.put("22x22/actions/window-split-horizontal.png", ics(MatIcons.SPLIT_HORIZONTAL, 22));

        material.put("16x16/mimetypes/text-x-generic.png", ics(MatIcons.INSERT_DRIVE_FILE, 10));
        material.put("16x16/places/folder.png", ics(MatIcons.FOLDER, 16));
        material.put("16x16/status/folder-open.png", ics(MatIcons.FOLDER_OPEN, 16));
        material.put("16x16/actions/edit-select-all.png", ics(MatIcons.SELECT_ALL, 16));
        material.put("22x22/actions/edit-select-all.png", ics(MatIcons.SELECT_ALL, 22));
        material.put("16x16/actions/view-refresh.png", ics(MatIcons.REFRESH, 16));
        material.put("16x16/devices/drive-harddisk.png", ics(MatIcons.HARD_DISK, 16));
        material.put("16x16/actions/document-new.png", ics(MatIcons.NOTE_ADD, 16));

        material.put("12x12/places/folder.png", ics(MatIcons.FOLDER, 10));
        material.put("12x12/status/folder-open.png", ics(MatIcons.FOLDER_OPEN, 10));
        material.put("12x12/actions/edit-select-all.png", ics(MatIcons.SELECT_ALL, 10));
        material.put("12x12/actions/view-refresh.png", ics(MatIcons.REFRESH, 10));
        material.put("12x12/devices/drive-harddisk.png", ics(MatIcons.HARD_DISK, 10));
        material.put("12x12/actions/document-new.png", ics(MatIcons.NOTE_ADD, 10));


        material.put("22x22/actions/document-new.png", ics(MatIcons.NOTE_ADD, 22));
        material.put("22x22/actions/folder-new.png", ics(MatIcons.CREATE_NEW_FOLDER, 22));
        material.put("22x22/actions/view-refresh.png", ics(MatIcons.REFRESH, 22));
        material.put("22x22/places/plugins.png", ics(MatIcons.PLUGINS, 22));
        material.put("22x22/actions/document-save.png", ics(MatIcons.SAVE, 22));
        material.put("16x16/actions/document-save.png", ics(MatIcons.SAVE, 16));
        material.put("22x22/actions/document-save-as.png", ics(MatIcons.SAVE, 22));
        material.put("16x16/actions/document-save-as.png", ics(MatIcons.SAVE, 16));
        material.put("22x22/actions/document-save-all.png", ics(MatIcons.SAVE, 22));
        material.put("16x16/actions/document-save-all.png", ics(MatIcons.SAVE, 16));
        material.put("22x22/actions/document-open.png", ics(MatIcons.FILE_OPEN, 22));
        material.put("16x16/actions/document-open.png", ics(MatIcons.FILE_OPEN, 16));
        material.put("12x12/actions/document-open.png", ics(MatIcons.FILE_OPEN, 12));
        material.put("22x22/actions/document-print.png", ics(MatIcons.PRINT, 22));
        material.put("16x16/actions/document-print.png", ics(MatIcons.PRINT, 16));
        material.put("12x12/actions/document-print.png", ics(MatIcons.PRINT, 12));
        material.put("22x22/devices/drive-harddisk.png", ics(MatIcons.HARD_DISK, 22));
        material.put("22x22/actions/edit-clear.png", ics(MatIcons.CLEAR, 22));
        material.put("22x22/actions/list-add.png", ics(MatIcons.ADD, 22));
        material.put("16x16/actions/list-add.png", ics(MatIcons.ADD, 16));
        material.put("22x22/actions/list-remove.png", ics(MatIcons.REMOVE, 22));
        material.put("16x16/actions/list-remove.png", ics(MatIcons.REMOVE, 16));
        material.put("22x22/actions/edit-find.png", ics(MatIcons.SEARCH, 22));
        material.put("16x16/actions/edit-find.png", ics(MatIcons.SEARCH, 16));
        material.put("22x22/actions/edit-find-next.png", ics(MatIcons.NAVIGATE_NEXT, 22));
        material.put("16x16/actions/edit-find-next.png", ics(MatIcons.NAVIGATE_NEXT, 16));
        material.put("22x22/actions/edit-find-in-folder.png", ics(MatIcons.SEARCH, 22));
        material.put("16x16/actions/edit-find-in-folder.png", ics(MatIcons.SEARCH, 16));
        material.put("22x22/actions/document-reload2.png", ics(MatIcons.REFRESH, 22));
        material.put("22x22/actions/document-reload.png", ics(MatIcons.REFRESH, 22));
        material.put("16x16/actions/document-reload2.png", ics(MatIcons.REFRESH, 16));
        material.put("22x22/actions/edit-delete.png", ics(MatIcons.DELETE, 22));
        material.put("16x16/actions/edit-delete.png", ics(MatIcons.DELETE, 16));
        material.put("22x22/actions/edit-paste.png", ics(MatIcons.CONTENT_PASTE, 22));
        material.put("16x16/actions/edit-paste.png", ics(MatIcons.CONTENT_PASTE, 16));
        material.put("22x22/actions/edit-cut.png", ics(MatIcons.CONTENT_CUT, 22));
        material.put("16x16/actions/edit-cut.png", ics(MatIcons.CONTENT_CUT, 16));
        material.put("22x22/actions/edit-copy.png", ics(MatIcons.CONTENT_COPY, 22));
        material.put("16x16/actions/edit-copy.png", ics(MatIcons.CONTENT_COPY, 16));
        material.put("22x22/actions/edit-undo.png", ics(MatIcons.UNDO, 22));
        material.put("16x16/actions/edit-undo.png", ics(MatIcons.UNDO, 16));
        material.put("22x22/actions/edit-redo.png", ics(MatIcons.REDO, 22));
        material.put("16x16/actions/edit-redo.png", ics(MatIcons.REDO, 16));
        material.put("22x22/status/folder-visiting.png", ics(MatIcons.FOLDER, 22));
        material.put("16x16/status/folder-visiting.png", ics(MatIcons.FOLDER, 16));
        material.put("22x22/actions/go-parent.png", ics(MatIcons.ARROW_UPWARD, 22));
        material.put("16x16/actions/go-parent.png", ics(MatIcons.ARROW_UPWARD, 16));
        material.put("16x16/actions/document-properties.png", ics(MatIcons.WRENCH, 16));
        material.put("22x22/actions/document-properties.png", ics(MatIcons.WRENCH, 22));
        material.put("16x16/actions/close.png", ics(MatIcons.CLOSE, 16));
        material.put("22x22/actions/edit-find-replace.png", ics(MatIcons.FIND_REPLACE, 22));
        material.put("16x16/actions/media-record.png", ics(MatIcons.RECORD, 16));
        material.put("22x22/actions/media-record.png", ics(MatIcons.RECORD, 22));
        material.put("16x16/actions/media-playback-stop.png", ics(MatIcons.STOP, 16));
        material.put("22x22/actions/media-playback-stop.png", ics(MatIcons.STOP, 22));
        material.put("22x22/actions/document-close.png", ics(MatIcons.CLOSE, 22));
        material.put("22x22/actions/bookmark-new.png", ics(MatIcons.BOOKMARK, 22));
        material.put("22x22/actions/go-jump.png", ics(MatIcons.JUMP, 22));
        material.put("16x16/actions/window-new.png", ics(MatIcons.WINDOW, 16));
        material.put("16x16/apps/system-file-manager.png", ics(MatIcons.FILE_MANAGER, 16));
        material.put("22x22/actions/system-search.png", ics(MatIcons.SEARCH, 22));
        material.put("arrow-asc.png", ics(MatIcons.CHEVRON_UP, 10));
        material.put("arrow-desc.png", ics(MatIcons.CHEVRON_DOWN, 10));
        material.put("16x16/status/image-loading.png", ics(MatIcons.CLOCK, 16));
        material.put("10x10/status/document-unmodified.png", ics(MatIcons.WEB_ASSET, 10));
        material.put("10x10/status/document-modified.png", ics(MatIcons.CREATE, 10));
        material.put("10x10/status/document-new.png", ics(MatIcons.NEW_RELEASES, 10));

        /*
reload.icon.small=12x12/actions/view-refresh.png
reload-all.icon.small=12x12/actions/view-refresh.png
close-buffer.icon.small=12x12/actions/document-close.png
closeall-bufferset.icon.small=12x12/actions/document-close.png
closeall-except-active.icon.small=12x12/actions/document-close.png
global-close-buffer.icon.small=12x12/actions/document-close.png
close-all.icon.small=12x12/actions/document-close.png
save.icon.small=12x12/actions/document-save.png
save-as.icon.small=12x12/actions/document-save-as.png
save-a-copy-as.icon.small=12x12/actions/document-save-as.png
save-all.icon.small=12x12/actions/document-save-all.png
print.icon.small=12x12/actions/document-print.png
page-setup.icon.small=12x12/actions/document-properties.png
exit.icon.small=12x12/actions/process-stop.png
         */

    }
    //}}}
}
