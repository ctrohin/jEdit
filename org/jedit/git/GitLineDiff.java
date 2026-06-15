/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.git;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

final class GitLineDiff {

    static final class Change {
        final int leftStart;
        final int leftEnd;
        final int rightStart;
        final int rightEnd;

        Change(int leftStart, int leftEnd, int rightStart, int rightEnd) {
            this.leftStart = leftStart;
            this.leftEnd = leftEnd;
            this.rightStart = rightStart;
            this.rightEnd = rightEnd;
        }

        boolean isInsert() {
            return rightStart >= 0 && leftStart < 0;
        }

        boolean isDelete() {
            return leftStart >= 0 && rightStart >= 0 && rightEnd < rightStart;
        }

        boolean isReplace() {
            return leftStart >= 0 && rightStart >= 0;
        }
    }

    static final class Result {
        final BitSet leftChanged = new BitSet();
        final BitSet rightChanged = new BitSet();
        final List<Change> changes = new ArrayList<>();
        final int[] leftToRight;
        final int[] rightToLeft;

        Result(int leftLineCount, int rightLineCount) {
            leftToRight = new int[leftLineCount];
            rightToLeft = new int[rightLineCount];
            for (int i = 0; i < leftLineCount; i++) {
                leftToRight[i] = -1;
            }
            for (int i = 0; i < rightLineCount; i++) {
                rightToLeft[i] = -1;
            }
        }

        boolean isLeftChanged(int line) {
            return leftChanged.get(line);
        }

        boolean isRightChanged(int line) {
            return rightChanged.get(line);
        }
    }

    private GitLineDiff() {}

    static Result compute(String leftText, String rightText) {
        List<String> leftLines = splitLines(leftText);
        List<String> rightLines = splitLines(rightText);
        Result result = new Result(leftLines.size(), rightLines.size());
        walkDiff(leftLines, rightLines, result);
        result.changes.addAll(collectChanges(leftLines, rightLines, result));
        return result;
    }

    static int mapLeftToRight(Result result, int leftLine) {
        if (leftLine < 0 || result.leftToRight.length == 0) {
            return 0;
        }
        if (leftLine >= result.leftToRight.length) {
            leftLine = result.leftToRight.length - 1;
        }
        int mapped = result.leftToRight[leftLine];
        if (mapped >= 0) {
            return mapped;
        }
        for (int i = leftLine; i >= 0; i--) {
            if (result.leftToRight[i] >= 0) {
                return result.leftToRight[i];
            }
        }
        return 0;
    }

    static int mapRightToLeft(Result result, int rightLine) {
        if (rightLine < 0 || result.rightToLeft.length == 0) {
            return 0;
        }
        if (rightLine >= result.rightToLeft.length) {
            rightLine = result.rightToLeft.length - 1;
        }
        int mapped = result.rightToLeft[rightLine];
        if (mapped >= 0) {
            return mapped;
        }
        for (int i = rightLine; i >= 0; i--) {
            if (result.rightToLeft[i] >= 0) {
                return result.rightToLeft[i];
            }
        }
        return 0;
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = text.split("\n", -1);
        List<String> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.endsWith("\r")) {
                lines.add(part.substring(0, part.length() - 1));
            } else {
                lines.add(part);
            }
        }
        if (text.charAt(text.length() - 1) == '\n' && !lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static void walkDiff(List<String> left, List<String> right, Result result) {
        int m = left.size();
        int n = right.size();
        int[][] lcs = buildLcsTable(left, right);
        int i = 0;
        int j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && left.get(i).equals(right.get(j))) {
                result.leftToRight[i] = j;
                result.rightToLeft[j] = i;
                i++;
                j++;
                continue;
            }
            if (i < m && (j >= n || lcs[i + 1][j] >= lcs[i][j + 1])) {
                result.leftChanged.set(i);
                result.leftToRight[i] = j < n ? j : n;
                i++;
            } else if (j < n) {
                result.rightChanged.set(j);
                result.rightToLeft[j] = i < m ? i : m;
                j++;
            }
        }
    }

    private static int[][] buildLcsTable(List<String> left, List<String> right) {
        int m = left.size();
        int n = right.size();
        int[][] table = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (left.get(i).equals(right.get(j))) {
                    table[i][j] = table[i + 1][j + 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i + 1][j], table[i][j + 1]);
                }
            }
        }
        return table;
    }

    private static List<Change> collectChanges(List<String> left, List<String> right,
                                               Result result) {
        List<Change> changes = new ArrayList<>();
        int m = left.size();
        int n = right.size();
        int[][] lcs = buildLcsTable(left, right);
        int i = 0;
        int j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && left.get(i).equals(right.get(j))) {
                i++;
                j++;
                continue;
            }
            int changeStartLeft = i;
            int changeStartRight = j;
            while (i < m || j < n) {
                if (i < m && j < n && left.get(i).equals(right.get(j))) {
                    break;
                }
                if (i < m && (j >= n || lcs[i + 1][j] >= lcs[i][j + 1])) {
                    i++;
                } else if (j < n) {
                    j++;
                } else {
                    break;
                }
            }
            int leftStart = changeStartLeft;
            int leftEnd = i - 1;
            int rightStart = changeStartRight;
            int rightEnd = j - 1;
            if (leftStart > leftEnd) {
                leftStart = -1;
                leftEnd = -1;
            }
            if (rightStart > rightEnd) {
                if (leftStart >= 0) {
                    rightEnd = rightStart - 1;
                } else {
                    rightStart = -1;
                    rightEnd = -1;
                }
            }
            if (leftStart >= 0 || rightStart >= 0) {
                changes.add(new Change(leftStart, leftEnd, rightStart, rightEnd));
            }
        }
        return changes;
    }
}
