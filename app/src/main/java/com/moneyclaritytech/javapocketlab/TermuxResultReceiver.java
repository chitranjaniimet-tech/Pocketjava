package com.moneyclaritytech.pocketforge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

/** Receives asynchronous Termux command output and forwards it to PocketForge. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    public static final String EXTRA_RECEIVER = "pocketforge.result_receiver";
    private static final String RESULT_BUNDLE = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE";
    private static final String STDOUT = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDOUT";
    private static final String STDERR = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDERR";
    private static final String EXIT_CODE = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_EXIT_CODE";
    private static final String ERRMSG = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_ERRMSG";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        intent.setExtrasClassLoader(ResultReceiver.class.getClassLoader());
        ResultReceiver receiver;
        try {
            receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
        } catch (Exception e) {
            return;
        }
        if (receiver == null) return;
        Bundle raw = intent.getBundleExtra(RESULT_BUNDLE);
        Bundle result = new Bundle();
        if (raw == null) {
            result.putString("stderr", "Termux returned no result bundle. Check its Run commands permission and allow-external-apps setting.");
            result.putInt("exitCode", 126);
        } else {
            result.putString("stdout", raw.getString(STDOUT, ""));
            result.putString("stderr", raw.getString(STDERR, ""));
            result.putInt("exitCode", raw.getInt(EXIT_CODE, -1));
            result.putString("errorMessage", raw.getString(ERRMSG, ""));
        }
        receiver.send(0, result);
    }
}
