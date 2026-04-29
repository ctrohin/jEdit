# LSP Completions Implementation Guide

## Summary

This document describes the implementation of LSP (Language Server Protocol) based completions in jEdit, following a similar pattern to the existing `CompleteWord` feature.

## Files Created/Modified

### New Files

1. **LspCompletion.java** (`org/jedit/lsp/LspCompletion.java`)
   - Main class providing LSP-based completion popup
   - Extends `CompletionPopup` for consistent UI with `CompleteWord`
   - Implements asynchronous request handling to LSP server
   - 380+ lines

2. **LSP_Complete.bsh** (`macros/LSP/LSP_Complete.bsh`)
   - BeanShell macro for triggering LSP completions
   - Can be bound to keyboard shortcut (e.g., Ctrl+Space alternative)

### Modified Files

1. **LspPlugin.java** (`org/jedit/lsp/LspPlugin.java`)
   - Added `completeLsp(View)` static method as entry point
   - Changed `clients` field from private to package-protected
   - Added import for `SwingUtilities`

## Implementation Flow

### 1. Triggering Completion

User triggers completion via macro/action:
```
User Ctrl+Space (via macro)
  ↓
LspPlugin.completeLsp(View view)
  ↓
Gets active buffer's mode
  ↓
Finds LSP client for that mode
  ↓
Calls LspCompletion.completeLsp(view, lspClient)
```

### 2. Word Detection

```
LspCompletion.completeLsp()
  ↓
getWordToComplete(buffer, caretLine, caret, noWordSep)
  ↓
Uses TextUtilities.findWordStart() to locate word boundaries
  ↓
Returns word string (or null if not valid)
```

Logic is identical to `CompleteWord.getWordToComplete()`:
- Checks character before caret
- Respects `noWordSep` property and keyword map
- Uses `TextUtilities` for word boundary detection

### 3. LSP Request

```
requestLspCompletions()
  ↓
Builds CompletionParams with:
  - Current document URI
  - Current line and character (Position)
  - Completion context (trigger kind)
  ↓
lspClient.getServer().getTextDocumentService().completion(params)
  ↓
Returns CompletableFuture<Either<List<CompletionItem>, CompletionList>>
```

The request is **asynchronous** to avoid blocking the UI:

```java
future.thenAccept(result -> {
    // Handle result on completion
    SwingUtilities.invokeLater(() -> {
        // Display popup on UI thread
    });
}).exceptionally(ex -> {
    // Handle errors gracefully
});
```

### 4. Result Display

```
Response received from LSP server
  ↓
Parse Either<List, CompletionList> to extract items
  ↓
Create LspCompletion popup with items
  ↓
LspCompletionCandidates implements Candidates interface
  ↓
Popup displays with:
  - Completion label + kind indicator
  - Sortable by number (1-9, 0)
  - Navigable with arrow keys
  - Description on selection
```

### 5. Selection & Insertion

User selects completion:
```
competedCompletion.complete(int index)
  ↓
Get CompletionItem at index
  ↓
Get insertText (or label if not set)
  ↓
Calculate insertion:
  String insertion = insertText.substring(word.length());
  ↓
textArea.replaceSelection(insertion)
  ↓
Popup disposed
```

## Key Design Patterns

### 1. Async-First Design
- Requests don't block UI
- Uses CompletableFuture's `thenAccept()` for non-blocking handling
- Exception handling via `exceptionally()`

### 2. Reusable UI
- Extends `CompletionPopup` instead of reimplementing
- Implements `Candidates` interface for consistency
- Supports same keyboard shortcuts as `CompleteWord`

### 3. Word Detection Consistency
- Uses same `noWordSep` logic as `CompleteWord`
- Respects keyword map for consistency
- Same word boundary algorithms

### 4. LSP Integration
- Leverages LSP4J library for protocol handling
- Works with any LSP server speaking standard protocol
- Maps jEdit language modes to LSP servers via `LspConfig`

## Comparison Matrix

| Aspect | CompleteWord | LspCompletion |
|--------|--------------|---------------|
| **Source** | Open buffers + keywords | LSP server |
| **Scope** | Whole project | Language-aware |
| **Async** | Synchronous | Asynchronous |
| **Latency** | Instant | ~100-500ms (server dependent) |
| **Accuracy** | Generic | Language-specific |
| **Detail** | Minimal | Full (kind, detail, doc) |
| **Config** | None needed | LSP server required |
| **UI** | CompletionPopup | CompletionPopup (same) |
| **Keyboard** | Same shortcuts | Same shortcuts |

## Class Structure

```
CompletionPopup (abstract)
  ├── CompleteWord extends CompletionPopup
  │   └── Words implements Candidates
  │
  └── LspCompletion extends CompletionPopup
      └── LspCompletionCandidates implements Candidates

GenericLspClient
  └── LanguageServer (from LSP4J)
      └── TextDocumentService
          └── completion(CompletionParams)
```

## Example Usage

### 1. Via Macro
```bsh
// In a BeanShell macro:
org.jedit.lsp.LspPlugin.completeLsp(view);
```

### 2. Via Action Handler
```java
// In an EditAction:
org.jedit.lsp.LspPlugin.completeLsp(view);
```

### 3. Programmatically
```java
View view = jEdit.getActiveView();
LspPlugin.completeLsp(view);

// Or directly:
LspClientMeta meta = ...; // Get the meta for your mode
LspCompletion.completeLsp(view, meta.getClient());
```

## Configuration

No configuration needed beyond `LspConfig.SERVER_COMMANDS` which defines:
- Which language modes have LSP support
- Which command starts the LSP server for that mode

Example:
```java
SERVER_COMMANDS.put("java", new String[]{"jdtls"});
SERVER_COMMANDS.put("python", new String[]{"pyright-langserver", "--stdio"});
```

## Error Handling

The implementation handles:
- Server not available for mode → Display error feedback
- Server crashes/timeout → Log error, show feedback
- No completions returned → Display error feedback
- Network/IPC errors → Log and gracefully degrade

## Performance Considerations

1. **Async Requests**: Non-blocking to keep UI responsive
2. **Error Timeouts**: Futures should include timeout handling (can be added)
3. **Caching**: Could cache results for repeated words (future enhancement)
4. **Filtering**: Currently shows all server results (could filter based on prefix)

## Testing

To test the implementation:

1. Configure an LSP server in `LspConfig`
2. Open a file in that language
3. Bind `LSP_Complete.bsh` to a keyboard shortcut
4. Position cursor after a word
5. Hit the shortcut key
6. Verify completion popup appears
7. Navigate and select using keyboard

## Future Enhancements

1. **Auto-trigger**: Trigger on trigger characters (`.`, `:`, etc.)
2. **Incremental Filter**: Filter results as user types
3. **Signature Help**: Show function signatures
4. **Go to Definition**: Click to navigate to definition
5. **Hover Info**: Display documentation on hover
6. **Snippet Expansion**: Expand template variables
7. **Resolve Items**: Request additional details on demand
8. **Cache Management**: Brief caching of recent completions
9. **Type Checking**: Show type information
10. **Import Suggestions**: Auto-import on selection

## Dependencies

- **LSP4J** (`org.eclipse.lsp4j`) - LSP protocol client library
- **jEdit Core** - Editor and UI framework
- **CompletionPopup** - Base UI component

## References

- LSP Specification: https://microsoft.github.io/language-server-protocol/
- LSP4J JavaDoc: https://javadoc.io/doc/org.eclipse.lsp4j/org.eclipse.lsp4j/3.17.0/
- jEdit Plugin Development: https://www.jedit.org/

## Implementation Notes

- Word detection uses same algorithm as `CompleteWord` for consistency
- Popup positioning respects screen bounds (see `fitInScreen()` method)
- Keyboard handling follows standard Swing/jEdit patterns
- Thread safety maintained via `SwingUtilities.invokeLater()`
- LSP server lifecycle managed by `LspPlugin`

