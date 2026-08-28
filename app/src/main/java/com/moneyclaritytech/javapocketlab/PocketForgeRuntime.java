package com.moneyclaritytech.pocketforge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PocketForge-owned runtime layer.
 *
 * Runtime packs are ordinary PocketForge archives, verified by SHA-256, extracted into the app's
 * private files directory, and executed directly with ProcessBuilder. No external terminal app or
 * package manager is involved.
 */
public final class PocketForgeRuntime {
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/chitranjaniimet-tech/Pocketjava/pocketforge-platform/runtime-modules/manifest.json";
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_OUTPUT = 100000;

    public interface Callback {
        void complete(boolean success, String output, int exitCode);
    }

    public interface InstallCallback {
        void complete(boolean success, String output);
    }

    public static final class Module {
        public final String id;
        public final String name;
        public final String description;
        public final String executablePath;

        Module(String id, String name, String description, String executablePath) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.executablePath = executablePath;
        }
    }

    private final Context context;
    private final File runtimeRoot;
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    public PocketForgeRuntime(Context context) {
        this.context = context.getApplicationContext();
        this.runtimeRoot = new File(this.context.getFilesDir(), "pocketforge-runtime");
        if (!runtimeRoot.exists()) runtimeRoot.mkdirs();
    }

    public static List<Module> modules() {
        return Collections.unmodifiableList(Arrays.asList(
                new Module("python", "Python 3", "Python scripts and the standard library.", "modules/python/bin/python"),
                new Module("javascript", "JavaScript / Node.js", "JavaScript scripts and Node.js tooling.", "modules/javascript/bin/node"),
                new Module("cpp", "C / C++", "Clang compiler and native console programs.", "modules/cpp/bin/clang"),
                new Module("kotlin", "Kotlin", "Kotlin compiler and learning projects.", "modules/kotlin/bin/kotlinc"),
                new Module("go", "Go", "Go compiler and command-line programs.", "modules/go/bin/go"),
                new Module("rust", "Rust", "Rust compiler and native tools.", "modules/rust/bin/rustc"),
                new Module("php", "PHP", "PHP command-line scripts.", "modules/php/bin/php"),
                new Module("ruby", "Ruby", "Ruby scripts and standard tooling.", "modules/ruby/bin/ruby"),
                new Module("shell", "Shell", "Bash scripts and PocketForge utilities.", "modules/shell/bin/bash"),
                new Module("perl", "Perl", "Perl scripts and standard tooling.", "modules/perl/bin/perl")
        ));
    }

    public static Module moduleFor(String id) {
        if (id == null) return null;
        for (Module module : modules()) if (module.id.equals(id)) return module;
        return null;
    }

    public File root() {
        return runtimeRoot;
    }

    public boolean isInstalled(Module module) {
        if (module == null) return false;
        File executable = new File(runtimeRoot, module.executablePath);
        return executable.isFile() && executable.canExecute();
    }

    public void installModule(Module module, InstallCallback callback) {
        worker.submit(() -> {
            try {
                JSONObject manifest = new JSONObject(downloadText(MANIFEST_URL));
                JSONArray entries = manifest.optJSONArray("modules");
                JSONObject selected = null;
                if (entries != null) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject candidate = entries.optJSONObject(i);
                        if (candidate != null && module.id.equals(candidate.optString("id"))) {
                            selected = candidate;
                            break;
                        }
                    }
                }
                if (selected == null) {
                    finishInstall(callback, false, "No PocketForge runtime pack is published for " + module.name + " yet.");
                    return;
                }
                String url = selected.optString("url", "");
                String sha256 = selected.optString("sha256", "").toLowerCase();
                String executable = selected.optString("executable", module.executablePath);
                if (url.isEmpty() || sha256.length() != 64 || !module.executablePath.equals(executable)) {
                    finishInstall(callback, false, "The " + module.name + " pack is missing a verified artifact or has an incompatible layout.");
                    return;
                }

                File archive = new File(runtimeRoot, ".download-" + module.id + ".zip");
                downloadTo(url, archive);
                if (!sha256.equals(sha256(archive))) {
                    archive.delete();
                    finishInstall(callback, false, "Checksum verification failed for " + module.name + ".");
                    return;
                }

                File staging = new File(runtimeRoot, ".staging-" + module.id);
                deleteTree(staging);
                staging.mkdirs();
                extractZip(archive, staging);
                archive.delete();

                File executableFile = new File(staging, module.executablePath);
                if (!executableFile.isFile()) {
                    deleteTree(staging);
                    finishInstall(callback, false, "The " + module.name + " pack does not contain its declared executable.");
                    return;
                }
                executableFile.setExecutable(true, false);

                File stagedModule = new File(staging, "modules/" + module.id);
                File installed = new File(runtimeRoot, "modules/" + module.id);
                deleteTree(installed);
                File installedParent = installed.getParentFile();
                if (installedParent != null) installedParent.mkdirs();
                if (!stagedModule.renameTo(installed)) {
                    deleteTree(staging);
                    finishInstall(callback, false, "Could not activate the " + module.name + " pack.");
                    return;
                }
                deleteTree(staging);
                finishInstall(callback, true, module.name + " installed in PocketForge private storage.");
            } catch (Exception e) {
                finishInstall(callback, false, "Module installation failed: " + safeMessage(e));
            }
        });
    }

    public void runSource(String languageId, String source, String stdin, Callback callback) {
        Module module = moduleFor(languageId);
        if (module == null || !isInstalled(module)) {
            finish(callback, false, "Install the PocketForge " + languageId + " module first.", 127);
            return;
        }
        worker.submit(() -> {
            Process process = null;
            try {
                List<String> command = commandFor(module, source);
                if (command == null) {
                    finish(callback, false, "The " + module.name + " module is installed, but its source runner is still being added.", 127);
                    return;
                }
                ProcessBuilder builder = new ProcessBuilder(command)
                        .directory(context.getFilesDir())
                        .redirectErrorStream(true);
                String bin = new File(runtimeRoot, "modules/" + module.id + "/bin").getAbsolutePath();
                builder.environment().put("POCKETFORGE_HOME", runtimeRoot.getAbsolutePath());
                builder.environment().put("POCKETFORGE_RUNTIME_BIN", bin);
                builder.environment().put("PATH", bin + File.pathSeparator + System.getenv("PATH"));
                process = builder.start();

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    if (stdin != null && !stdin.isEmpty()) writer.write(stdin);
                }

                boolean done = process.waitFor(30, TimeUnit.SECONDS);
                if (!done) {
                    process.destroy();
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                    finish(callback, false, "Program stopped after 30 seconds.", 124);
                    return;
                }
                String output = readLimited(process.getInputStream());
                finish(callback, process.exitValue() == 0, output, process.exitValue());
            } catch (Exception e) {
                if (process != null) process.destroyForcibly();
                finish(callback, false, "Runtime error: " + safeMessage(e), 126);
            }
        });
    }

    private List<String> commandFor(Module module, String source) {
        String executable = new File(runtimeRoot, module.executablePath).getAbsolutePath();
        ArrayList<String> command = new ArrayList<>();
        if ("python".equals(module.id)) {
            command.add(executable); command.add("-c"); command.add(source);
        } else if ("javascript".equals(module.id)) {
            command.add(executable); command.add("-e"); command.add(source);
        } else if ("php".equals(module.id)) {
            command.add(executable); command.add("-r"); command.add(source);
        } else if ("ruby".equals(module.id) || "perl".equals(module.id)) {
            command.add(executable); command.add("-e"); command.add(source);
        } else if ("shell".equals(module.id)) {
            command.add(executable); command.add("-c"); command.add(source);
        } else {
            return null;
        }
        return command;
    }

    private String downloadText(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream()) {
            return readLimited(input);
        } finally {
            connection.disconnect();
        }
    }

    private void downloadTo(String url, File destination) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PocketForge/" + BuildConfig.VERSION_NAME);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("server returned HTTP " + status);
        }
        return connection;
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_OUTPUT) {
                output.write(buffer, 0, Math.max(0, MAX_OUTPUT - output.size()));
                output.write("\n[output truncated]".getBytes(StandardCharsets.UTF_8));
                break;
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static void extractZip(File archive, File destination) throws Exception {
        String base = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                File target = new File(destination, entry.getName());
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(base)) throw new SecurityException("unsafe archive entry");
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream output = new FileOutputStream(target)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private void finish(Callback callback, boolean success, String output, int exitCode) {
        if (callback != null) main.post(() -> callback.complete(success, output == null ? "" : output, exitCode));
    }

    private void finishInstall(InstallCallback callback, boolean success, String output) {
        if (callback != null) main.post(() -> callback.complete(success, output == null ? "" : output));
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
