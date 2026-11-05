# Telegram X

Telegram X is an experimental Android client for [Telegram](https://telegram.org/), based on
[TDLib](https://core.telegram.org/tdlib). This TGX-Next fork supports Android 13+ (API 33+) and
`arm64-v8a` only. See the [migration notes](docs/ANDROID_13_ARM64.md) for details.

## Build

Prerequisites: Git with Git LFS, JDK 25, [rustup](https://rustup.rs), Android SDK and NDK.
Windows builds also require [MSYS2](https://www.msys2.org/) with the UCRT64 environment,
Git LFS, Perl, Make and diffutils; set `msys2.dir` in `local.properties`.

```shell
git lfs install
git clone --recursive https://github.com/TGX-Next/Telegram-X tgx
cd tgx
scripts/setup-rust.sh
scripts/setup.sh
./gradlew :app:assembleLatestDebug
```

Set `rust.dir` in `local.properties` to the value printed by `scripts/setup-rust.sh`. Run that
script again when `version.rust` in `version.properties` changes. If the repository was cloned
without submodules, run `git submodule update --init --recursive` first. The setup script
configures the Android SDK, NDK and local properties; follow any additional build error messages.

To install on a connected ARM64 device, run:

```shell
./gradlew :app:installLatestDebug -Pabi=arm64
```

For a published build, obtain [Telegram API credentials](https://core.telegram.org/api/obtaining_api_id),
set `telegram.api_id` and `telegram.api_hash` in `local.properties`, and configure
[Firebase](https://firebase.google.com/docs/android/setup) for the application ID.

## Tests and checks

```shell
./gradlew :app:testLatestDebugUnitTest
./gradlew :app:emulatorLatestDebugAndroidTest -Pabi=arm64
./gradlew :tests:ui:emulatorLatestNonMinifiedReleaseAndroidTest -Pabi=arm64
./gradlew checkMinSdk
./gradlew :app:lintLatestDebug
```

Device tests require Android 13+ and ARM64. See the [testing guide](tests/README.md) for test
workflows, benchmarks and baseline profiles.

## Contributing

See the [contributor guide](docs/GUIDE.md), [code style](docs/CODE_STYLE.md) and
[pull request template](docs/PULL_REQUEST_TEMPLATE.md).

## License

Telegram X is licensed under [GNU GPL v3.0](LICENSE). Third-party components may have different
licenses; see [third-party dependencies](docs/THIRDPARTY.md).
