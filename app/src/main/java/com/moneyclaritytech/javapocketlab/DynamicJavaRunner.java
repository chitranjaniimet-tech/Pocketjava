package com.moneyclaritytech.javapocketlab;

import android.content.Context;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexClassLoader;

/**
 * On-device Java pipeline:
 * Java source -> Eclipse Compiler for Java (ECJ) -> JVM .class -> D8 -> DEX -> main().
 *
 * ECJ is used instead of Janino so normal modern Java syntax such as lambdas, method
 * references, streams and Java 11 source constructs can be compiled. CI bundles stripped
 * Android API 36 class stubs, which act as the compiler platform library on Android.
 */
public final class DynamicJavaRunner {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final Pattern PUBLIC_CLASS = Pattern.compile("\\bpublic\\s+(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern ANY_CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");

    private final Context context;

    public DynamicJavaRunner(Context context) {
        this.context = context.getApplicationContext();
    }

    public RunResult run(String source, String stdin, List<File> dependencyJars) {
        long start = System.currentTimeMillis();
        File runDir = new File(context.getCodeCacheDir(), "runs/" + UUID.randomUUID());
        File sourceDir = new File(runDir, "src");
        File classesDir = new File(runDir, "classes");
        sourceDir.mkdirs();
        classesDir.mkdirs();

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        java.io.InputStream oldIn = System.in;
        String oldUserDir = System.getProperty("user.dir");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8.name())) {
            System.setOut(capture);
            System.setErr(capture);
            System.setIn(new ByteArrayInputStream((stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8)));
            System.setProperty("user.dir", context.getFilesDir().getAbsolutePath());

            String simpleName = detectSimpleClassName(source);
            String packageName = detectPackage(source);
            String fqcn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

            File packageDir = sourceDir;
            if (!packageName.isEmpty()) {
                packageDir = new File(sourceDir, packageName.replace('.', File.separatorChar));
                if (!packageDir.mkdirs() && !packageDir.isDirectory()) {
                    throw new IllegalStateException("Could not create source package directory");
                }
            }
            File sourceFile = new File(packageDir, simpleName + ".java");
            writeUtf8(sourceFile, source == null ? "" : source);

            File platformJar = CompilerPlatform.ensure(context);
            List<File> validDependencyJars = validDependencies(dependencyJars);

            List<String> classPathEntries = new ArrayList<>();
            classPathEntries.add(platformJar.getAbsolutePath());
            for (File dependency : validDependencyJars) classPathEntries.add(dependency.getAbsolutePath());
            String classPath = joinPath(classPathEntries);

            PrintWriter compilerOutput = new PrintWriter(new OutputStreamWriter(captured, StandardCharsets.UTF_8), true);
            String[] ecjArgs = new String[] {
                    "-proc:none",
                    "-encoding", "UTF-8",
                    "-source", "11",
                    "-target", "11",
                    "-nowarn",
                    "-g",
                    "-d", classesDir.getAbsolutePath(),
                    "-classpath", classPath,
                    sourceFile.getAbsolutePath()
            };

            boolean compiled = BatchCompiler.compile(ecjArgs, compilerOutput, compilerOutput, null);
            compilerOutput.flush();
            if (!compiled) {
                String diagnostics = asUtf8(captured).trim();
                if (diagnostics.isEmpty()) diagnostics = "Compilation failed without diagnostics.";
                return RunResult.error(diagnostics, elapsed(start));
            }

            File classesJar = new File(runDir, "program.jar");
            try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(classesJar))) {
                int count = addClassesToJar(classesDir, classesDir, jar);
                if (count == 0) {
                    return RunResult.error("Compilation produced no class files.", elapsed(start));
                }
            }

            File d8TempArchive = new File(runDir, "d8-temp.zip");
            File dexArchive = new File(runDir, "program-dex.zip");
            D8Command.Builder d8 = D8Command.builder()
                    .addProgramFiles(classesJar.toPath())
                    .addLibraryFiles(platformJar.toPath())
                    .setMode(CompilationMode.DEBUG)
                    .setMinApiLevel(26)
                    .setOutput(d8TempArchive.toPath(), OutputMode.DexIndexed);

            for (File jar : validDependencyJars) d8.addProgramFiles(jar.toPath());
            D8.run(d8.build());

            try (FileOutputStream out = new FileOutputStream(dexArchive);
                 FileInputStream in = new FileInputStream(d8TempArchive)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
            }
            if (!dexArchive.setReadOnly()) {
                throw new IllegalStateException("Could not secure generated DEX as read-only");
            }

            DexClassLoader loader = new DexClassLoader(
                    dexArchive.getAbsolutePath(),
                    context.getCodeCacheDir().getAbsolutePath(),
                    null,
                    context.getClassLoader()
            );
            Class<?> mainClass = loader.loadClass(fqcn);
            Method main = mainClass.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);

            capture.flush();
            return RunResult.success(asUtf8(captured), elapsed(start));
        } catch (InvocationTargetException target) {
            Throwable real = target.getTargetException() == null ? target : target.getTargetException();
            real.printStackTrace(new PrintStream(captured));
            return RunResult.error(asUtf8(captured), elapsed(start));
        } catch (Throwable t) {
            t.printStackTrace(new PrintStream(captured));
            return RunResult.error(asUtf8(captured), elapsed(start));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            System.setIn(oldIn);
            if (oldUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", oldUserDir);
            }
            deleteTree(runDir);
        }
    }

    public static boolean probablyNeedsInput(String source) {
        return source != null && (source.contains("System.in") || source.contains("new Scanner("));
    }

    private static List<File> validDependencies(List<File> dependencyJars) {
        List<File> result = new ArrayList<>();
        if (dependencyJars != null) {
            for (File jar : dependencyJars) {
                if (jar != null && jar.isFile()) result.add(jar);
            }
        }
        return result;
    }

    private static String joinPath(List<String> entries) {
        StringBuilder result = new StringBuilder();
        for (String entry : entries) {
            if (result.length() > 0) result.append(File.pathSeparatorChar);
            result.append(entry);
        }
        return result.toString();
    }

    private static int addClassesToJar(File root, File current, JarOutputStream jar) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                count += addClassesToJar(root, child, jar);
            } else if (child.getName().endsWith(".class")) {
                String relative = root.toURI().relativize(child.toURI()).getPath();
                JarEntry entry = new JarEntry(relative);
                jar.putNextEntry(entry);
                try (InputStream in = new FileInputStream(child)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) jar.write(buffer, 0, read);
                }
                jar.closeEntry();
                count++;
            }
        }
        return count;
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String detectPackage(String source) {
        Matcher m = PACKAGE.matcher(source == null ? "" : source);
        return m.find() ? m.group(1) : "";
    }

    private static String detectSimpleClassName(String source) {
        String s = source == null ? "" : source;
        Matcher pub = PUBLIC_CLASS.matcher(s);
        if (pub.find()) return pub.group(1);
        Matcher any = ANY_CLASS.matcher(s);
        if (any.find()) return any.group(1);
        throw new IllegalArgumentException("No Java class found. Add: public class Main { ... }");
    }

    private static String asUtf8(ByteArrayOutputStream captured) {
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }

    private static long elapsed(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) deleteTree(c);
        }
        file.delete();
    }

    public static final class RunResult {
        public final boolean success;
        public final String output;
        public final long durationMs;

        private RunResult(boolean success, String output, long durationMs) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.durationMs = durationMs;
        }

        public static RunResult success(String output, long ms) {
            return new RunResult(true, output, ms);
        }

        public static RunResult error(String output, long ms) {
            return new RunResult(false, output, ms);
        }
    }
}
