package com.moneyclaritytech.pocketforge;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectStore {
    private final File root;

    public ProjectStore(Context context) {
        root = new File(context.getFilesDir(), "projects/default");
        if (!root.exists()) root.mkdirs();
    }

    public File root() { return root; }

    public File file(String name) {
        String safe = name == null ? "Main.java" : name.replaceAll("[^A-Za-z0-9_.$-]", "_");
        if (!safe.endsWith(".java")) safe += ".java";
        return new File(root, safe);
    }

    public void ensureStarter() throws IOException {
        if (listJavaFiles().isEmpty()) {
            write("Main.java",
                    "public class Main {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(\"Hello from Java Pocket Lab!\");\n" +
                    "    }\n" +
                    "}\n");
        }
    }

    public List<File> listJavaFiles() {
        File[] files = root.listFiles((dir, name) -> name.endsWith(".java"));
        List<File> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) out.add(f);
        }
        out.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    public void write(String name, String text) throws IOException {
        File f = file(name);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean rename(File from, String toName) {
        return from != null && from.exists() && from.renameTo(file(toName));
    }

    public boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }
}
