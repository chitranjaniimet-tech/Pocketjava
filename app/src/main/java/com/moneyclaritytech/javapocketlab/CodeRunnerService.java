package com.moneyclaritytech.javapocketlab;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs untrusted practice code in a separate app process so an infinite loop cannot freeze the UI. */
public final class CodeRunnerService extends Service {
    public static final String ACTION_RUN = "com.moneyclaritytech.javapocketlab.RUN";
    public static final String ACTION_STOP = "com.moneyclaritytech.javapocketlab.STOP";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_STDIN = "stdin";
    public static final String EXTRA_DEPS = "deps";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final int RESULT_OK = 1;
    public static final int RESULT_ERROR = 2;
    public static final int RESULT_TIMEOUT = 3;
    private static final long TIMEOUT_MS = 20_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }
        if (!ACTION_RUN.equals(intent.getAction())) return START_NOT_STICKY;

        final String source = intent.getStringExtra(EXTRA_SOURCE);
        final String stdin = intent.getStringExtra(EXTRA_STDIN);
        final ArrayList<String> depPaths = intent.getStringArrayListExtra(EXTRA_DEPS);
        @SuppressWarnings("deprecation")
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);

        final AtomicBoolean runFinished = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(TIMEOUT_MS); } catch (InterruptedException ignored) { return; }
            if (runFinished.compareAndSet(false, true)) {
                if (receiver != null) {
                    Bundle b = new Bundle();
                    b.putString("output", "Program stopped after 20 seconds. Compilation or execution took too long; check for an infinite loop or very heavy work.");
                    receiver.send(RESULT_TIMEOUT, b);
                }
                Process.killProcess(Process.myPid());
            }
        }, "java-run-watchdog");
        watchdog.start();

        executor.submit(() -> {
            List<File> deps = new ArrayList<>();
            if (depPaths != null) {
                for (String p : depPaths) if (p != null) deps.add(new File(p));
            }
            DynamicJavaRunner.RunResult result = new DynamicJavaRunner(this).run(source, stdin, deps);
            if (runFinished.compareAndSet(false, true) && receiver != null) {
                Bundle b = new Bundle();
                b.putString("output", result.output);
                b.putLong("durationMs", result.durationMs);
                receiver.send(result.success ? RESULT_OK : RESULT_ERROR, b);
            }
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
