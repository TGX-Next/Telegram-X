# Repository Guidelines

## Project Structure & Module Organization

Telegram X uses Gradle, Java/Kotlin, and C/C++. Application code lives in
`app/src/main/java` and `app/src/main/kotlin`; resources and assets are in `app/src/main/res`
and `app/src/main/assets`. Native code is in `app/jni`.
Keep flavor-specific changes in shared `app/src/since*`, `pre*`, or `only*`
source sets. `tdlib/`, `tgcalls/`, `vkryl/`, and `extension/` provide supporting modules;
`buildSrc/` contains Gradle logic; `tests/` contains tests and benchmarks.

## Build, Test, and Development Commands

Run commands from the repository root using JDK 25, Git LFS, initialized submodules, and the
Gradle wrapper. See `README.md` for SDK/NDK and Rust prerequisites.

- `scripts/setup.sh`: configure Android SDK, NDK, and local properties.
- `./gradlew :app:assembleLatestDebug`: build the latest-flavor debug APK.
- `./gradlew :app:installLatestDebug -Pabi=arm64`: install on a connected ARM64 device.
- `./gradlew :app:testLatestDebugUnitTest`: run JVM unit tests.
- `./gradlew :app:emulatorLatestDebugAndroidTest -Pabi=arm64`: run integration tests.
- `./gradlew :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64`: run UI tests.
- `./gradlew checkMinSdk -PuseLegacyNdk=true`: check Android compatibility, including legacy.
- `./gradlew :app:lintLegacyDebug -PuseLegacyNdk=true`: run legacy Android lint checks.

## Coding Style & Naming Conventions

Follow `.editorconfig` and [code style](docs/CODE_STYLE.md): two-space indentation,
100-character lines, descriptive names, and braces around control-flow bodies. Use PascalCase
classes and camelCase methods; Kotlin has no trailing commas. Java method declarations use
a space before `(`. Use Android Studio formatting; introduce no warnings.
Add Kotlin only in new modules outside `org.thunderdog`, tests, or build code; keep it
Java-interoperable or private. Copy neighboring license headers for new app files.
Reuse existing helpers and avoid allocations during drawing, animations, or scrolling.

## Testing Guidelines

Use JUnit 4, Truth, UI Automator, and AndroidX benchmarks.
Place unit tests in `tests/unit/kotlin`, integration tests in `tests/integration/kotlin`, and
UI tests in `tests/ui/src/main/kotlin`. Use descriptive `*Test` classes and behavior-named methods.
Temporary checks belong in ignored `tests/*/checks` folders. Follow
[testing guidance](tests/README.md): reproduce bugs before fixing them, verify affected flavors,
and run relevant tests and lint before finishing. Device tests require Android 7.0+;
report failures, checks not run, and coverage gaps.

## Commit & Pull Request Guidelines

Use imperative commit titles within 65 characters, matching history, and explain why. Follow the
[PR checklist](docs/PULL_REQUEST_TEMPLATE.md): one scoped change, latest `main` base, linked
issue and issue number in bug-fix titles, affected flavors, before/after checks, and coverage gaps.
Include screenshots or recordings for UI changes and measurements for optimizations.
Follow its `@MadeByLLM` annotation and `Context-Id` trailer requirements where applicable.

## Security & Configuration

Never commit `local.properties`, signing keys, API credentials, or account snapshots.
Keep snapshots outside the checkout.
