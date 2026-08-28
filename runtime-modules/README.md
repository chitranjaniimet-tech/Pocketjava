# PocketForge runtime packs

PocketForge does not use an external terminal application. A language runtime is distributed as a
PocketForge runtime pack and installed below the app's private files/pocketforge-runtime directory.

## Manifest

manifest.json is read by the app from this branch. Each published module entry must contain:

- id: one of the catalog IDs (python, javascript, cpp, kotlin, go, rust, php, ruby, shell, perl)
- url: HTTPS URL to a ZIP archive
- sha256: lowercase SHA-256 digest of the ZIP
- executable: path inside the archive, matching the catalog layout

Example:

    {
      "modules": [
        {
          "id": "python",
          "url": "https://github.com/chitranjaniimet-tech/Pocketjava/releases/download/pocketforge-runtime-v1/python-arm64.zip",
          "sha256": "replace-with-64-lowercase-hex-digest",
          "executable": "modules/python/bin/python"
        }
      ]
    }

## Pack layout

A ZIP must contain the declared executable and all of its shared libraries and support files. The
archive must already be built for Android and the target device ABI; PocketForge does not run Linux
desktop binaries. The pack should include:

    modules/<id>/bin/<executable>
    modules/<id>/lib/...
    modules/<id>/share/...

The installer rejects missing digests, checksum mismatches, incompatible paths, and unsafe ZIP
entries. Runtime commands are launched with ProcessBuilder, a 30-second watchdog, bounded output,
and POCKETFORGE_HOME / POCKETFORGE_RUNTIME_BIN environment variables.

Runtime packs are intentionally separate from the base APK so PocketForge can grow language by
language without changing the independent PocketJava product.

