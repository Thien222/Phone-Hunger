# PhoneHunger

Android app written in Kotlin and Jetpack Compose. Core battery monitoring and sound playback work without a backend. Google Play Billing needs Play Store access.

## Build without Android Studio

Requirements: JDK 17 or 21, Android SDK platform 36 and Build Tools 35, or use GitHub Actions. On Windows run `gradlew.bat lint testDebugUnitTest assembleDebug`; on Linux/macOS run `./gradlew lint testDebugUnitTest assembleDebug`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Push this source to GitHub and open **Actions → Android CI** to download the debug APK and reports. The separate **Signed AAB** workflow runs manually after four repository secrets are set: `PH_UPLOAD_KEYSTORE_BASE64`, `PH_STORE_PASSWORD`, `PH_KEY_ALIAS`, `PH_KEY_PASSWORD`. It never publishes to Play. Create `PH_UPLOAD_KEYSTORE_BASE64` by base64 encoding the upload keystore, without line breaks. A signed bundle appears as the `phonehunger-signed-aab` artifact.

To run Android UI tests without installing an emulator locally, open **Actions → Android device tests in cloud → Run workflow** after pushing this repository. That job uses [android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner) on a temporary GitHub Linux runner and executes `connectedDebugAndroidTest`. The test APK can be compiled locally with `gradlew.bat assembleDebugAndroidTest`; compiling it does not run the device test. The cloud job has not yet run because this workspace has no GitHub remote.

The app ID `com.phonehunger.app` is provisional. Set a final unique ID before creating the Play Console listing; changing it afterward creates a different app.

## What the app currently does

- Reads battery broadcasts, emits one low-battery warning per threshold crossing with hysteresis and cooldown, debounces plug events, and reacts once to charging reaching full.
- Runs a user-controlled foreground service with a persistent Stop notification; optionally resumes after reboot. This service type requires a Google Play declaration and approval.
- Plays bundled original SFX and, when installed, offline English or Vietnamese Android TTS voices. There is no network TTS or speech asset bundled.
- Persists settings locally with DataStore; supports volume, quiet hours, thresholds, voice language, four battery face colors, a home widget, and an optional non-touchable overlay beside the status bar.
- PRO Audio Studio imports audio using the file picker, records AAC/M4A after microphone permission, creates offline TTS files, trims and sequentially combines clips through Media3, changes pitch/speed, exports M4A, previews files, and assigns custom clips to battery events.
- Queries a one-time Play product `phonehunger_pro_lifetime`, shows its real localized price, handles pending/owned purchases, acknowledges and restores. The last Play-owned state is cached for offline use; client-only verification is weaker than server verification.

## Before release

See [implementation status](docs/IMPLEMENTATION_STATUS.md), [release checklist](docs/RELEASE_CHECKLIST.md) and [test matrix](docs/TEST_PLAN.md). In particular, Play Console product setup, real-device tests, a public privacy policy with owner contact, owner signing key, and Play approval of foreground-service use remain open. The current app is a tested build, not a production-ready Play submission.

No account, ads, analytics, backend, network permission, or third-party speech assets are included.
