package com.moneyclaritytech.pocketforge;

public final class JavaFormatter {
    private JavaFormatter() {}

    /**
     * Lightweight brace-aware formatter for phone practice. It intentionally avoids
     * rewriting tokens or expressions; it only normalizes leading indentation.
     */
    public static String format(String source) {
        if (source == null || source.isEmpty()) return "";
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 32);
        int depth = 0;
        boolean inBlockComment = false;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) {
                if (i < lines.length - 1) out.append('\n');
                continue;
            }

            int closeBefore = startsWithClosingBrace(raw) ? 1 : 0;
            int lineDepth = Math.max(0, depth - closeBefore);
            for (int s = 0; s < lineDepth * 4; s++) out.append(' ');
            out.append(raw);
            if (i < lines.length - 1) out.append('\n');

            int delta = 0;
            boolean inString = false;
            boolean inChar = false;
            boolean escape = false;
            for (int c = 0; c < raw.length(); c++) {
                char ch = raw.charAt(c);
                char next = c + 1 < raw.length() ? raw.charAt(c + 1) : '\0';

                if (inBlockComment) {
                    if (ch == '*' && next == '/') {
                        inBlockComment = false;
                        c++;
                    }
                    continue;
                }
                if (!inString && !inChar && ch == '/' && next == '*') {
                    inBlockComment = true;
                    c++;
                    continue;
                }
                if (!inString && !inChar && ch == '/' && next == '/') break;

                if (escape) {
                    escape = false;
                    continue;
                }
                if ((inString || inChar) && ch == '\\') {
                    escape = true;
                    continue;
                }
                if (!inChar && ch == '"') {
                    inString = !inString;
                    continue;
                }
                if (!inString && ch == '\'') {
                    inChar = !inChar;
                    continue;
                }
                if (!inString && !inChar) {
                    if (ch == '{') delta++;
                    else if (ch == '}') delta--;
                }
            }
            depth = Math.max(0, depth + delta);
        }
        return out.toString();
    }

    private static boolean startsWithClosingBrace(String s) {
        return s.startsWith("}") || s.startsWith("]") || s.startsWith(")");
    }
}
