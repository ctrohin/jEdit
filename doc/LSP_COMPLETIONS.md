# LSP-Based Completions in jEdit

## Overview

The `LspCompletion` class provides Language Server Protocol (LSP) based code completions in jEdit, similar to how the `CompleteWord` class provides word-based completions. This integration leverages LSP servers to provide intelligent, context-aware completions for various programming languages.

## Architecture

### Components

1. **LspCompletion** (`org.jedit.lsp.LspCompletion`)
   - Extends `CompletionPopup` to provide a UI for LSP completions
   - Implements the `Candidates` interface to supply completion items to the popup
   - Handles keyboard input and selection within the completion popup
   - Asynchronously requests completions from the LSP server

2. **LspPlugin** (`org.jedit.lsp.LspPlugin`)
   - Main plugin class managing LSP client lifecycle
   - Provides `completeLsp(View)` static method to trigger LSP completions
   - Manages LSP clients per language mode

### How It Works

1. **Initialization**: When `LspCompletion.completeLsp(View view, GenericLspClient lspClient)` is called:
   - Determines the word to complete at the caret position
   - Builds a `CompletionParams` request with the current position
   - Sends the request asynchronously to the LSP server

2. **Completion Request**: 
   - Uses LSP4J library to communicate with the language server
   - Server responds with either `List<CompletionItem>` or `CompletionList`
   - Items include label, detail, kind, and insert text

3. **UI Display**:
   - Results are displayed in a popup window positioned near the caret
   - User can navigate with arrow keys or number keys
   - Completion is inserted when selected (Enter, Tab, or digit key)

4. **Integration**: 
   - The popup intercepts keyboard events like `CompleteWord` does
   - Supports backspace to reduce the completion word
   - Supports typing additional characters to filter results

## Usage

### Via BeanShell Macro

Users can create a macro bound to a keyboard shortcut (e.g., Ctrl+Space):

```bsh
// LSP_Complete.bsh
org.jedit.lsp.LspPlugin.completeLsp(view);
```

### Via Java Code

```java
import org.jedit.lsp.LspCompletion;
import org.jedit.lsp.LspPlugin;
import org.gjt.sp.jedit.View;

// In your action handler:
LspPlugin.completeLsp(view);
```

## Comparison with CompleteWord

### Similarities
- Both extend `CompletionPopup` for consistent UI
- Both implement the `Candidates` interface
- Both support keyboard navigation (arrows, numbers, etc.)
- Both support backspace for narrowing completions
- Both position the popup near the caret

### Differences

| Feature | CompleteWord | LspCompletion |
|---------|--------------|---------------|
| Completion Source | Text in open buffers + keywords | LSP server |
| Context Awareness | Limited (keywords only) | Full language semantics |
| Async Support | Synchronous | Asynchronous |
| Language Support | All (generic) | Per-language (configured) |
| Performance | Instant | Depends on server |
| Richness | Basic text + keyword indication | Full completion items with kind/detail |

## Key Implementation Details

### Word Detection
```java
String word = getWordToComplete(buffer, caretLine, caret, noWordSep);
```
Uses the same word detection logic as `CompleteWord`, respecting `noWordSep` property and keyword map.

### Async Handling
```java
CompletableFuture<Either<List<CompletionItem>, CompletionList>> future = 
    lspClient.getServer().getTextDocumentService().completion(params);

future.thenAccept(result -> {
    // Display results on UI thread
    SwingUtilities.invokeLater(() -> {
        new LspCompletion(...);
    });
}).exceptionally(ex -> {
    // Handle errors
});
```

### Completion Item Display
- Shows label with optional kind indicator (e.g., "myFunction [Function]")
- Shows detail/documentation on selection
- Supports numeric selection (1-9, 0 for 10th)
- Highlights keywords vs. other items

## Configuration

LSP servers and their launch commands are configured in `LspConfig`:

```java
public static final Map<String, String[]> SERVER_COMMANDS = new HashMap<>();
// Example:
SERVER_COMMANDS.put("java", new String[]{"java", "-jar", "path/to/server.jar"});
```

## Error Handling

- If LSP server is not available for the language, user feedback is provided
- Network/server errors are caught and logged
- Graceful fallback if completion request fails

## Future Enhancements

1. **Incremental Filtering**: Filter existing results as user types
2. **Server Capabilities**: Check server capabilities before requesting completion
3. **Trigger Characters**: Auto-trigger on specific characters (`.`, `::`, etc.)
4. **Documentation**: Display rich documentation/signatures
5. **Snippet Support**: Expand snippet templates from completion items
6. **Resolve Details**: Request additional details for selected items
7. **Caching**: Cache results briefly to avoid redundant requests

## Related Files

- `org/jedit/lsp/LspCompletion.java` - Main completion UI class
- `org/jedit/lsp/LspPlugin.java` - Plugin integration
- `org/jedit/lsp/GenericLspClient.java` - LSP client wrapper
- `org/gjt/sp/jedit/gui/CompletionPopup.java` - Base popup class
- `org/gjt/sp/jedit/gui/CompleteWord.java` - Word-based completion reference
- `macros/LSP/LSP_Complete.bsh` - Example macro for triggering completions

## Requirements

- LSP4J library (org.eclipse.lsp4j)
- jEdit with active LSP plugin and configured LSP server
- Supported language mode with corresponding LSP server

