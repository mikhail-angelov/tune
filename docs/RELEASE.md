# Android CI and releases

The Android application ID is `dev.mangelov.tune`. APKs with the previous `dev.angelov.tune` ID install as a separate app; Android does not carry their saved settings into the new package.

Every branch push, pull request, and published GitHub Release runs `./gradlew build` and the Android UI test on an API 29 emulator. This runs the JVM tests and lint and assembles an unsigned release APK. The unsigned APK is retained as a workflow artifact for seven days, named `Tune-v<version>-unsigned.apk`.

The Android `versionCode` is the workflow run number plus one, so each new run gets a higher code. Branch and pull request builds use `0.0.<run number>` as `versionName`. A tag such as `v1.2.3` uses `1.2.3` as `versionName`.

For a locally installable test build, run `python3 scripts/build_test_apk.py` with `TUNE_TEST_STORE_PASSWORD` and `TUNE_TEST_KEY_PASSWORD` set for the local test keystore. It increments `version.properties` after a successful build, sets the APK's `versionCode` and `versionName`, and creates `app/build/outputs/apk/release/Tune-v<version>-test.apk`. The default keystore is `~/.android/debug.keystore` with alias `androiddebugkey`; `TUNE_TEST_KEYSTORE` and `TUNE_TEST_KEY_ALIAS` can override them. Keep the previous APKs so each version has a distinct file.

To publish an installable APK, add these repository Actions secrets:

- `ANDROID_SIGNING_KEY_BASE64`: base64 of the existing release keystore, with line breaks removed. For example: `base64 < release.jks | tr -d '\n'`.
- `ANDROID_SIGNING_STORE_PASSWORD`: keystore password.
- `ANDROID_SIGNING_KEY_ALIAS`: signing key alias.
- `ANDROID_SIGNING_KEY_PASSWORD`: signing key password.

Keep the keystore backed up securely. Android updates must use the same signing key. Do not commit the keystore or passwords.

Push a semantic tag, for example `git tag v1.2.3` followed by `git push origin v1.2.3`. After the build passes, the workflow signs and verifies `Tune-v1.2.3.apk`, then creates a GitHub Release for that tag and attaches the APK. A rerun replaces the APK asset if the release already exists.
