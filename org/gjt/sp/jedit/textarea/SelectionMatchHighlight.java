/*
 * SelectionMatchHighlight.java - Highlight other instances of selected text
 * :tabSize=4:indentSize=4:noTabs=false:
 *
 * Copyright (C) 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.textarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.syntax.KeywordMap;

/**
 * Highlights every occurrence of the selected text, or when nothing is selected,
 * every occurrence of the word at the caret. Selection matches use a stronger
 * tint; caret-symbol matches use a lighter one.
 */
public class SelectionMatchHighlight extends TextAreaExtension
{
	private static final int[] NO_MATCHES = new int[0];
	private static final int SELECTION_MATCH_ALPHA = 128;
	private static final int CARET_SYMBOL_ALPHA = 70;

	private final JEditTextArea textArea;
	private String cachedNeedle;
	private int[] cachedMatches = NO_MATCHES;
	private HighlightTarget lastRepaintTarget;
	private JEditBuffer attachedBuffer;

	private final CaretListener caretListener = e -> onSelectionChanged();

	private final BufferAdapter bufferListener = new BufferAdapter()
	{
		@Override
		public void contentInserted(JEditBuffer buffer, int startLine,
			int offset, int numLines, int length)
		{
			onBufferChanged();
		}

		@Override
		public void contentRemoved(JEditBuffer buffer, int startLine,
			int offset, int numLines, int length)
		{
			onBufferChanged();
		}
	};

	public SelectionMatchHighlight(JEditTextArea textArea)
	{
		this.textArea = textArea;
		textArea.getPainter().addExtension(
			TextAreaPainter.BELOW_SELECTION_LAYER, this);
		textArea.addCaretListener(caretListener);
		attachBufferListener(textArea.getBuffer());
	}

	void dispose()
	{
		textArea.removeCaretListener(caretListener);
		detachBufferListener();
		textArea.getPainter().removeExtension(this);
	}

	@Override
	public void paintValidLine(Graphics2D gfx, int screenLine,
		int physicalLine, int start, int end, int y)
	{
		attachBufferListener(textArea.getBuffer());

		HighlightTarget target = getHighlightTarget();
		if (target == null)
		{
			return;
		}

		int[] matches = getMatches(target.needle());
		if (matches.length == 0)
		{
			return;
		}

		TextAreaPainter painter = textArea.getPainter();
		Color selectionColor = textArea.isMultipleSelectionEnabled()
			? painter.getMultipleSelectionColor()
			: painter.getSelectionColor();
		gfx.setColor(new Color(
			selectionColor.getRed(),
			selectionColor.getGreen(),
			selectionColor.getBlue(),
			target.alpha()));

		int lineHeight = painter.getLineHeight();
		int needleLength = target.needle().length();
		for (int matchStart : matches)
		{
			int matchEnd = matchStart + needleLength;
			if (matchEnd <= start || matchStart >= end)
			{
				continue;
			}
			paintMatch(gfx, screenLine, physicalLine, matchStart, matchEnd,
				start, end, y, lineHeight);
		}
	}

	private record HighlightTarget(String needle, int alpha) {}

	private HighlightTarget getHighlightTarget()
	{
		String selected = getSelectedNeedle();
		if (selected != null)
		{
			return new HighlightTarget(selected, SELECTION_MATCH_ALPHA);
		}
		String word = getWordAtCaret();
		if (word != null)
		{
			return new HighlightTarget(word, CARET_SYMBOL_ALPHA);
		}
		return null;
	}

	private String getSelectedNeedle()
	{
		Selection selection = textArea.getSelectionAtOffset(
			textArea.getCaretPosition());
		if (selection == null || selection instanceof Selection.Rect)
		{
			return null;
		}

		int start = selection.getStart();
		int end = selection.getEnd();
		if (start >= end)
		{
			return null;
		}

		return textArea.getBuffer().getText(start, end - start);
	}

	private String getWordAtCaret()
	{
		JEditBuffer jeditBuffer = textArea.getBuffer();
		if (!(jeditBuffer instanceof Buffer buffer))
		{
			return null;
		}

		int caret = textArea.getCaretPosition();
		int caretLine = textArea.getCaretLine();
		CharSequence line = buffer.getLineSegment(caretLine);
		int dot = caret - buffer.getLineStartOffset(caretLine);
		if (line.isEmpty())
		{
			return null;
		}

		KeywordMap keywordMap = buffer.getKeywordMapAtOffset(caret);
		String noWordSep = getNonAlphaNumericWordChars(buffer, keywordMap);

		int index = dot;
		if (index >= line.length())
		{
			index = line.length() - 1;
		}
		if (index < 0)
		{
			return null;
		}

		char ch = line.charAt(index);
		if (!isWordChar(ch, noWordSep))
		{
			if (index == 0)
			{
				return null;
			}
			index--;
			ch = line.charAt(index);
			if (!isWordChar(ch, noWordSep))
			{
				return null;
			}
		}

		boolean joinNonWordChars = textArea.getJoinNonWordChars();
		int wordStart = TextUtilities.findWordStart(line, index, noWordSep,
			joinNonWordChars, false, false);
		int wordEnd = TextUtilities.findWordEnd(line, index + 1, noWordSep,
			joinNonWordChars, false, false);
		if (wordEnd <= wordStart)
		{
			return null;
		}

		return buffer.getText(
			buffer.getLineStartOffset(caretLine) + wordStart,
			wordEnd - wordStart);
	}

	private static boolean isWordChar(char ch, String noWordSep)
	{
		return Character.isLetterOrDigit(ch) || noWordSep.indexOf(ch) != -1;
	}

	private static String getNonAlphaNumericWordChars(Buffer buffer,
		KeywordMap keywordMap)
	{
		String noWordSep = buffer.getStringProperty("noWordSep");
		if (noWordSep == null)
		{
			noWordSep = "";
		}
		if (keywordMap != null)
		{
			String keywordNoWordSep = keywordMap.getNonAlphaNumericChars();
			if (keywordNoWordSep != null)
			{
				noWordSep += keywordNoWordSep;
			}
		}
		return noWordSep;
	}

	private int[] getMatches(String needle)
	{
		if (needle.equals(cachedNeedle))
		{
			return cachedMatches;
		}

		cachedNeedle = needle;
		cachedMatches = findAllMatches(textArea.getBuffer(), needle);
		return cachedMatches;
	}

	private static int[] findAllMatches(JEditBuffer buffer, String needle)
	{
		int bufferLength = buffer.getLength();
		if (bufferLength == 0 || needle.isEmpty())
		{
			return NO_MATCHES;
		}

		String haystack = buffer.getText(0, bufferLength);
		List<Integer> matches = new ArrayList<>();
		int from = 0;
		while (from <= haystack.length() - needle.length())
		{
			int index = haystack.indexOf(needle, from);
			if (index < 0)
			{
				break;
			}
			matches.add(index);
			from = index + needle.length();
		}

		int[] result = new int[matches.size()];
		for (int i = 0; i < matches.size(); i++)
		{
			result[i] = matches.get(i);
		}
		return result;
	}

	private void paintMatch(Graphics2D gfx, int screenLine, int physicalLine,
		int matchStart, int matchEnd, int lineStart, int lineEnd, int y,
		int lineHeight)
	{
		if (matchEnd <= lineStart || matchStart >= lineEnd)
		{
			return;
		}

		int segStart = Math.max(matchStart, lineStart);
		int segEnd = Math.min(matchEnd, lineEnd);
		if (segEnd <= segStart)
		{
			return;
		}

		JEditBuffer buffer = textArea.getBuffer();
		int lineStartOffset = buffer.getLineStartOffset(physicalLine);

		int matchStartLine = buffer.getLineOfOffset(matchStart);
		int matchEndLine = buffer.getLineOfOffset(matchEnd);
		int matchStartScreenLine = textArea.displayManager.isLineVisible(matchStartLine)
			? textArea.getScreenLineOfOffset(matchStart)
			: -1;
		int matchEndScreenLine = textArea.displayManager.isLineVisible(matchEndLine)
			? textArea.getScreenLineOfOffset(matchEnd)
			: -1;

		int x1;
		int x2;
		if (matchStartScreenLine == matchEndScreenLine
			&& matchStartScreenLine != -1)
		{
			Point p1 = textArea.offsetToXY(physicalLine, segStart - lineStartOffset);
			Point p2 = textArea.offsetToXY(physicalLine, segEnd - lineStartOffset);
			if (p1 == null)
			{
				return;
			}
			x1 = p1.x;
			x2 = p2 != null ? p2.x : textArea.getWidth();
		}
		else if (screenLine == matchStartScreenLine)
		{
			Point p1 = textArea.offsetToXY(physicalLine, segStart - lineStartOffset);
			if (p1 == null)
			{
				return;
			}
			x1 = p1.x;
			x2 = textArea.getWidth();
		}
		else if (screenLine == matchEndScreenLine)
		{
			x1 = 0;
			Point p2 = textArea.offsetToXY(physicalLine, segEnd - lineStartOffset);
			x2 = p2 != null ? p2.x : textArea.getWidth();
		}
		else
		{
			x1 = 0;
			x2 = textArea.getWidth();
		}

		if (x2 <= x1)
		{
			x2 = x1 + 2;
		}

		gfx.fillRect(x1, y, x2 - x1, lineHeight);
	}

	private void attachBufferListener(JEditBuffer buffer)
	{
		if (buffer == attachedBuffer)
		{
			return;
		}
		detachBufferListener();
		if (buffer != null)
		{
			buffer.addBufferListener(bufferListener);
			attachedBuffer = buffer;
		}
	}

	private void detachBufferListener()
	{
		if (attachedBuffer != null)
		{
			attachedBuffer.removeBufferListener(bufferListener);
			attachedBuffer = null;
		}
		invalidateCache();
	}

	private void onSelectionChanged()
	{
		HighlightTarget target = getHighlightTarget();
		if (Objects.equals(target, lastRepaintTarget))
		{
			return;
		}

		lastRepaintTarget = target;
		invalidateCache();
		repaintVisibleLines();
	}

	private void onBufferChanged()
	{
		invalidateCache();
		if (lastRepaintTarget != null)
		{
			repaintVisibleLines();
		}
	}

	private void repaintVisibleLines()
	{
		int visibleLines = textArea.getVisibleLines();
		if (visibleLines > 0)
		{
			textArea.invalidateScreenLineRange(0, visibleLines - 1);
		}
		else
		{
			textArea.getPainter().repaint();
		}
	}

	private void invalidateCache()
	{
		cachedNeedle = null;
		cachedMatches = NO_MATCHES;
	}
}
