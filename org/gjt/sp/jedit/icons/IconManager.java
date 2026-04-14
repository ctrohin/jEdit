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
        deprecatedIcons.put("Run.png",        "22x22/actions/application-run.png");
        deprecatedIcons.put("RunAgain.png",   "22x22/actions/application-run-again.png");
        deprecatedIcons.put("RunToBuffer.png",  "22x22/actions/run-to-buffer.png");
        deprecatedIcons.put("CopyToBuffer.png", "22x22/actions/copy-to-buffer.png");
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
        deprecatedIcons.put("Undo.png",       "22x22/actions/edit-undo.png");
        deprecatedIcons.put("Redo.png",       "22x22/actions/edit-redo.png");
        deprecatedIcons.put("CurrentDir.png", "22x22/status/folder-visiting.png");
        deprecatedIcons.put("ParentDir.png",  "22x22/actions/go-parent.png");
        deprecatedIcons.put("PageSetup.png",  "22x22/actions/printer-setup.png");
        deprecatedIcons.put("Plugins.png",    "22x22/apps/system-installer.png");
        deprecatedIcons.put("Floppy.png",     "22x22/devices/media-floppy.png");
        deprecatedIcons.put("Stop.png",       "MatIcons.STOP:22");
        deprecatedIcons.put("Cancel.png",     "MatIcons.STOP:22");
        deprecatedIcons.put("Home.png",       "MatIcons.HOME:22");
        deprecatedIcons.put("Help.png",       "MatIcons.HELP:22");
        deprecatedIcons.put("Properties.png", "22x22/actions/document-properties.png");
        deprecatedIcons.put("Preferences.png","MatIcons.SETTINGS:22");
        deprecatedIcons.put("ZoomIn.png",     "MatIcons.ZOOM_IN:22");
        deprecatedIcons.put("ZoomOut.png",    "MatIcons.ZOOM_OUT:22");
        deprecatedIcons.put("BrokenImage.png","22x22/status/image-missing.png");
        deprecatedIcons.put("AdjustWidth.png","MatIcons.RESIZE_HORIZONTAL:22");
        deprecatedIcons.put("ToolbarMenu.gif","ToolbarMenu.gif");
        deprecatedIcons.put("Play.png","22x22/actions/media-playback-start.png");
        deprecatedIcons.put("Pause.png","22x22/actions/media-playback-pause.png");
        deprecatedIcons.put("MultipleResults.png", "MatIcons.FIND_MULTIPLE:22");
        deprecatedIcons.put("SingleResult.png",    "MatIcons.TARGET:22");
        deprecatedIcons.put("NextFile.png",    "MatIcons.LAST:22");
        deprecatedIcons.put("PreviousFile.png","MatIcons.FIRST:22");
        deprecatedIcons.put("closebox.gif",   "MatIcons.CLOSE:16");
        deprecatedIcons.put("normal.gif",   "10x10/status/document-unmodified.png");
        deprecatedIcons.put("readonly.gif",   "10x10/emblem/emblem-readonly.png");
        deprecatedIcons.put("dirty.gif",    "10x10/status/document-modified.png");
        deprecatedIcons.put("new.gif",    "10x10/status/document-new.png");
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
        deprecatedIcons.put("ButtonProperties.png", "22x22/actions/document-properties.png");


        //
    }

    private static void initializeMaterialIcons() {
        IconFontSwing.register(GoogleMaterialDesignIcons.getIconFont());
        material.put("MatIcons.STOP:22", ics(MatIcons.STOP, 22));
        material.put("MatIcons.STOP:16", ics(MatIcons.STOP, 16));
        material.put("MatIcons.HOME:22", ics(MatIcons.HOME, 22));
        material.put("MatIcons.HOME:16", ics(MatIcons.HOME, 16));
        material.put("MatIcons.HELP:22", ics(MatIcons.HELP, 22));
        material.put("MatIcons.SETTINGS:22", ics(MatIcons.SETTINGS, 22));
        material.put("MatIcons.SETTINGS:16", ics(MatIcons.SETTINGS, 16));
        material.put("MatIcons.ZOOM_IN:22", ics(MatIcons.ZOOM_IN, 22));
        material.put("MatIcons.ZOOM_OUT:22", ics(MatIcons.ZOOM_OUT, 22));
        material.put("MatIcons.TARGET:22", ics(MatIcons.TARGET, 22));
        material.put("MatIcons.FIND_MULTIPLE:22", ics(MatIcons.FIND_MULTIPLE, 22));
        material.put("MatIcons.RESIZE_HORIZONTAL:22", ics(MatIcons.RESIZE_HORIZONTAL, 22));
        material.put("MatIcons.FIRST:22", ics(MatIcons.FIRST, 22));
        material.put("MatIcons.LAST:22", ics(MatIcons.LAST, 22));
        material.put("MatIcons.CLOSE:16", ics(MatIcons.CLOSE, 16));
        material.put("MatIcons.ARROW_UPWARD:22", ics(MatIcons.ARROW_UPWARD, 22));
        material.put("MatIcons.ARROW_FORWARD:22", ics(MatIcons.ARROW_FORWARD, 22));
        material.put("MatIcons.ARROW_DOWNWARD:22", ics(MatIcons.ARROW_DOWNWARD, 22));
        material.put("MatIcons.ARROW_BACK:22", ics(MatIcons.ARROW_BACK, 22));
        material.put("MatIcons.CHEVRON_RIGHT:16", ics(MatIcons.CHEVRON_RIGHT, 16));
        material.put("MatIcons.CHEVRON_DOWN:16", ics(MatIcons.CHEVRON_DOWN, 16));

        material.put("MatIcons.CHEVRON_RIGHT:10", ics(MatIcons.CHEVRON_RIGHT, 10));
        material.put("MatIcons.CHEVRON_DOWN:10", ics(MatIcons.CHEVRON_DOWN, 10));

        material.put("MatIcons.PLAY_ARROW:10", ics(MatIcons.PLAY_ARROW, 10));

        material.put("MatIcons.OPEN_IN_NEW:22", ics(MatIcons.OPEN_IN_NEW, 22));
        material.put("MatIcons.SPLIT_MERGE:22", ics(MatIcons.SPLIT_MERGE, 22));
        material.put("MatIcons.SPLIT_VERTICAL:22", ics(MatIcons.SPLIT_VERTICAL, 22));
        material.put("MatIcons.SPLIT_HORIZONTAL:22", ics(MatIcons.SPLIT_HORIZONTAL, 22));

        material.put("MatIcons.INSERT_DRIVE_FILE:10", ics(MatIcons.INSERT_DRIVE_FILE, 10));
        material.put("MatIcons.FOLDER:16", ics(MatIcons.FOLDER, 16));
        material.put("MatIcons.FOLDER_OPEN:16", ics(MatIcons.FOLDER_OPEN, 16));
        material.put("MatIcons.SELECT_ALL:16", ics(MatIcons.SELECT_ALL, 16));
        material.put("MatIcons.SELECT_ALL:22", ics(MatIcons.SELECT_ALL, 22));
        material.put("MatIcons.REFRESH:16", ics(MatIcons.REFRESH, 16));
        material.put("MatIcons.HARD_DISK:16", ics(MatIcons.HARD_DISK, 16));
        material.put("MatIcons.NOTE_ADD:16", ics(MatIcons.NOTE_ADD, 16));

        material.put("MatIcons.F0LDER:10", ics(MatIcons.FOLDER, 10));
        material.put("MatIcons.F0LDER_OPEN:10", ics(MatIcons.FOLDER_OPEN, 10));
        material.put("MatIcons.SELECT_ALL:10", ics(MatIcons.SELECT_ALL, 10));
        material.put("MatIcons.REFRESH:10", ics(MatIcons.REFRESH, 10));
        material.put("MatIcons.HARD_DISK:10", ics(MatIcons.HARD_DISK, 10));
        material.put("MatIcons.NOTE_ADD:10", ics(MatIcons.NOTE_ADD, 10));
        
        material.put("MatIcons.NOTE_ADD:22", ics(MatIcons.NOTE_ADD, 22));
        material.put("MatIcons.CREATE_NEW_FOLDER:22", ics(MatIcons.CREATE_NEW_FOLDER, 22));
        material.put("MatIcons.REFRESH:22", ics(MatIcons.REFRESH, 22));
        material.put("MatIcons.PLUGINS:22", ics(MatIcons.PLUGINS, 22));
        material.put("MatIcons.SAVE:22", ics(MatIcons.SAVE, 22));
        material.put("MatIcons.SAVE:16", ics(MatIcons.SAVE, 16));
        material.put("MatIcons.FILE_OPEN:22", ics(MatIcons.FILE_OPEN, 22));
        material.put("MatIcons.FILE_OPEN:16", ics(MatIcons.FILE_OPEN, 16));
        material.put("MatIcons.FILE_OPEN:12", ics(MatIcons.FILE_OPEN, 12));
        material.put("MatIcons.PRINT:22", ics(MatIcons.PRINT, 22));
        material.put("MatIcons.PRINT:16", ics(MatIcons.PRINT, 16));
        material.put("MatIcons.PRINT:12", ics(MatIcons.PRINT, 12));
        material.put("MatIcons.HARD_DISK:22", ics(MatIcons.HARD_DISK, 22));
        material.put("MatIcons.CLEAR:22", ics(MatIcons.CLEAR, 22));
        material.put("MatIcons.ADD:22", ics(MatIcons.ADD, 22));
        material.put("MatIcons.ADD:16", ics(MatIcons.ADD, 16));
        material.put("MatIcons.REMOVE:22", ics(MatIcons.REMOVE, 22));
        material.put("MatIcons.REMOVE:16", ics(MatIcons.REMOVE, 16));
        material.put("MatIcons.SEARCH:22", ics(MatIcons.SEARCH, 22));
        material.put("MatIcons.SEARCH:16", ics(MatIcons.SEARCH, 16));
        material.put("MatIcons.NAVIGATE_NEXT:22", ics(MatIcons.NAVIGATE_NEXT, 22));
        material.put("MatIcons.NAVIGATE_NEXT:16", ics(MatIcons.NAVIGATE_NEXT, 16));
        material.put("MatIcons.DELETE:22", ics(MatIcons.DELETE, 22));
        material.put("MatIcons.DELETE:16", ics(MatIcons.DELETE, 16));
        material.put("MatIcons.CONTENT_PASTE:22", ics(MatIcons.CONTENT_PASTE, 22));
        material.put("MatIcons.CONTENT_PASTE:16", ics(MatIcons.CONTENT_PASTE, 16));
        material.put("MatIcons.CONTENT_CUT:22", ics(MatIcons.CONTENT_CUT, 22));
        material.put("MatIcons.CONTENT_CUT:16", ics(MatIcons.CONTENT_CUT, 16));
        material.put("MatIcons.CONTENT_COPY:22", ics(MatIcons.CONTENT_COPY, 22));
        material.put("MatIcons.CONTENT_COPY:16", ics(MatIcons.CONTENT_COPY, 16));
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
        material.put("22x22/actions/media-playback-pause.png", ics(MatIcons.PAUSE, 22));
        material.put("22x22/actions/media-playback-start.png", ics(MatIcons.PLAY_ARROW, 22));
        material.put("22x22/devices/media-floppy.png", ics(MatIcons.SAVE, 22));
        material.put("Blank24.gif", ics(MatIcons.BLANK, 24));
        material.put("22x22/apps/system-installer.png", ics(MatIcons.PLUGINS, 22));
        material.put("22x22/apps/internet-web-browser.png", ics(MatIcons.LANGUAGE, 22));
        material.put("22x22/apps/system-file-manager.png", ics(MatIcons.FILE_MANAGER, 22));
        material.put("22x22/apps/utilities-terminal.png", ics(MatIcons.DESKTOP_MAC, 22));
        material.put("22x22/devices/printer.png", ics(MatIcons.PRINT, 22));
        material.put("22x22/actions/application-run.png", ics(MatIcons.NEXT_WEEK, 22));
        material.put("22x22/actions/printer-setup.png", ics(MatIcons.PRINT, 22));
        material.put("22x22/actions/copy-to-buffer.png", ics(MatIcons.CONTENT_COPY, 22));
        material.put("MatIcons.BLUR_ON:22", ics(MatIcons.BLUR_ON, 22));
        /*
reload.icon.small=MatIcons.REFRESH:10
reload-all.icon.small=MatIcons.REFRESH:10
close-buffer.icon.small=12x12/actions/document-close.png
closeall-bufferset.icon.small=12x12/actions/document-close.png
closeall-except-active.icon.small=12x12/actions/document-close.png
global-close-buffer.icon.small=12x12/actions/document-close.png
close-all.icon.small=12x12/actions/document-close.png
save.icon.small=12x12/actions/document-save.png
save-as.icon.small=12x12/actions/document-save-as.png
save-a-copy-as.icon.small=12x12/actions/document-save-as.png
save-all.icon.small=12x12/actions/document-save-all.png
print.icon.small=MatIcons.PRINT:12
page-setup.icon.small=12x12/actions/document-properties.png
exit.icon.small=12x12/actions/process-stop.png
         */

    }
    //}}}
}
