# Security notes

PocketJava executes code written by the device owner. Java code is treated as trusted local code and runs with the application's Android sandbox permissions.

- Program execution is isolated in a dedicated Android process.
- A watchdog stops runs that exceed 8 seconds.
- Shell commands run with normal app-sandbox permissions and are stopped after 6 seconds.
- Maven downloads are HTTPS-only and size-limited.
- No advertising or analytics SDK is present.

Do not run Java or shell code from an untrusted source unless you understand what it does.
