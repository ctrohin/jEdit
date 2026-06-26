/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 */

package org.jedit.cursor;

public final class CursorExchange {

    public final String query;
    public final String response;
    public final long timestamp;

    public CursorExchange(String query, String response, long timestamp) {
        this.query = query != null ? query : "";
        this.response = response != null ? response : "";
        this.timestamp = timestamp;
    }
}
