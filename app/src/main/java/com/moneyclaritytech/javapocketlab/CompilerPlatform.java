package com.moneyclaritytech.javapocketlab;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Makes the compile-time Android/Java API stubs bundled by CI available as a normal file.
 * ECJ needs a classpath JAR; AssetManager streams are not sufficient for its batch compiler.
 */
final class CompilerPlatform {
    private static final String ASSET = "compiler/android-36.jar";
    // Bump when compiler stubs change: Android retains app files through an update.
    private static final String FILE_NAME = "android-api36-compiler-v2.jar";

    private CompilerPlatform() {}

    static File ensure(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "compiler-platform");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create compiler platform directory");
        }

        File target = new File(dir, FILE_NAME);
        if (target.isFile() && target.length() > 1_000_000L) return target;

        File temp = new File(dir, FILE_NAME + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Could not replace temporary compiler platform file");
        }

        try (InputStream in = context.getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace old compiler platform");
        }
        if (!temp.renameTo(target)) {
            throw new IOException("Could not install compiler platform");
        }
        return target;
    }
}
