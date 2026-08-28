package com.moneyclaritytech.pocketforge;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Online runtime bridge for Termux-installed language modules. */
public final class TermuxBridge {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND";
    private static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final AtomicInteger REQUESTS = new AtomicInteger(2000);

    private TermuxBridge() {}

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void runSource(Context context, String languageId, String source, String stdin, ResultReceiver receiver) {
        String path;
        String[] args;
        if ("python".equals(languageId)) {
            path = "$PREFIX/bin/python";
            String encoded = Base64.encodeToString(source.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            args = new String[]{"-c", "import base64;exec(compile(base64.b64decode('" + encoded + "'),'<pocketforge>','exec'))"};
        } else if ("javascript".equals(languageId)) {
            path = "$PREFIX/bin/node";
            args = new String[]{"-e", source};
        } else if ("php".equals(languageId)) {
            path = "$PREFIX/bin/php";
            args = new String[]{"-r", source};
        } else if ("ruby".equals(languageId)) {
            path = "$PREFIX/bin/ruby";
            args = new String[]{"-e", source};
        } else if ("shell".equals(languageId)) {
            path = "$PREFIX/bin/bash";
            args = new String[]{"-c", source};
        } else {
            if (receiver != null) {
                Bundle error = new Bundle();
                error.putString("stderr", "No direct runner is registered for " + languageId + " yet. Install the toolchain and use the terminal command.");
                error.putInt("exitCode", 127);
                receiver.send(0, error);
            }
            return;
        }
        runCommand(context, path, args, stdin, receiver, languageId + " source");
    }

    public static void installPackage(Context context, String packageName, ResultReceiver receiver) {
        runCommand(context, "$PREFIX/bin/pkg", new String[]{"install", "-y", packageName}, "", receiver, "Install " + packageName);
    }

    private static void runCommand(Context context, String path, String[] args, String stdin, ResultReceiver receiver, String label) {
        Intent intent = new Intent(RUN_COMMAND_ACTION);
        intent.setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", path);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args);
        intent.putExtra("com.termux.RUN_COMMAND_STDIN", stdin == null ? "" : stdin);
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "$HOME");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        intent.putExtra("com.termux.RUN_COMMAND_LABEL", label);
        intent.putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "PocketForge runtime execution");
        if (receiver != null) {
            Intent callback = new Intent(context, TermuxResultReceiver.class);
            callback.putExtra(TermuxResultReceiver.EXTRA_RECEIVER, receiver);
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, REQUESTS.incrementAndGet(), callback, flags);
            intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
        }
        try {
            context.startService(intent);
        } catch (Exception e) {
            if (receiver != null) {
                Bundle error = new Bundle();
                error.putString("stderr", "Could not start Termux runtime: " + e.getMessage());
                error.putInt("exitCode", 126);
                receiver.send(0, error);
            }
        }
    }
}
