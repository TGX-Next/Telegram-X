# Repository Guidelines

## Project Structure & Module Organization

Telegram X uses Gradle, Java/Kotlin, and C/C++. Application code lives in
`app/src/main/java` and `app/src/main/kotlin`; resources and assets are in `app/src/main/res`
and `app/src/main/assets`. Native code is in `app/jni`.
The only supported flavor is `latest` (Android 13+, arm64); shared application
code and resources live in the `main` source set. `tdlib/`, `tgcalls/`, `vkryl/`, and `extension/` provide supporting modules;
`buildSrc/` contains Gradle logic. Tests and benchmarks are maintained outside this checkout,
as in upstream Telegram X.

## Build, Test, and Development Commands

Run commands from the repository root using JDK 25, Git LFS, initialized submodules, and the
Gradle wrapper. See `README.md` for SDK/NDK and Rust prerequisites.

- `scripts/setup.sh`: configure Android SDK, NDK, and local properties.
- `./gradlew :app:assembleLatestDebug`: build the latest-flavor debug APK.
- `./gradlew :app:installLatestDebug -Pabi=arm64`: install on a connected ARM64 device.
- `./gradlew :app:lintLatestDebug`: run Android lint checks.

## Coding Style & Naming Conventions

Follow `.editorconfig` and [code style](docs/CODE_STYLE.md): two-space indentation,
100-character lines, descriptive names, and braces around control-flow bodies. Use PascalCase
classes and camelCase methods; Kotlin has no trailing commas. Java method declarations use
a space before `(`. Use Android Studio formatting; introduce no warnings.
Add Kotlin only in new modules outside `org.thunderdog`, tests, or build code; keep it
Java-interoperable or private. Copy neighboring license headers for new app files.
Reuse existing helpers and avoid allocations during drawing, animations, or scrolling.

## Testing Guidelines

The checkout does not include test sources, benchmarks, or `checkMinSdk`. Gradle enables
external test infrastructure only when `tests/.git` exists, following upstream.
Validate changes with `./gradlew :app:assembleLatestDebug` and
`./gradlew :app:lintLatestDebug`. Reproduce bugs before fixing them and verify affected
behavior manually on Android 13+ and arm64 when a device is available. Report failures,
checks not run, and coverage gaps.

## Commit & Pull Request Guidelines

Use imperative commit titles within 65 characters, matching history, and explain why. Follow the
[PR checklist](docs/PULL_REQUEST_TEMPLATE.md): one scoped change, latest `main` base, linked
issue and issue number in bug-fix titles, affected flavors, before/after checks, and coverage gaps.
Include screenshots or recordings for UI changes and measurements for optimizations.
Follow its `@MadeByLLM` annotation and `Context-Id` trailer requirements where applicable.

## Security & Configuration

Never commit `local.properties`, signing keys, API credentials, or account snapshots.
Keep snapshots outside the checkout.
