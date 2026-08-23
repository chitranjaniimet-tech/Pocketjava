# Third-party notices

PocketJava is a clean-room project and does not contain Jvdroid source code, bundled Jvdroid examples, artwork, branding, or private implementation material.

## Sora Editor
- Project: Rosemoe/sora-editor
- Version: 0.24.6
- License: LGPL-2.1
- Used for the Android code editor and Java language support.

## Eclipse Compiler for Java (ECJ)
- Project: Eclipse JDT Core
- Version: 3.18.0 (Eclipse 4.12 line)
- License: Eclipse Public License 2.0
- Used as the on-device Java compiler for Java 11 language-level compilation.
- This Android-compatible compiler line avoids the desktop `java.compiler` / `javax.lang.model.SourceVersion` dependency introduced in newer ECJ lines that is unavailable on Android's runtime.

## Android API 36 compile-time stubs
- Source: Android SDK platform android-36 `android.jar`
- Used only as the compile-time platform library for ECJ. CI strips Android resources/assets and bundles the class stubs required for type resolution.
- Subject to the Android SDK / Android Open Source Project notices applicable to the platform SDK.

## Google R8 / D8
- Version: 8.13.19
- License: BSD-style / Android Open Source Project notices
- Used at runtime to convert ECJ-generated JVM class files into Android DEX.

## AndroidX / Material Components
- Used for Android UI and platform compatibility under their respective Apache-2.0 licenses.
