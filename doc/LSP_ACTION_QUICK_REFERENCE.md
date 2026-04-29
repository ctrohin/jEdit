# Quick Reference: LSP Complete Action

## TL;DR (30 seconds)

1. **Action Name**: `lsp-complete`
2. **How to Bind**:
   - Utilities → Global Options → Shortcuts
   - Add action: `lsp-complete`
   - Set shortcut: (e.g., `Ctrl+Space`)
3. **Use It**: Press your shortcut in editor to get LSP completions

---

## Comparison: CompleteWord Action vs LSP Complete Action

| Feature | CompleteWord | LSP Complete |
|---------|--------------|--------------|
| **Action Name** | `complete-word` | `lsp-complete` |
| **Class** | `CompleteWord` class | `CompleteLspAction` class |
| **Source** | Searches open buffers | Language server |
| **Speed** | Instant | ~100-500ms |
| **Accuracy** | Low (generic) | High (language-aware) |
| **Details** | None | Full (type, documentation) |
| **Keyboard** | Same as below | Same as below |

---

## Keyboard Shortcuts (In Completion Popup)

Both actions support the same keyboard shortcuts:

| Key | Action |
|-----|--------|
| `↑` / `↓` | Navigate |
| `Enter` / `Tab` | Insert |
| `1-9` | Quick select |
| `0` | Select 10th |
| `Esc` | Close |
| `Backspace` | Refine word |

---

## Setup in 3 Steps

### Step 1: Open Preferences
```
Utilities → Global Options → Shortcuts
```

### Step 2: Add Action
- Click "Add"
- Action: `lsp-complete`
- Shortcut: `Ctrl+Space` (or your preference)
- Click OK

### Step 3: Use It
```
1. Open a .py/.java/.cpp file
2. Type a partial word
3. Press Ctrl+Space
4. Select from popup
```

---

## Enable/Disable Logic

The action is **enabled** when:
- ✅ Current view exists
- ✅ Buffer is editable
- ✅ Language mode has LSP server configured

The action is **disabled** (grayed out) when:
- ❌ No view or buffer
- ❌ Buffer is read-only
- ❌ Language has no LSP server

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Action not in list | Compile code, restart jEdit |
| Action disabled | LSP server not configured for language |
| No completions | LSP server not installed/running |
| Shortcut not working | Check for conflicts in Shortcuts dialog |

---

## Alternative: Use Macro

If you prefer the macro approach:

**File**: `macros/LSP/LSP_Complete.bsh`
```bsh
org.jedit.lsp.LspPlugin.completeLsp(view);
```

Bind this macro to a shortcut instead of the action.

---

## Files

| File | Purpose |
|------|---------|
| `org/jedit/lsp/CompleteLspAction.java` | Action class |
| `org/jedit/lsp/actions.xml` | Action definitions |
| `macros/LSP/LSP_Complete.bsh` | Alternative: macro |
| `doc/LSP_ACTION_SETUP.md` | Detailed setup guide |

---

## Full Setup Guide

See: [LSP_ACTION_SETUP.md](doc/LSP_ACTION_SETUP.md)

---

## Similar to CompleteWord

Just like CompleteWord has an action you can bind to Ctrl+Shift+C, LSP Complete action can be bound to Ctrl+Space (or any key).

**CompleteWord**:
- Ctrl+Shift+C → `complete-word` action → Popup with buffer words

**LSP Complete**:
- Ctrl+Space → `lsp-complete` action → Popup with LSP completions

---

*Version 1.0 - April 28, 2026*

