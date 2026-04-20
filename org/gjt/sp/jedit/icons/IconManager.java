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
        if (deprecatedIcons.containsKey(iconName)) {
            Log.log(Log.ERROR, null, "Deprecated icon found: " + iconName);
        }
        final var iconString = deprecatedIcons.getOrDefault(iconName, iconName);
        // TODO: Check if it is possible to render a multiresolution image properly
        final IconAndSize fontIcon = parseIconCode(iconString); // material.get(iconString);
        if (fontIcon == null) {
            Log.log(Log.ERROR, null, "Icon material mapping NOT found: " + iconString);
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
        initializeIconFonts();

        // Load the icon theme but fallback on the old icons
        String theme = IconTheme.get();
        Log.log(Log.DEBUG, GUIUtilities.class, "Icon theme set to: "+theme);
        setIconPath("jeditresource:/org/gjt/sp/jedit/icons/themes/tango/");
        Log.log(Log.DEBUG, GUIUtilities.class, "Loading icon theme from: "+iconPath);
    } //}}}

    /**
     * Initializes a list of mappings between old icon names and new names
     */
    private static void initializeDeprecatedIcons()
    {
        deprecatedIcons.put("File.png",       "MatIcons.INSERT_DRIVE_FILE:10");
        deprecatedIcons.put("Folder.png",     "MatIcons.FOLDER:16");
        deprecatedIcons.put("OpenFolder.png", "MatIcons.FOLDER_OPEN:16");
        deprecatedIcons.put("OpenFile.png",   "MatIcons.SELECT_ALL:16");
        deprecatedIcons.put("ReloadSmall.png","MatIcons.REFRESH:16");
        deprecatedIcons.put("DriveSmall.png", "MatIcons.HARD_DISK:16");
        deprecatedIcons.put("New.png",        "MatIcons.NOTE_ADD:22");
        deprecatedIcons.put("NewDir.png",     "MatIcons.CREATE_NEW_FOLDER:22");
        deprecatedIcons.put("Reload.png",     "MatIcons.REFRESH:22");
        deprecatedIcons.put("Load.png",       "MatIcons.PLUGINS:22");
        deprecatedIcons.put("Save.png",       "MatIcons.SAVE:22");
        deprecatedIcons.put("SaveAs.png",     "MatIcons.SAVE:22");
        deprecatedIcons.put("SaveAll.png",    "MatIcons.SAVE:22");
        deprecatedIcons.put("Open.png",       "MatIcons.FILE_OPEN:22");
        deprecatedIcons.put("Print.png",      "MatIcons.PRINT:22");
        deprecatedIcons.put("Drive.png",      "MatIcons.HARD_DISK:22");
        deprecatedIcons.put("Clear.png",      "MatIcons.CLEAR:22");
        deprecatedIcons.put("Run.png",        "MatIcons.NEXT_WEEK:22");
        deprecatedIcons.put("RunAgain.png",   "22x22/actions/application-run-again.png");
        deprecatedIcons.put("RunToBuffer.png",  "22x22/actions/run-to-buffer.png");
        deprecatedIcons.put("CopyToBuffer.png", "MatIcons.CONTENT_COPY:22");
        deprecatedIcons.put("Plus.png",       "MatIcons.ADD:22");
        deprecatedIcons.put("Minus.png",      "MatIcons.REMOVE:22");
        deprecatedIcons.put("Find.png",       "MatIcons.SEARCH:22");
        deprecatedIcons.put("FindAgain.png",  "MatIcons.NAVIGATE_NEXT:22");
        deprecatedIcons.put("FindInDir.png",  "MatIcons.SEARCH:22");
        deprecatedIcons.put("Parse.png",      "MatIcons.REFRESH:22");
        deprecatedIcons.put("Delete.png",     "MatIcons.DELETE:22");
        deprecatedIcons.put("Paste.png",      "MatIcons.CONTENT_PASTE:22");
        deprecatedIcons.put("Cut.png",        "MatIcons.CONTENT_CUT:22");
        deprecatedIcons.put("Copy.png",       "MatIcons.CONTENT_COPY:22");
        deprecatedIcons.put("Undo.png",       "MatIcons.UNDO:22");
        deprecatedIcons.put("Redo.png",       "MatIcons.REDO:22");
        deprecatedIcons.put("CurrentDir.png", "MatIcons.FOLDER:22");
        deprecatedIcons.put("ParentDir.png",  "MatIcons.ARROW_UPWARD:22");
        deprecatedIcons.put("PageSetup.png",  "MatIcons.PRINT:22");
        deprecatedIcons.put("Plugins.png",    "MatIcons.PLUGINS:22");
        deprecatedIcons.put("Floppy.png",     "MatIcons.SAVE:22");
        deprecatedIcons.put("Stop.png",       "MatIcons.STOP:22");
        deprecatedIcons.put("Cancel.png",     "MatIcons.STOP:22");
        deprecatedIcons.put("Home.png",       "MatIcons.HOME:22");
        deprecatedIcons.put("Help.png",       "MatIcons.HELP:22");
        deprecatedIcons.put("Properties.png", "MatIcons.WRENCH:22");
        deprecatedIcons.put("Preferences.png","MatIcons.SETTINGS:22");
        deprecatedIcons.put("ZoomIn.png",     "MatIcons.ZOOM_IN:22");
        deprecatedIcons.put("ZoomOut.png",    "MatIcons.ZOOM_OUT:22");
        deprecatedIcons.put("BrokenImage.png","22x22/status/image-missing.png");
        deprecatedIcons.put("AdjustWidth.png","MatIcons.RESIZE_HORIZONTAL:22");
        deprecatedIcons.put("ToolbarMenu.gif","ToolbarMenu.gif");
        deprecatedIcons.put("Play.png","MatIcons.PLAY_ARROW:22");
        deprecatedIcons.put("Pause.png","MatIcons.PAUSE:22");
        deprecatedIcons.put("MultipleResults.png", "MatIcons.FIND_MULTIPLE:22");
        deprecatedIcons.put("SingleResult.png",    "MatIcons.TARGET:22");
        deprecatedIcons.put("NextFile.png",    "MatIcons.LAST:22");
        deprecatedIcons.put("PreviousFile.png","MatIcons.FIRST:22");
        deprecatedIcons.put("closebox.gif",   "MatIcons.CLOSE:16");
        deprecatedIcons.put("normal.gif",   "MatIcons.WEB_ASSET:10");
        deprecatedIcons.put("readonly.gif",   "10x10/emblem/emblem-readonly.png");
        deprecatedIcons.put("dirty.gif",    "MatIcons.CREATE:10");
        deprecatedIcons.put("new.gif",    "MatIcons.NEW_RELEASES:10");
        deprecatedIcons.put("ArrowU.png", "MatIcons.ARROW_UPWARD:22");
        deprecatedIcons.put("ArrowR.png", "MatIcons.ARROW_FORWARD:22");
        deprecatedIcons.put("ArrowD.png", "MatIcons.ARROW_DOWNWARD:22");
        deprecatedIcons.put("ArrowL.png", "MatIcons.ARROW_BACK:22");
        deprecatedIcons.put("arrow1.png", "MatIcons.CHEVRON_RIGHT:16");
        deprecatedIcons.put("arrow2.png", "MatIcons.CHEVRON_DOWN:16");
        deprecatedIcons.put("NewView.png", "MatIcons.OPEN_IN_NEW:22");
        deprecatedIcons.put("UnSplit.png", "MatIcons.SPLIT_MERGE:22");
        deprecatedIcons.put("SplitVertical.png", "MatIcons.SPLIT_VERTICAL:22");
        deprecatedIcons.put("SplitHorizontal.png", "MatIcons.SPLIT_HORIZONTAL:22");
        deprecatedIcons.put("ButtonProperties.png", "MatIcons.WRENCH:22");
        deprecatedIcons.put("22x22/actions/edit-clear.png", "MatIcons.CLEAR:22");

        deprecatedIcons.put("22x22/actions/process-stop.png", "MatIcons.STOP:22");
        deprecatedIcons.put("16x16/actions/process-stop.png", "MatIcons.STOP:16");
        deprecatedIcons.put("22x22/actions/go-home.png", "MatIcons.HOME:22");
        deprecatedIcons.put("16x16/actions/go-home.png", "MatIcons.HOME:16");
        deprecatedIcons.put("22x22/apps/help-browser.png", "MatIcons.HELP:22");
        deprecatedIcons.put("22x22/categories/preferences-system.png", "MatIcons.SETTINGS:22");
        deprecatedIcons.put("16x16/categories/preferences-system.png", "MatIcons.SETTINGS:16");
        deprecatedIcons.put("22x22/actions/zoom-in.png", "MatIcons.ZOOM_IN:22");
        deprecatedIcons.put("22x22/actions/zoom-out.png", "MatIcons.ZOOM_OUT:22");
        deprecatedIcons.put("22x22/actions/edit-find-single.png", "MatIcons.TARGET:22");
        deprecatedIcons.put("22x22/actions/edit-find-multiple.png", "MatIcons.FIND_MULTIPLE:22");
        deprecatedIcons.put("22x22/actions/resize-horisontal.png", "MatIcons.RESIZE_HORIZONTAL:22");
        deprecatedIcons.put("22x22/go-first.png", "MatIcons.FIRST:22");
        deprecatedIcons.put("22x22/go-last.png", "MatIcons.LAST:22");
        deprecatedIcons.put("22x22/actions/go-first.png", "MatIcons.FIRST:22");
        deprecatedIcons.put("22x22/actions/go-last.png", "MatIcons.LAST:22");
        deprecatedIcons.put("10x10/actions/close.png", "MatIcons.CLOSE:16");
        deprecatedIcons.put("16x16/actions/document-close.png", "MatIcons.CLOSE:16");
        deprecatedIcons.put("22x22/actions/go-up.png", "MatIcons.ARROW_UPWARD:22");
        deprecatedIcons.put("22x22/actions/go-next.png", "MatIcons.ARROW_FORWARD:22");
        deprecatedIcons.put("22x22/actions/go-down.png", "MatIcons.ARROW_DOWNWARD:22");
        deprecatedIcons.put("22x22/actions/go-previous.png", "MatIcons.ARROW_BACK:22");
        deprecatedIcons.put("16x16/actions/group-expand.png", "MatIcons.CHEVRON_RIGHT:16");
        deprecatedIcons.put("16x16/actions/group-collapse.png", "MatIcons.CHEVRON_DOWN:16");

        deprecatedIcons.put("12x12/actions/group-expand.png", "MatIcons.CHEVRON_RIGHT:10");
        deprecatedIcons.put("12x12/actions/group-collapse.png", "MatIcons.CHEVRON_DOWN:10");

        deprecatedIcons.put("12x12/actions/opened-file.png", "MatIcons.PLAY_ARROW:10");

        deprecatedIcons.put("22x22/actions/window-new.png", "MatIcons.OPEN_IN_NEW:22");
        deprecatedIcons.put("22x22/actions/window-unsplit.png", "MatIcons.SPLIT_MERGE:22");
        deprecatedIcons.put("22x22/actions/window-split-vertical.png", "MatIcons.SPLIT_VERTICAL:22");
        deprecatedIcons.put("22x22/actions/window-split-horizontal.png", "MatIcons.SPLIT_HORIZONTAL:22");

        deprecatedIcons.put("16x16/mimetypes/text-x-generic.png", "MatIcons.INSERT_DRIVE_FILE:10");
        deprecatedIcons.put("16x16/places/folder.png", "MatIcons.FOLDER:16");
        deprecatedIcons.put("16x16/status/folder-open.png", "MatIcons.FOLDER_OPEN:16");
        deprecatedIcons.put("16x16/actions/edit-select-all.png", "MatIcons.SELECT_ALL:16");
        deprecatedIcons.put("22x22/actions/edit-select-all.png", "MatIcons.SELECT_ALL:22");
        deprecatedIcons.put("16x16/actions/view-refresh.png", "MatIcons.REFRESH:16");
        deprecatedIcons.put("16x16/devices/drive-harddisk.png", "MatIcons.HARD_DISK:16");
        deprecatedIcons.put("22x22/actions/document-new.png", "MatIcons.NOTE_ADD:22");
        deprecatedIcons.put("16x16/actions/document-new.png", "MatIcons.NOTE_ADD:16");

        deprecatedIcons.put("12x12/places/folder.png", "MatIcons.FOLDER:10");
        deprecatedIcons.put("12x12/status/folder-open.png", "MatIcons.FOLDER_OPEN:10");
        deprecatedIcons.put("12x12/actions/edit-select-all.png", "MatIcons.SELECT_ALL:10");
        deprecatedIcons.put("12x12/actions/view-refresh.png", "MatIcons.REFRESH:10");
        deprecatedIcons.put("12x12/devices/drive-harddisk.png", "MatIcons.HARD_DISK:10");
        deprecatedIcons.put("12x12/actions/document-new.png", "MatIcons.NOTE_ADD:10");


        deprecatedIcons.put("22x22/actions/folder-new.png", "MatIcons.CREATE_NEW_FOLDER:22");
        deprecatedIcons.put("22x22/actions/view-refresh.png", "MatIcons.REFRESH:22");
        deprecatedIcons.put("22x22/places/plugins.png", "MatIcons.PLUGINS:22");
        deprecatedIcons.put("22x22/actions/document-save.png", "MatIcons.SAVE:22");
        deprecatedIcons.put("16x16/actions/document-save.png", "MatIcons.SAVE:16");
        deprecatedIcons.put("22x22/actions/document-save-as.png", "MatIcons.SAVE:22");
        deprecatedIcons.put("16x16/actions/document-save-as.png", "MatIcons.SAVE:16");
        deprecatedIcons.put("22x22/actions/document-save-all.png", "MatIcons.SAVE:22");
        deprecatedIcons.put("16x16/actions/document-save-all.png", "MatIcons.SAVE:16");
        deprecatedIcons.put("22x22/actions/document-open.png", "MatIcons.FILE_OPEN:22");
        deprecatedIcons.put("16x16/actions/document-open.png", "MatIcons.FILE_OPEN:16");
        deprecatedIcons.put("12x12/actions/document-open.png", "MatIcons.FILE_OPEN:12");
        deprecatedIcons.put("22x22/actions/document-print.png", "MatIcons.PRINT:22");
        deprecatedIcons.put("16x16/actions/document-print.png", "MatIcons.PRINT:16");
        deprecatedIcons.put("12x12/actions/document-print.png", "MatIcons.PRINT:12");
        deprecatedIcons.put("22x22/devices/drive-harddisk.png", "MatIcons.HARD_DISK:22");
        deprecatedIcons.put("22x22/actions/list-add.png", "MatIcons.ADD:22");
        deprecatedIcons.put("16x16/actions/list-add.png", "MatIcons.ADD:16");
        deprecatedIcons.put("22x22/actions/list-remove.png", "MatIcons.REMOVE:22");
        deprecatedIcons.put("16x16/actions/list-remove.png", "MatIcons.REMOVE:16");
        deprecatedIcons.put("22x22/actions/edit-find.png", "MatIcons.SEARCH:22");
        deprecatedIcons.put("16x16/actions/edit-find.png", "MatIcons.SEARCH:16");
        deprecatedIcons.put("22x22/actions/edit-find-next.png", "MatIcons.NAVIGATE_NEXT:22");
        deprecatedIcons.put("16x16/actions/edit-find-next.png", "MatIcons.NAVIGATE_NEXT:16");
        deprecatedIcons.put("22x22/actions/edit-find-in-folder.png", "MatIcons.SEARCH:22");
        deprecatedIcons.put("16x16/actions/edit-find-in-folder.png", "MatIcons.SEARCH:16");
        deprecatedIcons.put("22x22/actions/document-reload2.png", "MatIcons.REFRESH:22");
        deprecatedIcons.put("22x22/actions/document-reload.png", "MatIcons.REFRESH:22");
        deprecatedIcons.put("16x16/actions/document-reload2.png", "MatIcons.REFRESH:16");
        deprecatedIcons.put("22x22/actions/edit-delete.png", "MatIcons.DELETE:22");
        deprecatedIcons.put("16x16/actions/edit-delete.png", "MatIcons.DELETE:16");
        deprecatedIcons.put("22x22/actions/edit-paste.png", "MatIcons.CONTENT_PASTE:22");
        deprecatedIcons.put("16x16/actions/edit-paste.png", "MatIcons.CONTENT_PASTE:16");
        deprecatedIcons.put("22x22/actions/edit-cut.png", "MatIcons.CONTENT_CUT:22");
        deprecatedIcons.put("16x16/actions/edit-cut.png", "MatIcons.CONTENT_CUT:16");
        deprecatedIcons.put("22x22/actions/edit-copy.png", "MatIcons.CONTENT_COPY:22");
        deprecatedIcons.put("16x16/actions/edit-copy.png", "MatIcons.CONTENT_COPY:16");
        deprecatedIcons.put("22x22/actions/edit-undo.png", "MatIcons.UNDO:22");
        deprecatedIcons.put("16x16/actions/edit-undo.png", "MatIcons.UNDO:16");
        deprecatedIcons.put("22x22/actions/edit-redo.png", "MatIcons.REDO:22");
        deprecatedIcons.put("16x16/actions/edit-redo.png", "MatIcons.REDO:16");
        deprecatedIcons.put("22x22/status/folder-visiting.png", "MatIcons.FOLDER:22");
        deprecatedIcons.put("16x16/status/folder-visiting.png", "MatIcons.FOLDER:16");
        deprecatedIcons.put("22x22/actions/go-parent.png", "MatIcons.ARROW_UPWARD:22");
        deprecatedIcons.put("16x16/actions/go-parent.png", "MatIcons.ARROW_UPWARD:16");
        deprecatedIcons.put("16x16/actions/document-properties.png", "MatIcons.WRENCH:16");
        deprecatedIcons.put("22x22/actions/document-properties.png", "MatIcons.WRENCH:22");
        deprecatedIcons.put("16x16/actions/close.png", "MatIcons.CLOSE:16");
        deprecatedIcons.put("22x22/actions/edit-find-replace.png", "MatIcons.FIND_REPLACE:22");
        deprecatedIcons.put("16x16/actions/media-record.png", "MatIcons.RECORD:16");
        deprecatedIcons.put("22x22/actions/media-record.png", "MatIcons.RECORD:22");
        deprecatedIcons.put("16x16/actions/media-playback-stop.png", "MatIcons.STOP:16");
        deprecatedIcons.put("22x22/actions/media-playback-stop.png", "MatIcons.STOP:22");
        deprecatedIcons.put("22x22/actions/document-close.png", "MatIcons.CLOSE:22");
        deprecatedIcons.put("22x22/actions/bookmark-new.png", "MatIcons.BOOKMARK:22");
        deprecatedIcons.put("22x22/actions/go-jump.png", "MatIcons.JUMP:22");
        deprecatedIcons.put("16x16/actions/window-new.png", "MatIcons.WINDOW:16");
        deprecatedIcons.put("16x16/apps/system-file-manager.png", "MatIcons.FILE_MANAGER:16");
        deprecatedIcons.put("22x22/actions/system-search.png", "MatIcons.SEARCH:22");
        deprecatedIcons.put("arrow-asc.png", "MatIcons.CHEVRON_UP:10");
        deprecatedIcons.put("arrow-desc.png", "MatIcons.CHEVRON_DOWN:10");
        deprecatedIcons.put("16x16/status/image-loading.png", "MatIcons.CLOCK:16");
        deprecatedIcons.put("10x10/status/document-unmodified.png", "MatIcons.WEB_ASSET:10");
        deprecatedIcons.put("10x10/status/document-modified.png", "MatIcons.CREATE:10");
        deprecatedIcons.put("10x10/status/document-new.png", "MatIcons.NEW_RELEASES:10");
        
        //
    }

    private static final String MAT_ICONS_PREFIX = "MatIcons.";
    private static final String FILE_ICONS_PREFIX = "FileIcons.";
    private static IconAndSize parseIconCode(final String icon) {
        if (!icon.startsWith(MAT_ICONS_PREFIX) && !icon.startsWith(FILE_ICONS_PREFIX)) {
            return null;
        }
        final var matIcon = icon.startsWith(MAT_ICONS_PREFIX);
        final var prefix = matIcon ? MAT_ICONS_PREFIX : FILE_ICONS_PREFIX;
        var iconAndSize = icon.substring(prefix.length());
        if (!iconAndSize.contains(":")) {
            return null;
        }
        var split = iconAndSize.split(":");
        try {
            var ic = matIcon ? MatIcons.valueOf(split[0]) : FileIcons.valueOf(split[0]);
            var sz = Integer.parseInt(split[1]);
            return new IconAndSize(ic, sz);
        } catch (Exception _) {}
        return null;
    }

    private static void initializeIconFonts() {
        IconFontSwing.register(MatIcons.getIconFont());
        IconFontSwing.register(FileIcons.getIconFont());
    }
    //}}}
}
