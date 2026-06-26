/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.Color;

import javax.swing.Icon;

import org.eclipse.lsp4j.SymbolKind;
import org.gjt.sp.jedit.icons.WorkspaceFileIcons;

/**
 * Circle-letter icons for LSP document symbols in the Structure view.
 */
final class LspStructureIcons {

    private static final Color CLASS_BG = new Color(0xE7, 0x6F, 0x00);
    private static final Color METHOD_BG = new Color(0x31, 0x78, 0xC6);
    private static final Color FIELD_BG = new Color(0x4E, 0xAA, 0x25);
    private static final Color ENUM_BG = new Color(0x00, 0xAD, 0x9C);
    private static final Color PROPERTY_BG = new Color(0x7F, 0x52, 0xFF);
    private static final Color OTHER_BG = new Color(0x9E, 0x9E, 0x9E);
    private static final Color LIGHT_FG = Color.WHITE;

    private LspStructureIcons() {}

    static Icon iconFor(SymbolKind kind) {
        return WorkspaceFileIcons.createLetterIcon(
            letterFor(kind),
            backgroundFor(kind),
            foregroundFor(kind));
    }

    private static String letterFor(SymbolKind kind) {
        if (kind == null) {
            return "s";
        }
        return switch (kind) {
            case Class, Interface, Struct, TypeParameter -> "c";
            case Method, Function, Constructor -> "m";
            case Field, Variable, Constant, EnumMember -> "f";
            case Property -> "p";
            case Enum -> "e";
            case Namespace, Module, Package -> "n";
            default -> "s";
        };
    }

    private static Color backgroundFor(SymbolKind kind) {
        if (kind == null) {
            return OTHER_BG;
        }
        return switch (kind) {
            case Class, Interface, Struct, TypeParameter -> CLASS_BG;
            case Method, Function, Constructor -> METHOD_BG;
            case Field, Variable, Constant, EnumMember -> FIELD_BG;
            case Property -> PROPERTY_BG;
            case Enum -> ENUM_BG;
            default -> OTHER_BG;
        };
    }

    private static Color foregroundFor(SymbolKind kind) {
        return LIGHT_FG;
    }
}
