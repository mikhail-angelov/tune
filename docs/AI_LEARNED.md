## 2026-09-28 — Kotlin version for the pinned Android build

### Goal

Build a minified release APK with AGP 8.13.2 and Gradle 8.14.3 on this machine.

### Golden path

Use Kotlin 2.3.21 in `gradle/libs.versions.toml`, JDK 21 from Android Studio, and run `./gradlew build`.

### Verification

`./gradlew build` and `./gradlew :app:connectedDebugAndroidTest` passed. The release APK was built with R8 and was 1.9 MB.

### Failure pattern avoided

Kotlin 2.4.20 compiled and passed tests, but R8 emitted Kotlin metadata parsing warnings during release minification.

### Ruled-out approaches

- Tried Kotlin 2.4.20 with AGP 8.13.2; release build succeeded but R8 reported metadata parsing warnings, so it did not meet the warning-free release requirement.

### Notes

Keep `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`, since the JDK path is machine-specific.

## 2026-09-28 — Installable APK for local phone checks

### Goal

Provide a locally built APK that can actually be installed for a manual Android test.

### Golden path

Run `./gradlew :app:assembleRelease`, align `app-release-unsigned.apk` with Build Tools `zipalign`, sign the aligned APK with a local test keystore using `apksigner`, and verify it with `apksigner verify`. Keep the resulting test APK under `app/build/outputs/apk/release/` so Git ignores it.

### Verification

`apksigner verify` passed, and `adb install -r` plus launching `dev.mangelov.tune/.MainActivity` succeeded on the API 29 emulator.

### Failure pattern avoided

A successful release Gradle task can leave only `app-release-unsigned.apk`, which is not ready for a user to install.

### Ruled-out approaches

- Built only with `assembleRelease`; it produced an unsigned APK, so it did not satisfy the request for a phone-test build.

### Notes

A test-signed APK cannot update an installation signed with a different key. GitHub tag releases use the separately configured release keystore secrets.
## 2026-09-28 — Versioned local test APK

### Goal

Build an installable APK for each phone test without reusing the previous Android version or filename.

### Golden path

Set `TUNE_TEST_STORE_PASSWORD` and `TUNE_TEST_KEY_PASSWORD` for the local test keystore, then run `python3 scripts/build_test_apk.py` (with Android Studio's JBR as `JAVA_HOME` if needed). The script advances `version.properties` only after assembling, signing, and checking the versioned APK.

### Verification

The script produced `Tune-v1.0.1-test.apk` with `versionCode=2` and `versionName=1.0.1`; `adb install -r` succeeded on `emulator-5554`. `./gradlew --no-daemon build` and `actionlint` passed.

### Failure pattern avoided

A fixed `Tune-1.0-test.apk` name and unchanged `versionCode=1` make successive test builds indistinguishable and can block Android upgrades.

### Ruled-out approaches

- Putting `java.util.Properties()` directly in the Gradle Kotlin script failed because `java` resolved to the Gradle DSL extension; importing `java.util.Properties` and using `Properties()` worked.
## 2026-09-28 — Keep tuner readings steady between plucks

### Goal

Prevent a tuner screen from blinking or changing targets when a string decays into silence.

### Golden path

Keep the last detected string, frequency, and cents in `TunerUiState.Detected` when `PitchTracker` reports silence. Use the nearest string of the current tuning as the cents target, and require consecutive stable readings before switching strings. Give the gauge fixed slots for the string line and deviation line.

### Verification

`./gradlew --no-daemon build :app:connectedDebugAndroidTest --quiet` passed, including three UI tests. The screen layout was inspected on the emulator, and `Tune-v1.0.2-test.apk` installed with `versionCode=3` and `versionName=1.0.2`.

### Failure pattern avoided

Changing `Detected` to `Listening` on every silence event and replacing a detuned string with a chromatic note makes the displayed target blink and the needle measure against the wrong pitch.

### Ruled-out approaches

- The earlier fixed-height gauge kept the card size steady but still replaced the note with a dash on every silence event; layout height alone did not preserve the reading.
- The earlier 50-cent cutoff hid the target string and showed a chromatic note; this did not measure deviation from a guitar string.
