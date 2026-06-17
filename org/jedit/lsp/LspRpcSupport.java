/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;

/**
 * Helpers for interpreting LSP JSON-RPC failures.
 */
final class LspRpcSupport {

    private LspRpcSupport() {}

    static boolean isUnsupportedMethod(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof UnsupportedOperationException) {
                return true;
            }
            if (current instanceof ResponseErrorException responseError) {
                if (responseError.getResponseError().getCode() == -32601) {
                    return true;
                }
                String message = responseError.getMessage();
                if (message != null && message.contains("Unknown method")) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && message.contains("Unknown method")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
