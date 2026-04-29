# LSP Completions Quick Start Guide

## What is LSP Completion?

LSP (Language Server Protocol) completions provide intelligent, context-aware code completions powered by language servers. Unlike the built-in `Complete Word` feature that searches open buffers, LSP completions understand your code's structure and semantics.

## Quick Start (5 minutes)

### Step 1: Ensure LSP Plugin is Loaded
- Check that the LSP plugin is enabled in jEdit
- The plugin should auto-load if properly installed

### Step 2: Verify LSP Server is Configured
- Check `org/jedit/lsp/LspConfig.java` for your language
- Available servers:
  - **Python**: `pyright-langserver`
  - **Java**: `jdtls`
  - **C/C++**: `clangd`
  - **TypeScript/JS**: `typescript-language-server`
  - **Rust**: `rust-analyzer`

### Step 3: Install Language Server (if needed)
Example for Python:
```bash
pip install pyright
```

### Step 4: Create Keyboard Shortcut
1. Open jEdit preferences
2. Go to "Shortcuts" section
3. Create new shortcut binding to macro: `macros/LSP/LSP_Complete.bsh`
4. Assign keyboard shortcut (e.g., `Ctrl+Space`)

### Step 5: Use LSP Completions
1. Open a file in a supported language (Python, Java, C++, etc.)
2. Position cursor after a word
3. Press your shortcut key (e.g., `Ctrl+Space`)
4. Wait for completions popup to appear (usually instant to ~500ms)
5. Select completion with arrow keys or type number
6. Press Enter/Tab to insert

## Keyboard Shortcuts in Completion Popup

| Key | Action |
|-----|--------|
| `↑` / `↓` | Navigate completions |
| `Ctrl+P` / `Ctrl+N` | Previous/Next (Emacs-style) |
| `1-9` | Quick select (if enabled) |
| `0` | Select 10th item |
| `Enter` / `Tab` | Insert selected completion |
| `Esc` | Close popup |
| `Backspace` | Remove last typed char, refine |
| `Typing` | Narrows search, auto-closes if no matches |

## Example Workflow

### Python Example
```python
import os
# Position cursor here after "os."
# Press Ctrl+Space
# Get completions like: path, getcwd, environ, list, ...
```

### Java Example
```java
public class MyClass {
    public void test() {
        String s = "hello";
        // Position after "s."
        // Press Ctrl+Space  
        // Get completions like: length, charAt, substring, ...
    }
}
```

## Troubleshooting

### No Completions Appear

**Problem**: Popup doesn't show when shortcut is pressed

**Solutions**:
1. Verify LSP server is installed: `which jdtls` (Java), `which pyright` (Python), etc.
2. Check jEdit logs for errors: `Edit` → `Utilities` → `jEdit Server` → View logs
3. Verify file is in supported language mode (check mode at bottom of editor)
4. Check `LspConfig.java` includes your language

### Completions Are Slow

**Problem**: Takes 1-2 seconds to show completions

**Solutions**:
1. This is normal - LSP servers can take time
2. Check if server is starting fresh (first completion slower)
3. For better performance, ensure project is not too large
4. Some servers have overhead starting up

### Wrong Language Mode

**Problem**: Completions don't work for a file type

**Solutions**:
1. Check mode at bottom right of editor
2. If mode is wrong, change it: `Utilities` → `Buffer Options` → Select mode
3. Ensure language is configured in `LspConfig.SERVER_COMMANDS`

### "LSP server not available"

**Problem**: See message "LSP server not available for mode..."

**Solutions**:
1. Language isn't configured in `LspConfig.java`
2. Language server executable isn't installed
3. Server isn't in PATH (needs full path in config)

## Configuration Examples

### Setup for Python

1. **Install Pyright**:
   ```bash
   pip install pyright
   ```

2. **Verify in PATH**:
   ```bash
   which pyright-langserver
   # Should output something like /usr/local/bin/pyright-langserver
   ```

3. **Test completion**: Open `.py` file and try shortcut

### Setup for Java

1. **Install Eclipse JDT.LS**:
   ```bash
   # On macOS/Linux
   curl -L https://download.eclipse.org/jdtls/releases/latest/jdt-language-server-latest.tar.gz | tar xz
   ```

2. **Configure path in LspConfig.java**:
   ```java
   SERVER_COMMANDS.put("java", new String[]{
       "/path/to/eclipse/bin/jdtls"
   });
   ```

3. **Test completion**: Open `.java` file and try shortcut

### Setup for C/C++

1. **Install clangd**:
   ```bash
   # On macOS (Homebrew)
   brew install llvm
   
   # On Ubuntu/Debian
   sudo apt-get install clangd
   ```

2. **Server is already configured**, just test it

## Performance Tips

1. **First Completion Slower**: LSP server initialization takes time (1-2s)
2. **Subsequent Completions Faster**: Server stays running, future completions instant
3. **Large Projects**: May take longer if project is large
4. **Network**: If using remote LSP server, network latency applies

## Comparison: CompleteWord vs LSP Completions

### CompleteWord (Built-in)
- **Source**: Searches open buffer files
- **Speed**: Instant (synchronous)
- **Accuracy**: Low (no language understanding)
- **Details**: None
- **When to use**: Generic word hunting

### LSP Completions
- **Source**: Language server (language-aware)
- **Speed**: Fast (usually <500ms)
- **Accuracy**: High (understands code structure)
- **Details**: Shows types, documentation
- **When to use**: When precision matters

## Advanced: Custom Language Setup

To add support for a new language:

1. **Edit** `org/jedit/lsp/LspConfig.java`:
   ```java
   // Add your language
   SERVER_COMMANDS.put("mylang", new String[]{
       "/path/to/langserver", "--stdio"
   });
   ```

2. **Restart jEdit** for changes to take effect

3. **Create macro** for easy invocation (or bind existing one)

## Keyboard Shortcut Setup (Detailed)

### Option 1: Global Shortcut
1. `Utilities` → `Global Options` → `Shortcuts`
2. Click "Add"
3. Label: "LSP Complete"
4. Shortcut: `Ctrl+Space` (or preferred)
5. Action: Macro → `macros/LSP/LSP_Complete.bsh`
6. Click "OK" and save

### Option 2: Mode-Specific Shortcut
1. `Utilities` → `Global Options` → `Editing`
2. Select your mode (Python, Java, etc.)
3. Go to "Shortcuts" tab
4. Set shortcut like above

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| "Package not found" in logs | Install LSP server, add to PATH |
| Popup appears but is empty | Server returned no completions (valid) |
| Server crashes | Check server installation, see logs |
| Slow first completion | Normal - server needs initialization |
| Completions don't match code | Server may need project context/config |
| Wrong completions | Some servers need build/index files |

## Next Steps

1. **Set up keyboard shortcut** (see above)
2. **Try on different file types** 
3. **Check status command** if available (e.g., Java imports)
4. **Report issues** in jEdit forums/bugtracker

## Resources

- **LSP Specification**: https://microsoft.github.io/language-server-protocol/
- **jEdit Documentation**: https://www.jedit.org/
- **Language Server Directory**: https://langserver.org/
- **Implementation Details**: See `doc/LSP_COMPLETIONS.md`

## Example Videos/Demos

Create your own demo by:
1. Setting up a language server
2. Opening a file in that language
3. Positioning after a partial word
4. Pressing LSP Complete shortcut
5. Selection from popup with keyboard/mouse

---

**Last Updated**: April 28, 2026
**LSP4J Version**: Check build.xml dependencies
**jEdit Minimum Version**: Check plugin requirements

