package com.moneyclaritytech.javapocketlab;

import android.content.Context;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.StringResource;
import org.codehaus.janino.CompilerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexClassLoader;

/**
 * Real on-device Java execution pipeline:
 * Java source -> Janino .class bytes -> D8 -> classes.dex -> DexClassLoader -> main().
 *
 * This intentionally targets beginner/educational Java. It does not pretend to bundle a
 * complete desktop OpenJDK installation. Code executes with the app's Android process
 * permissions, so the UI enforces a short timeout and treats user code as trusted local code.
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
        runDir.mkdirs();

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        java.io.InputStream oldIn = System.in;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8.name())) {
            System.setOut(capture);
            System.setErr(capture);
            System.setIn(new ByteArrayInputStream((stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8)));

            String simpleName = detectSimpleClassName(source);
            String packageName = detectPackage(source);
            String fqcn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

            ICompilerFactory factory = new CompilerFactory();
            ICompiler compiler = factory.newCompiler();
            Map<String, byte[]> classes = new HashMap<>();
            compiler.setClassFileCreator(new MapResourceCreator(classes));
            if (dependencyJars != null && !dependencyJars.isEmpty()) {
                compiler.setClassPath(dependencyJars.toArray(new File[0]));
            }
            compiler.compile(new Resource[] { new StringResource(simpleName + ".java", source) });

            if (classes.isEmpty()) {
                return RunResult.error("Compilation produced no classes.", elapsed(start));
            }

            File classesJar = new File(runDir, "program.jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(classesJar.toPath()))) {
                for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                    String name = entry.getKey().replace('\\', '/');
                    if (!name.endsWith(".class")) name += ".class";
                    JarEntry je = new JarEntry(name);
                    jar.putNextEntry(je);
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }

            File d8TempArchive = new File(runDir, "d8-temp.zip");
            File dexArchive = new File(runDir, "program-dex.zip");
            D8Command.Builder d8 = D8Command.builder()
                    .addProgramFiles(classesJar.toPath())
                    .setMode(CompilationMode.DEBUG)
                    .setMinApiLevel(26)
                    .setDisableDesugaring(true)
                    .setOutput(d8TempArchive.toPath(), OutputMode.DexIndexed);

            if (dependencyJars != null) {
                for (File jar : dependencyJars) {
                    if (jar != null && jar.isFile()) d8.addProgramFiles(jar.toPath());
                }
            }
            D8.run(d8.build());

            try (FileOutputStream out = new FileOutputStream(dexArchive);
                 FileInputStream in = new FileInputStream(d8TempArchive)) {
                if (!dexArchive.setReadOnly()) {
                    throw new IllegalStateException("Could not secure generated DEX as read-only");
                }
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
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
            deleteTree(runDir);
        }
    }

    public static boolean probablyNeedsInput(String source) {
        return source != null && (source.contains("System.in") || source.contains("new Scanner("));
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
