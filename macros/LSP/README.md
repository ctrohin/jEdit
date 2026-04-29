# LSP Macros Directory

This directory contains BeanShell macros for LSP (Language Server Protocol) functionality in jEdit.

## Macros Included

### LSP_Complete.bsh
**Description**: Trigger LSP-based code completions at cursor position

**Usage**:
1. Bind this macro to a keyboard shortcut (e.g., Ctrl+Space)
2. Open a file in a supported language (Python, Java, C++, etc.)
3. Position cursor after a word
4. Press the shortcut key
5. Select from completion popup with arrows/numbers

**Requirements**:
- LSP plugin enabled and running
- LSP server configured for the file's language mode
- LSP server executable installed and in PATH

**What It Does**:
```
1. Gets the current view
2. Calls LspPlugin.completeLsp(view)
3. Triggers LSP server completion request
4. Displays results in popup window
```

**Keyboard Shortcuts in Popup**:
- `↑ / ↓` - Navigate up/down
- `Ctrl+P / Ctrl+N` - Previous/Next (Emacs-style)
- `1-9` - Quick select item
- `0` - Select 10th item
- `Enter / Tab` - Insert selection
- `Esc` - Close popup
- `Backspace` - Remove character, refine search
- Any other char - Auto-insert and quit (if not a word char)

**Example Configuration**:
In Utilities → Global Options → Shortcuts:
- Label: "LSP Complete"
- Shortcut: `Ctrl+Space`
- Action: Macro → `macros/LSP/LSP_Complete.bsh`

**Troubleshooting**:
- See `doc/LSP_COMPLETIONS_QUICK_START.md` for detailed troubleshooting
- Check LSP server is installed: `which pyright` (Python), `which jdtls` (Java), etc.
- Check jEdit logs for errors
- Verify file language mode matches configured server

## Adding New LSP Macros

To create additional LSP macros:

1. Create new `.bsh` file in this directory
2. Call appropriate methods from `org.jedit.lsp` package
3. Test with actual LSP server
4. Document usage and keyboard shortcuts

Examples:
```bsh
// Show signature help (if implemented)
// org.jedit.lsp.LspSignatureHelp.showSignature(view, client);

// Show hover information (if implemented)  
// org.jedit.lsp.LspHover.showHover(view, client);

// Go to definition (if implemented)
// org.jedit.lsp.LspNavigate.gotoDefinition(view, client);
```

## Configuration

LSP servers are configured in:
```
org/jedit/lsp/LspConfig.java
```

Example:
```java
SERVER_COMMANDS.put("python", new String[]{"pyright-langserver", "--stdio"});
SERVER_COMMANDS.put("java", new String[]{"jdtls"});
```

## Documentation

For more information, see:
- `doc/LSP_COMPLETIONS_QUICK_START.md` - User quick start guide
- `doc/LSP_COMPLETIONS.md` - Complete feature overview
- `doc/LSP_COMPLETION_IMPLEMENTATION.md` - Technical implementation details

## Related Classes

- `org.jedit.lsp.LspPlugin` - Main plugin class
- `org.jedit.lsp.LspCompletion` - Completion UI implementation
- `org.jedit.lsp.GenericLspClient` - LSP client wrapper
- `org.jedit.lsp.LspConfig` - Server configuration

## Credits

LSP integration for jEdit by jEdit contributors, 2026.
Based on the `CompleteWord` architecture for consistency.

---

*Last Updated: April 28, 2026*

