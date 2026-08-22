# PocketJava

PocketJava is a clean-room, phone-first Java learning IDE for Android. It is designed for people who may have no laptop and want to learn, write, compile and run Java directly on an Android phone.

## What is in the current build

- Real on-device Java compilation with Eclipse Compiler for Java (ECJ)
- Java 11 source/target compilation with Android 16 / API 36 compile-time classes
- Lambdas, method references, Streams-facing code, CompletableFuture and modern Java syntax
- NIO.2 APIs available when the Android runtime provides them
- ECJ class files converted to DEX by D8 and executed on-device
- Sora mobile code editor with Java syntax highlighting, indentation, tabs and editing support
- Large one-tap **Run** action with a small output preview so the editor remains visible
- Multi-file local project, create/import/rename/delete/share
- Extended Java symbol keyboard row
- Code formatter, find, undo/redo, light/dark mode and editor settings
- 10 short beginner lessons with expected output and tiny challenges
- Original built-in examples (not copied from Jvdroid)
- Java REPL-style statement runner
- Maven Central library resolver
- Console plus Android shell commands inside the app sandbox
- Offline Java quick docs
- Scanner/System.in input dialog
- Separate runner process with a 20-second compile/run watchdog
- No ads, no ad SDK, no analytics SDK and no in-app purchase SDK

## Compiler regression gate

The GitHub release workflow compiles and executes an advanced Java regression program before building the APK. It covers lambdas, method references, Streams, NIO.2 and CompletableFuture. A release is not published if this compiler regression fails.

## Jvdroid feature target

The product target is the publicly advertised Jvdroid workflow: offline Java, libraries/Maven, examples, terminal, REPL, strong mobile editor, Javadocs, formatter, programming keyboard, themes, tabs and sharing. PocketJava implements these workflows with its own design and code. It does **not** copy Jvdroid source, bundled examples, artwork or branding.

PocketJava now uses the full Eclipse Java compiler rather than Janino. It is still not a bundled desktop OpenJDK distribution; real OpenJDK JShell/Nailgun and Kotlin/Scala/Clojure toolchains remain separate future modules.

## APK: GitHub Releases only

The repository intentionally does **not** use `actions/upload-artifact`. GitHub Actions builds an installable debug-signed APK and attaches `PocketJava.apk` directly to a GitHub Release. This avoids GitHub Actions artifact-storage quota.

All CI debug builds use the same persistent signing identity and an increasing versionCode, so builds created after the persistent signing change can update one another without uninstalling the app.

Open the repository **Releases** page and download `PocketJava.apk` from the newest release.

## Build configuration

- Android Gradle Plugin 8.13.2
- Gradle 8.13 in CI
- JDK 17 in CI
- Eclipse Compiler for Java 3.46.0
- Google R8 / D8 8.13.19
- compileSdk / targetSdk 36
- minSdk 26

## Third-party components

See `THIRD_PARTY_NOTICES.md`. The app itself is Apache-2.0 licensed.
