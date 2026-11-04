# Android 13 and arm64 migration

TGX-Next supports Android 13 (API 33) and newer on `arm64-v8a` only.
`latest` is the only SDK flavor. The release version-code offsets remain unchanged:
SDK offset `5` and ABI offset `2`.

## Platform and source changes

| Build contract | Original checkout | TGX-Next |
|---|---|---|
| Minimum Android version | API 16 | API 33 |
| SDK flavors | 6 | `latest` |
| Android ABIs | arm64, arm32, x86_64, x86 | arm64 |
| TDLib/OpenSSL prebuilt configurations | 22 | API 33, NDK 30, arm64 |
| Media3 source checkouts | latest, lollipop, legacy | latest |

The Gradle configuration rejects unsupported ABI arguments. Both native CMake entry points
also reject API levels below 33 and ABIs other than `arm64-v8a`. Rust setup and the native
builders select only the arm64 target. Host compiler architectures remain independent of the
Android target.

Obsolete SDK flavors, baseline profiles, Media3 checkouts, dependency versions, multidex and
Dalvik workarounds, ReLinker, and the legacy video transcoder were removed. App and library
helpers use the APIs available on Android 13 directly; checks for newer Android APIs remain.
The bitmap pinning/purgeable path and old external-storage permissions were removed.

Supported Kotlin implementations, Google/Firebase implementations, manifest entries and
resources now use the `main` source sets. Resource variants through API 33 were consolidated;
API 35 variants remain. Ten old launcher PNG variants were removed: the unchanged adaptive
`anydpi` icons take precedence on all supported devices, according to the
[Android resource selection rules](https://developer.android.com/guide/topics/resources/providing-resources).

## Repositories

Only changed submodules were forked. Other submodules retain their upstream URLs and pins.

| Repository | Published branch | Commit |
|---|---|---|
| [TDLib bundle](https://github.com/TGX-Next/tdlib) | `main` | `40d51146716d` |
| [X-Core](https://github.com/TGX-Next/X-Core) | `codex/android13-arm64` | `6ab46f81e826` |
| [X-Android](https://github.com/TGX-Next/X-Android) | `codex/android13-arm64` | `d44a2d89f66b` |
| [LevelDB](https://github.com/TGX-Next/leveldb) | `main` | `836e6fcffc96` |

X-Core and X-Android use the migration branch as their default branch. This preserves the
pinned dependency baselines without overwriting their newer upstream `main` branches.
The parent repository records the exact commits and branch names in `.gitmodules`.

## Validation

All commands use JDK 25, the pinned SDK/NDK/Rust versions and an example application configuration.
No personal Telegram credentials, signing keys or session snapshots are part of this change.

| Check | Result |
|---|---|
| `./gradlew -p buildSrc test --tests SupportedPlatformTest` | 3 tests passed |
| `./gradlew :app:testLatestDebugUnitTest` | 23 tests passed |
| `./gradlew checkMinSdk` | Passed; all 9 checked modules reported zero violations |
| `./gradlew :app:lintLatestDebug` | Passed; zero obsolete-SDK warnings and zero errors |
| `./gradlew :app:assembleLatestDebug` | Passed |
| `./gradlew :app:assembleLatestDebugAndroidTest` | Passed |
| `scripts/check-test-tools.sh -y minsdk` | All expectations passed |
| Gradle with `-Pabi=x64` | Rejected as expected |
| CMake: API 32 or arm32/x86/x86_64 | All 8 negative checks rejected the target |
| Modified shell scripts | All 9 syntax checks passed |
| Fresh TDLib fork clone | Published commit and all 3 LFS libraries downloaded successfully |

The generated APK declares `minSdkVersion=33` with no maximum SDK. All 11 packaged native
libraries were inspected and are ELF64/AArch64 files under `lib/arm64-v8a`. The obsolete
read/write external-storage permissions are absent from the merged APK.

Resource-table comparison retained all 11,897 resource names with no changed values.
All 1,245 retained resource files are byte-identical to the pre-consolidation APK. The only
removed configurations are the ten launcher PNG fallbacks described above; both selected
adaptive icons are unchanged.

Lint still reports six findings from existing code and unchanged dependency pins: three
`MissingExplicitUriGrant` findings in `ShareController` for future Android 18 behavior and
three patch-version recommendations for UI Automator and ReCaptcha. These are outside the
platform-support change.

No Android device was connected. Integration/UI tests, manual calls/media/notification checks,
and baseline-profile regeneration were not run. The integration test APK was compiled only.
The optional HMS extension was not built separately.
