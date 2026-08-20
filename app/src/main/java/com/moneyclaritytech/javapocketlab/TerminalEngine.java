package com.moneyclaritytech.javapocketlab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Phone-friendly terminal. Learner commands are handled directly; everything else is passed to
 * Android's /system/bin/sh inside the app sandbox. This is far more useful than a fake terminal,
 * while still keeping commands within normal Android application permissions.
 */
public final class TerminalEngine {
    public static final String CLEAR = "__CLEAR__";
    public static final String RUN = "__RUN__";
    public static final String SAVE = "__SAVE__";

    private final ProjectStore store;

    public TerminalEngine(ProjectStore store) {
        this.store = store;
    }

    public String execute(String raw) {
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) return "";
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (command) {
                case "help":
                    return "PocketJava learner commands:\n" +
                            "  help              show this help\n" +
                            "  pwd               show project folder\n" +
                            "  ls                list Java files\n" +
                            "  cat <file>        show a Java file\n" +
                            "  touch <file>      create a Java file\n" +
                            "  rm <file>         delete a Java file\n" +
                            "  run               run active editor file\n" +
                            "  save              save active editor file\n" +
                            "  clear             clear console\n\n" +
                            "Normal Android shell commands also work in the project folder.\n" +
                            "Examples: echo hello, date, wc Main.java";
                case "pwd":
                    return store.root().getAbsolutePath();
                case "ls":
                    String listed = store.listJavaFiles().stream()
                            .map(File::getName)
                            .collect(Collectors.joining("\n"));
                    return listed.isEmpty() ? "(no Java files)" : listed;
                case "cat":
                    if (arg.isEmpty()) return "usage: cat <file>";
                    File cat = store.file(arg);
                    if (!cat.exists()) return "not found: " + cat.getName();
                    try (FileInputStream in = new FileInputStream(cat)) {
                        byte[] data = new byte[(int) cat.length()];
                        int off = 0;
                        while (off < data.length) {
                            int n = in.read(data, off, data.length - off);
                            if (n < 0) break;
                            off += n;
                        }
                        return new String(data, 0, off, StandardCharsets.UTF_8);
                    }
                case "touch":
                    if (arg.isEmpty()) return "usage: touch <file>";
                    File touch = store.file(arg);
                    if (!touch.exists()) store.write(touch.getName(), starterFor(touch.getName()));
                    return "created " + touch.getName();
                case "rm":
                    if (arg.isEmpty()) return "usage: rm <file>";
                    File rm = store.file(arg);
                    return store.delete(rm) ? "deleted " + rm.getName() : "could not delete " + rm.getName();
                case "run": return RUN;
                case "save": return SAVE;
                case "clear": return CLEAR;
                default: return runShell(input);
            }
        } catch (Exception e) {
            return "Terminal error: " + e.getMessage();
        }
    }

    private String runShell(String command) throws Exception {
        Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(store.root())
                .redirectErrorStream(true)
                .start();
        boolean done = process.waitFor(6, TimeUnit.SECONDS);
        if (!done) {
            process.destroy();
            if (!process.waitFor(400, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            return "Command stopped after 6 seconds.";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                if (out.length() > 100_000) {
                    out.append("\n[output truncated]");
                    break;
                }
            }
        }
        if (out.length() == 0) return "(exit " + process.exitValue() + ")";
        if (process.exitValue() != 0) out.append("\n(exit ").append(process.exitValue()).append(')');
        return out.toString();
    }

    private static String starterFor(String fileName) {
        String cls = fileName.replace(".java", "").replaceAll("[^A-Za-z0-9_$]", "_");
        if (cls.isEmpty() || Character.isDigit(cls.charAt(0))) cls = "Main";
        return "public class " + cls + " {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from " + cls + "\");\n" +
                "    }\n" +
                "}\n";
    }
}
