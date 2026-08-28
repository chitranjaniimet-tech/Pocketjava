# PocketForge

PocketForge is a phone-first mobile coding workstation and language-learning platform. It keeps the original PocketJava product separate while providing a larger, modular path for users who want to learn and run multiple programming languages on Android.

## Product direction

PocketForge combines:

- A comfortable mobile code editor
- A real terminal inside the Android app sandbox with PocketForge project commands
- Local project and file management
- Guided lessons, examples and challenges
- On-device execution where an Android-compatible runtime is available
- Online language modules rather than one oversized base APK
- Git, package and workspace integrations as the platform matures

## Language platform

Java is the first built-in compiler because it is already implemented and tested on-device. The language registry is deliberately independent from the Java runner. Runtime modules are downloaded from the PocketForge runtime repository and installed into PocketForge's private app storage. The catalog is designed for:

- Python
- C and C++
- JavaScript and Node.js
- Kotlin
- Go
- Rust
- PHP
- Shell scripting

Each future module will provide its own editor mode, runtime, package manager, examples, learning path and safety limits. A module can be installed or removed without changing the core application.

## Branch separation

This branch is `pocketforge-platform`. It is intentionally independent from the existing PocketJava branch. PocketJava remains the Java-focused product. These branches must not be merged unless the owner explicitly requests it.

## Current foundation

- PocketForge application ID: `com.moneyclaritytech.pocketforge`
- Separate debug signing identity from PocketJava
- Java compiler and runner plus PocketForge-owned runtime installer and process runner
- Language hub, runtime manager and module registry
- Mobile editor, lessons, examples, files, formatter, REPL, terminal and settings
- No ads, trackers or in-app purchase SDK
- No dependency on Termux or another terminal application

## Build

The GitHub Actions workflow builds `PocketForge.apk` and publishes it directly to a GitHub Release. Its signing identity and package ID are independent from PocketJava, so both apps can be installed together.

## License

See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
