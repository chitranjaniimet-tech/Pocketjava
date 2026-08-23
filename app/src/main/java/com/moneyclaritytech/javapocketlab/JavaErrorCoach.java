package com.moneyclaritytech.javapocketlab;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns the most common ECJ diagnostics into short, actionable learner guidance. */
final class JavaErrorCoach {
    private static final Pattern ERROR_LINE = Pattern.compile("ERROR in .*?\\(at line (\\d+)\\)");
    private static final Pattern TOKEN_EXTENDS = Pattern.compile("Syntax error on token \\\"([^\\\"]+)\\\", extends expected");
    private static final Pattern TYPE_MISMATCH = Pattern.compile("Type mismatch: cannot convert from (.+?) to (.+)");
    private static final Pattern UNDEFINED_METHOD = Pattern.compile("The method (.+?) is undefined");

    private JavaErrorCoach() {}

    static String addGuidance(String source, String diagnostics) {
        String raw = diagnostics == null ? "" : diagnostics.trim();
        if (raw.isEmpty()) return raw;

        List<String> tips = new ArrayList<>();
        int lineNumber = firstLineNumber(raw);
        String sourceLine = sourceLine(source, lineNumber);
        Matcher extendsToken = TOKEN_EXTENDS.matcher(raw);

        if (extendsToken.find()) {
            String token = extendsToken.group(1);
            tips.add("Java keywords are case-sensitive. Replace `" + token
                    + "` with lowercase `extends`.");
            if (!sourceLine.isEmpty()) {
                tips.add("On this line, change `? " + token + "` to `? extends`.");
            }
        } else if (raw.contains("Cannot infer type arguments for")) {
            tips.add("Java cannot determine the generic type automatically here. "
                    + "For recursive generic bounds, create a named concrete class or write the type explicitly instead of using `<>`.");
        } else if (raw.contains("is not applicable for the arguments")) {
            tips.add("The values passed to this method do not meet its parameter types or generic bounds. "
                    + "Compare the method signature with the argument types, especially `extends`, `super`, and required interfaces.");
        } else if (raw.contains("cannot be resolved to a type")) {
            tips.add("Java cannot find this type. Check its spelling and add the required `import`, or create the class/interface in this file.");
        } else if (raw.contains("cannot be resolved")) {
            tips.add("Java cannot find this name. Check its spelling, scope, and imports. "
                    + "If it is a library class, make sure the correct JAR is installed.");
        } else if (raw.contains("Syntax error, insert \";\"")) {
            tips.add("A statement is missing its closing semicolon `;`. Check this line and the line immediately before it.");
        } else if (raw.contains("insert \"}\" to complete")) {
            tips.add("A closing brace `}` is missing. Match every `{` in the class, method, `if`, loop, and lambda blocks above this point.");
        } else if (raw.contains("delete this token")) {
            tips.add("There is an extra token, often an extra `}` or `)`. Check the brackets around this line and remove the unmatched one.");
        } else {
            Matcher mismatch = TYPE_MISMATCH.matcher(raw);
            Matcher method = UNDEFINED_METHOD.matcher(raw);
            if (mismatch.find()) {
                tips.add("The expression produces `" + mismatch.group(1).trim() + "`, but Java needs `"
                        + mismatch.group(2).trim() + "`. Change the variable type or convert the value deliberately.");
            } else if (method.find()) {
                tips.add("Java cannot find the method `" + method.group(1) + "`. Check the method name, parameter count, parameter types, and whether it belongs to this object.");
            } else {
                tips.add("Start with the first error shown above. Later compiler errors can be consequences of that first error.");
            }
        }

        if (countErrors(raw) > 1) {
            tips.add("Fix the first error, then run again. A single syntax error can cause several follow-up messages.");
        }

        StringBuilder guide = new StringBuilder(raw);
        guide.append("\n\n━━ PocketJava Fix Guide ━━\n");
        if (lineNumber > 0) {
            guide.append("Start at line ").append(lineNumber);
            if (!sourceLine.isEmpty()) guide.append(": ").append(sourceLine.trim());
            guide.append('\n');
        }
        for (String tip : tips) guide.append("• ").append(tip).append('\n');
        return guide.toString().trim();
    }

    private static int firstLineNumber(String diagnostics) {
        Matcher matcher = ERROR_LINE.matcher(diagnostics);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String sourceLine(String source, int lineNumber) {
        if (source == null || lineNumber < 1) return "";
        String[] lines = source.split("\\r?\\n", -1);
        return lineNumber <= lines.length ? lines[lineNumber - 1] : "";
    }

    private static int countErrors(String diagnostics) {
        Matcher matcher = Pattern.compile("(?m)^\\d+\\. ERROR in ").matcher(diagnostics);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
