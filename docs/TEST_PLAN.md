# Test report — 27 November 2026

Environment: Windows 10, JDK 21, Android SDK 36 in workspace, Gradle 8.13, AGP 8.13.2. No Android device or emulator was connected.

| Test | Result | Evidence / remaining work |
|---|---|---|
| Gradle lint | PASS | `gradlew.bat lint` |
| Unit tests: crossing, skip, hysteresis, full, debounce, cooldown, quiet hours, segment bounds | PASS | 8 tests, 0 failures via `gradlew.bat testDebugUnitTest` |
| Debug APK | PASS | `gradlew.bat assembleDebug` |
| Release R8 and AAB build | PASS | `gradlew.bat bundleRelease`; output is unsigned without owner key |
| Android instrumentation test APK | PASS (compile only) | `gradlew.bat assembleDebugAndroidTest` |
| GitHub cloud Android UI test | PASS | [Run 45961049126](https://github.com/Thien222/Phone-Hunger/actions/runs/45961049126): API 35 Google APIs emulator, 2 Compose tests passed (Home/Settings and debug battery update) |
| Install and first launch | PARTIAL | Cloud emulator installed APK and opened Home; audio output, widget and overlay still require separate device checks |
| Import, record, TTS, Media3 export and playback | NOT RUN | Requires Android device with codecs, microphone and offline voices |
| Background, reboot, force-stop, notification denial, DND | NOT RUN | Requires API 26/36 and OEM devices |
| Play purchase, pending, refund, restore | NOT RUN | Requires Play Console product, signing and license tester |

For device tests, follow scenarios T01–T14 in `PhoneHunger_IMPLEMENTATION_PLAN.md`. Run `adb shell dumpsys battery` changes on a test device and always reset battery simulation afterward. Do not assume a cloud device represents OEM battery management or physical audio behavior.
