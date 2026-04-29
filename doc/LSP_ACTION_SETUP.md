# LSP Completion Action - Setup Guide

## Overview

The **LSP Completion Action** (`lsp-complete`) provides a proper jEdit action that can be bound to any keyboard shortcut to trigger LSP-based code completions.

This follows the same pattern as the built-in "complete-word" action from jEdit's `CompleteWord` class.

## Files

### Java Files
- **CompleteLspAction.java** - Main action class
  - Extends `EditAction`
  - Implements `isEnabled()` to check if LSP server is available
  - Delegates to `LspPlugin.completeLsp()`
  - Can be bound to keyboard shortcuts

### XML Files
- **actions.xml** - Action definitions
  - Registers the "lsp-complete" action
  - Can be invoked by jEdit action system

## Setup: Bind to Keyboard Shortcut

### Step 1: Open Shortcuts Configuration
1. Open jEdit
2. Go to **Utilities** → **Global Options**
3. Click **Shortcuts** in the left panel

### Step 2: Add LSP Complete Action
In the Shortcuts dialog:
1. Click **Add** button
2. In "Action" field, type: `lsp-complete`
3. In "Shortcut" field, type your preferred shortcut (e.g., `Ctrl+Space`)
4. Click **OK** to save

### Step 3: Verify Setup
1. Open a file in a supported language (Python, Java, C++, etc.)
2. Position cursor after a word
3. Press your shortcut key
4. Verify LSP completions popup appears

## Alternative: Using Macro

If you prefer using the macro approach instead:

```bsh
// In a macro file (e.g., macros/LSP/LSP_Complete.bsh)
org.jedit.lsp.LspPlugin.completeLsp(view);
```

Or invoke the action directly:

```bsh
// Using the action system
view.getInputHandler().invokeAction("lsp-complete");
```

## What the Action Does

When invoked via shortcut, the action:

1. **Checks preconditions**:
   - Verifies view exists
   - Checks buffer is editable
   - Confirms LSP server is configured for language mode

2. **Requests completions**:
   - Gets current caret position
   - Detects word at cursor
   - Sends request to LSP server

3. **Displays results**:
   - Shows popup with completions
   - Allows keyboard/mouse selection
   - Inserts selected completion

## Configuration Requirements

### LSP Server Configuration
Ensure your language's LSP server is configured in `LspConfig.java`:

```java
SERVER_COMMANDS.put("python", new String[]{"pyright-langserver", "--stdio"});
SERVER_COMMANDS.put("java", new String[]{"jdtls"});
SERVER_COMMANDS.put("cpp", new String[]{"clangd"});
```

### LSP Server Installation
Install the LSP server executable for your language:

**Python:**
```bash
pip install pyright
```

**Java:**
```bash
# Install Eclipse JDT.LS manually or use package manager
brew install jdtls  # macOS
```

**C/C++:**
```bash
brew install llvm  # macOS (includes clangd)
sudo apt-get install clangd  # Ubuntu/Debian
```

## Keyboard Shortcuts

When the completion popup is open:

| Key | Action |
|-----|--------|
| `↑` / `↓` | Navigate up/down |
| `Ctrl+P` / `Ctrl+N` | Previous/Next (Emacs) |
| `Page Up` / `Page Down` | Navigate by page |
| `1-9` | Quick select item |
| `0` | Select 10th item |
| `Enter` / `Tab` | Insert selected completion |
| `Esc` | Close popup |
| `Backspace` | Remove character, refine |
| Type char | Narrow search, auto-close if no matches |

## Troubleshooting

### Action not appearing in Shortcuts dialog

**Solution:**
1. Verify `CompleteLspAction.java` is compiled
2. Verify `actions.xml` is in correct location
3. Restart jEdit to reload actions
4. Check jEdit error logs: **Utilities** → **View Server Console**

### Action is disabled/grayed out

**Reasons:**
- Current buffer is not editable
- Current language mode has no LSP server configured
- See `isEnabled()` method in `CompleteLspAction.java`

**Solution:**
1. Verify LSP server is configured for your language in `LspConfig.java`
2. Verify LSP server executable is installed
3. Check jEdit logs for errors

### Shortcut key not working

**Solution:**
1. Verify shortcut is bound correctly in Global Options → Shortcuts
2. Check if shortcut conflicts with other actions
3. Try different key combination
4. Look in Shortcuts dialog for conflicting bindings

### No completions appear

See main LSP Completion troubleshooting guide: `doc/LSP_COMPLETIONS_QUICK_START.md`

## Comparison: Action vs Macro

| Aspect | Action | Macro |
|--------|--------|-------|
| **Definition** | Java class | BeanShell script |
| **Compilation** | Pre-compiled | Scripted |
| **Performance** | Faster | Slightly slower |
| **Preconditions** | Can implement `isEnabled()` | Manual checks |
| **Reusability** | Via action name | Via binding |
| **Debugging** | Standard Java debugging | Script debugging |
| **Usage** | Shortcuts, menus, toolbars | Direct invocation only |
| **jEdit Integration** | Full integration | Basic integration |

## Usage Examples

### Example 1: Direct Invocation in Code
```java
// Get the view and trigger the action
View view = jEdit.getActiveView();
LspPlugin.completeLsp(view);
```

### Example 2: Via Action System
```bsh
// In a BeanShell context
view.getInputHandler().invokeAction("lsp-complete");
```

### Example 3: Keyboard Shortcut (Recommended)
1. Bind `lsp-complete` to `Ctrl+Space`
2. Press `Ctrl+Space` in editor
3. Get completions

## Implementation Details

### CompleteLspAction.java

Key methods:

```java
public CompleteLspAction() {
    super("lsp-complete");  // Action name for binding
}

@Override
public void invoke(View view) {
    LspPlugin.completeLsp(view);  // Delegate to plugin
}

@Override
public boolean isEnabled(View view) {
    // Check preconditions before allowing invocation
    if (view == null) return false;
    if (!view.getBuffer().isEditable()) return false;
    
    String modeName = view.getBuffer().getMode().getName();
    return LspConfig.isServerAvailable(modeName);
}
```

### Action Lifecycle

1. **Registration**: Action is registered with jEdit when plugin loads
2. **Binding**: User binds action to keyboard shortcut via preferences
3. **Invocation**: When shortcut pressed, `invoke()` method called
4. **Enable Check**: Before invoking, `isEnabled()` checked (action grayed out if false)
5. **Execution**: Action executes by calling `LspPlugin.completeLsp()`

## Advanced: Custom Actions

To create additional LSP actions:

```java
public class MyLspAction extends EditAction {
    public MyLspAction() {
        super("my-lsp-action");  // Unique action name
    }
    
    @Override
    public void invoke(View view) {
        // Your LSP action logic
    }
    
    @Override
    public boolean isEnabled(View view) {
        return true;  // Can customize
    }
}
```

Then register in `actions.xml`:

```xml
<ACTION NAME="my-lsp-action">
    <CODE>
        org.jedit.lsp.LspPlugin.myCustomMethod(view);
    </CODE>
</ACTION>
```

## References

- **CompleteLspAction.java** - Action implementation
- **actions.xml** - Action definitions
- **LspPlugin.java** - Plugin providing completions
- **LspCompletion.java** - UI for completions
- **CompleteWord class** - Similar action for word completions

## Related Documentation

- `doc/LSP_COMPLETIONS_QUICK_START.md` - User quick start
- `doc/LSP_COMPLETIONS.md` - Feature overview
- `doc/LSP_COMPLETION_IMPLEMENTATION.md` - Technical details

---

**Version**: 1.0  
**Created**: April 28, 2026  
**Status**: Ready for Use

