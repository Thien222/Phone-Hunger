# PhoneHunger — Implementation Plan

> Đặc tả dành cho AI coding agent (Codex, Claude Code, Cursor...). Phiên bản kế hoạch 1.0 — 26/11/2026.
> Mục tiêu: triển khai ứng dụng Android đầy đủ, có thể build, kiểm thử và chuẩn bị phát hành Google Play. Đây là kế hoạch, không phải xác nhận đã triển khai hoặc đã được duyệt.

## 1. Product vision

**PhoneHunger — Your phone gets hungry, too.**

Biến chiếc điện thoại thành một sinh vật có giọng nói, cảm xúc và phản ứng theo trạng thái pin. Khi pin yếu, điện thoại réo bụng hoặc than đói; khi cắm sạc, nó phát tiếng ăn uống; khi đầy pin, nó ợ hơi. Người dùng có thể thay đổi giọng, tự tạo âm thanh và tùy chỉnh skin pin.

- Android-first; iOS chỉ xem xét sau, không thuộc phạm vi triển khai này.
- Cài đặt, mở app, nghe demo ngay trong tối đa một thao tác; bật chế độ chạy nền qua một nút và hoàn thành quyền cần thiết của Android.
- Không đăng ký, không tài khoản riêng, không quảng cáo, không yêu cầu bạn bè, không AI API trả phí.
- Local-first, offline cho các tính năng chính; Google Play Billing cần mạng/Play Store khi mua hoặc xác minh lại.
- Hỗ trợ giao diện và lời thoại tiếng Việt (`vi-VN`) và tiếng Anh (`en-US`).
- Free + PRO mua một lần, giá tham chiếu 2.99 USD. Không subscription.
- Tên PhoneHunger là tên làm việc; chưa xác minh nhãn hiệu hoặc tính độc quyền.

## 2. Phạm vi tính năng và mức ưu tiên

Triển khai toàn bộ phạm vi dưới đây theo thứ tự; tính năng overlay nâng cao có cổng kiểm tra tương thích/chính sách trước khi đưa vào bản production. Không biến cả dự án thành một demo UI.

| Module | Free | PRO | Ưu tiên |
|---|---|---|---|
| Pin realtime, cắm/rút sạc, pin đầy | Có | Có | P0 |
| Tiếng bụng đói, ăn, ợ hơi mặc định | Có | Có | P0 |
| Hai bộ giọng mặc định Anh/Việt | Có | Có | P0 |
| Bật/tắt monitor, âm lượng, quiet hours, ngưỡng pin | Có | Có | P0 |
| Nghe thử, mô phỏng sự kiện trong app | Có | Có | P0 |
| Skin pin biểu cảm cơ bản trong app/widget | Có | Có | P0 |
| Giọng dễ thương, cục súc, robot, lười biếng | Giới hạn | Toàn bộ | P1 |
| Custom text-to-speech, chọn giọng, tốc độ/cao độ | Không | Có | P1 |
| Thu âm, nhập file MP3/WAV/M4A | Không | Có | P1 |
| Audio Studio: cắt, ghép tuần tự, hiệu ứng, preview, lưu | Không | Có | P1 |
| Gán âm thanh riêng cho mỗi sự kiện; random sound | Mặc định | Tùy chỉnh | P1 |
| Skin nâng cao, màu, kích thước, animation sạc | Giới hạn | Có | P1 |
| Overlay cạnh thanh trạng thái | Skin cơ bản | Skin nâng cao | P2 |
| Overlay trực tiếp vùng status bar (thử nghiệm) | Có điều kiện | Có điều kiện | P2 |
| Google Play Billing, restore, paywall | Có | Có | P1 |
| Tự khởi động lại monitor sau reboot nếu OS cho phép | Có | Có | P2 |

### 2.1. Battery rules mặc định

| Event ID | Điều kiện | Tiếng Việt | English | SFX |
|---|---|---|---|---|
| LOW_50 | Qua ngưỡng 50% khi xả | Hơi đói rồi nha. | I'm getting hungry. | Bụng sôi nhẹ |
| LOW_30 | Qua 30% | Có gì ăn không? | Got any food? | Bụng réo |
| LOW_20 | Qua 20% | Cho tao ăn coi! | Feed me, please! | Bụng réo |
| LOW_10 | Qua 10% | Đói muốn xỉu rồi! | I'm starving! | Bụng réo lớn |
| LOW_5 | Qua 5% | Mày định bỏ đói tao à? | Are you starving me? | Thở mệt |
| LOW_1 | Qua 1% | Kiếp sau gặp lại... | See you in another life... | Thở yếu |
| POWER_CONNECTED | Chưa cắm → cắm | Aaa, ngon quá! | Finally, some food! | Nhai/nuốt |
| POWER_DISCONNECTED | Cắm → rút | Ơ, đang ăn mà! | Hey! I'm still eating! | Âm thanh bất ngờ |
| FULL | Lần đầu đạt 100% khi cắm | Ợ... no quá! | Burp... I'm full! | Ợ hơi |

- Mặc định kích hoạt 20%, 10%, 5%, 1%, cắm/rút và đầy pin; 50%/30% là tùy chọn để tránh làm phiền.
- Qua ngưỡng: `previousLevel > threshold && currentLevel <= threshold`, không dùng điều kiện bằng tuyệt đối. Nếu nhảy qua nhiều ngưỡng thì chỉ phát cảnh báo nghiêm trọng nhất, đánh dấu các ngưỡng đã đi qua.
- Phát một lần mỗi chu kỳ xả cho từng ngưỡng; reset khi sạc lên vượt ngưỡng với hysteresis (đề xuất 3%). Full chỉ một lần mỗi phiên sạc. Không phát khi khởi tạo snapshot đầu tiên, trừ demo do người dùng chủ động.
- Debounce sự kiện cắm/rút (đề xuất 1–2 giây), cooldown mặc định 30 giây cho cảnh báo pin, không queue vô hạn; ưu tiên sự kiện mới phù hợp, không phát chồng tiếng.
- Tôn trọng chế độ im lặng/DND, audio focus, quiet hours, cuộc gọi; không tự thay đổi system volume, không dùng alarm stream để vượt chế độ im lặng. Âm lượng app chỉ điều chỉnh mức gain của app.
- Không phát tiếng ăn lặp liên tục trong suốt thời gian sạc; chỉ phát clip ngắn khi có sự kiện.
- Nhiệt độ pin và các trigger Wi-Fi/Bluetooth/tai nghe là mở rộng sau bản đầu; không yêu cầu thêm quyền không cần thiết.

## 3. Stack kỹ thuật

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Kotlin Native |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Kiến trúc | MVVM + Repository, StateFlow, Coroutines; DI thủ công đơn giản |
| Build | Gradle Kotlin DSL, Gradle Wrapper, version catalog |
| SDK | minSdk 26 (đề xuất), compileSdk/targetSdk 36 trở lên theo yêu cầu Play hiện hành |
| Pin | BatteryManager, dynamic BroadcastReceiver, ACTION_BATTERY_CHANGED |
| Background | Foreground Service nếu đủ điều kiện chính sách; persistent notification có Stop |
| Audio playback | Media3 ExoPlayer hoặc native MediaPlayer; chọn một engine chính |
| Audio edit | Media3 Transformer/Composition, AudioProcessor; MediaCodec/MediaExtractor khi cần |
| TTS | Android TextToSpeech, synthesizeToFile, UtteranceProgressListener |
| Recording | MediaRecorder hoặc AudioRecord, chỉ khi màn thu âm đang hoạt động |
| Storage | DataStore Preferences, JSON metadata trong app-private storage; không cần Room nếu chưa cần |
| Import | Storage Access Framework (ACTION_OPEN_DOCUMENT), copy vào sandbox |
| Widget | AppWidget/Glance, cập nhật theo sự kiện và giới hạn hệ thống |
| Overlay | WindowManager; AccessibilityService chỉ ở biến thể thử nghiệm nếu thực sự cần |
| Billing | Google Play Billing Library phiên bản còn được Play hỗ trợ tại ngày build |
| Test | JUnit, coroutine tests, Compose UI tests, Genymotion Cloud |
| CI | GitHub Actions, JDK phù hợp AGP (ưu tiên JDK 17 nếu tương thích) |

Dùng phiên bản stable tương thích của Kotlin/AGP/Compose/Gradle/Media3/Billing. Pin version trong `libs.versions.toml`; không bịa version hay dùng `+`. Kiểm tra tài liệu chính thức trước khi chọn.

## 4. Kiến trúc và thư mục đề xuất

Một app module với package theo feature đủ cho v1; không over-engineer multi-module.

```text
PhoneHunger/
├── .github/workflows/android-ci.yml
├── gradle/libs.versions.toml
├── gradle/wrapper/                  # Commit wrapper đầy đủ
├── gradlew / gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/phonehunger/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── PhoneHungerApp.kt
│       │   │   ├── core/model/
│       │   │   ├── core/storage/
│       │   │   ├── core/audio/
│       │   │   ├── feature/battery/
│       │   │   ├── feature/monitor/
│       │   │   ├── feature/voice/
│       │   │   ├── feature/studio/
│       │   │   ├── feature/skin/
│       │   │   ├── feature/widget/
│       │   │   ├── feature/billing/
│       │   │   ├── feature/settings/
│       │   │   └── ui/theme/
│       │   ├── assets/audio/        # Bundled SFX & EN/VI voice assets
│       │   └── res/values/ and values-vi/
│       ├── debug/                   # Fake battery source, fake billing, debug-only tools
│       ├── test/
│       └── androidTest/
├── docs/
│   ├── PRIVACY_POLICY.md
│   ├── TERMS_OF_SERVICE.md
│   ├── AUDIO_LICENSES.md
│   ├── TEST_PLAN.md
│   └── RELEASE_CHECKLIST.md
└── README.md
```

Package name chỉ là placeholder; owner chốt applicationId duy nhất trước khi tạo sản phẩm Play Console. Không đổi applicationId tùy tiện sau phát hành.

### 4.1. Models & interfaces

- `BatterySnapshot(level, isPlugged, status, timestamp)`; normalize level/scale, xử lý unknown.
- `BatteryEvent(type, level, timestamp)`; event engine thuần Kotlin, test độc lập Android.
- `TriggerRule(id, enabled, threshold?, soundIds, random, cooldownMs)`.
- `AudioClip(id, title, sourceType, localPath, durationMs, language?, createdAt)`.
- `AudioEditProject(id, segments, gain, pitch, speed, fadeInMs, fadeOutMs)`; mỗi segment có clipId/startMs/endMs.
- `UserSettings(locale, monitoringEnabled, volume, quietHours, personality, skinId, overlayEnabled)`.
- `Entitlement(state, lastVerifiedAt)` với FREE/PRO/UNKNOWN, không tin boolean UI đơn thuần.
- Interfaces: `BatterySource`, `AudioPlayer`, `AudioEditor`, `VoiceSynthesizer`, `EntitlementRepository`, `SettingsRepository`.

```text
BatterySource -> BatteryEventEngine -> TriggerRuleEvaluator
                                    -> AudioPlaybackManager
                                    -> UI / Widget / Overlay state
DataStore + Local Audio Library ----^ 
```

## 5. UI/UX

Thiết kế dark-first, typography rõ ràng, vui nhộn, ít nút; hỗ trợ light/system theme, screen reader, font scaling, edge-to-edge. Không bắt onboarding nhiều màn; không paywall trước khi người dùng được thử hiệu ứng mặc định.

- **Home:** Battery Face lớn, pin thật, trạng thái đói/no, một toggle `Enable Phone Voice`, nút `Try sound` dùng ngay, quick access Voice / Skin / Studio. Nếu monitor chưa được bật hoặc bị OS dừng, hiển thị trạng thái thật.
- **Voices:** chọn ngôn ngữ/tính cách, danh sách event, nút nghe thử, chọn/gán clip. Tách ngôn ngữ giao diện và ngôn ngữ giọng nói nếu thuận tiện.
- **Audio Studio:** chọn Record / Import / Text to speech; waveform nhẹ (downsample); cắt bằng 2 handle, reorder/ghép clip, speed/pitch/gain, preset robot/trầm/cao, preview, Save & Assign. Không bắt user chỉnh âm thanh để dùng app.
- **Battery Skins:** mặt vui khi đầy, bình thường, đói, khóc, kiệt sức, đang ăn khi sạc. Custom màu, size, vị trí; preview trước khi cấp quyền. Widget và overlay là tùy chọn.
- **Settings:** ngưỡng pin, cooldown, quiet hours qua đêm, tôn trọng silent/DND, monitor status, permission help, ngôn ngữ, restore purchase, xóa audio/dữ liệu, privacy/terms.
- **PRO:** paywall ngắn, giá thật từ Play, giải thích mua một lần, nút Restore; đóng được.

Mọi string UI dùng resource localization; không hardcode tiếng Anh trong composable. Demo trong Home có sẵn clip tiếng Anh và Việt do dự án có quyền sử dụng; thiếu clip production là release blocker, không thay bằng file rỗng rồi báo hoàn thành.

## 6. Battery engine và chạy nền (rủi ro kỹ thuật lớn nhất)

1. Đọc sticky broadcast để lấy snapshot ban đầu; `ACTION_BATTERY_CHANGED` đăng ký động, không trông chờ receiver trong manifest. Kiểm tra API/receiver flags theo phiên bản Android.
2. Monitor khi app mở; sau khi user bật, thử nghiệm FGS với notification thường trực có Stop. Trên Android mới, FGS phải có type/permission và mục đích hợp lệ; đánh giá `specialUse` và khai báo subtype nếu thực sự phù hợp. Không khai báo `mediaPlayback` chỉ để giữ monitor sống khi không phát media.
3. Không polling liên tục, không wakelock vô hạn, không microphone chạy nền, không restart vòng lặp. WorkManager không đảm bảo real-time và không được coi là thay thế FGS.
4. Persist trạng thái rule/debounce hợp lý; xử lý process death, reboot, Doze, notification bị chặn, permission revoke, OEM battery optimization. Boot recovery chỉ nếu user opt-in và Android cho phép; nếu không, hướng dẫn mở lại app. Force-stop phải được tôn trọng.
5. Foreground service không đảm bảo chạy 24/7 trên mọi máy. Nếu use case monitor không được Play chấp thuận, không tìm cách lách; dùng chế độ khi app mở/widget với giới hạn được công bố và đánh giá lại khả năng thương mại trước khi phát hành.
6. Tách `FakeBatterySource` vào debug source set; debug panel giả lập level/charging để test nhanh trên cloud. Không có debug panel hoặc fake entitlement trong release.

## 7. Voice & Audio Studio

- Default assets: SFX và lời thoại EN/VI đã ghi âm hoặc được cấp quyền thương mại. Có manifest asset + license/source; không tải meme, giọng celeb hay âm thanh có bản quyền không rõ quyền. Nếu dùng TTS để tạo asset đóng gói, kiểm tra điều khoản phân phối bản ghi của engine/voice.
- TTS custom: chọn locale, voice hỗ trợ offline (`!isNetworkConnectionRequired`), pitch/rate; đợi init và callback synthesizeToFile thành công mới đưa vào library. Nếu thiếu voice/engine, giải thích cách cài, cho dùng clip mặc định/thu âm. Không gửi text tới network TTS mặc định.
- Recording: RECORD_AUDIO chỉ khi người dùng chủ động bấm Record; xử lý từ chối quyền, stop, cancel, lỗi mic. Không lưu âm thanh không mong muốn.
- Import bằng SAF; validate loại file, decode được, giới hạn dung lượng/thời lượng, copy file vào app-private. Không xin quyền truy cập toàn bộ bộ nhớ.
- Cắt/ghép tuần tự bằng pipeline Media3 phù hợp phiên bản, chuẩn hóa sample rate/channel khi cần, xử lý encoder/codec failure. Đề xuất output AAC-LC/M4A; WAV khi cần trung gian. Không đưa FFmpeg full hoặc model AI vào bản đầu.
- Hiệu ứng: gain, fade, pitch/rate độc lập nếu pipeline hỗ trợ; robot bằng DSP đơn giản có test; không tuyên bố voice cloning. Preview phải phản ánh output thật. Lưu file atomic, không ghi đè bản gốc; cancel export và dọn temp file.
- Giới hạn ban đầu (có thể điều chỉnh): import tối đa 20 MB/120 giây, mỗi clip trigger tối đa 15 giây, project ghép tối đa 60 giây, thu âm tối đa 60 giây. Nếu source quá dài, hướng dẫn cắt trước khi gán. Không decode toàn bộ file lớn vào RAM; xử lý IO/CPU ngoài main thread.
- Xóa clip đang được gán phải hỏi xác nhận, reset rule về clip mặc định, dọn file mồ côi. Không upload file lên server. Cho xóa toàn bộ custom audio.

## 8. Battery Skin, widget và overlay

- Skin trong app là chức năng chắc chắn; widget là phương án an toàn cho home screen. Không hứa widget cập nhật mỗi giây hoặc hiển thị được trên mọi lock screen.
- Android không cho app thường thay icon pin gốc. `TYPE_APPLICATION_OVERLAY` nằm dưới các cửa sổ hệ thống quan trọng như status bar. Dùng overlay cạnh/dưới status bar, tôn trọng insets, cutout, rotation, density và vùng thao tác hệ thống.
- `SYSTEM_ALERT_WINDOW` là quyền đặc biệt, user tự cấp; không xin khi chưa bật overlay. Overlay không chiếm input, không che nút bảo mật, không giả danh thông báo hệ thống; có nút tắt trong app/notification. Không lạm dụng full screen intent.
- `TYPE_ACCESSIBILITY_OVERLAY` có thể thử nghiệm trong một flavor riêng (ví dụ `lab`), không mặc định có trong `play` flavor. Không đọc nội dung màn hình, không theo dõi accessibility events ngoài nhu cầu, không tự cấp quyền, không giả là công cụ hỗ trợ người khuyết tật. Chỉ tích hợp vào production khi đã xác minh tính cần thiết, phù hợp chính sách, disclosure/consent và declaration; kết quả phụ thuộc Google Play và OEM.
- Nếu không khả thi, production dùng widget/overlay thông thường; UI ghi rõ không thay icon gốc. Không root, không sửa SystemUI, không đòi ADB từ người dùng.

## 9. Billing và dữ liệu

- One-time non-consumable product đề xuất: `phonehunger_pro_lifetime`. Giá tham chiếu $2.99, lấy giá định dạng theo quốc gia từ ProductDetails; không hardcode giá trên paywall.
- Kết nối BillingClient, query product/owned purchases, xử lý PURCHASED/PENDING/CANCELLED/error; chỉ cấp PRO cho giao dịch hợp lệ PURCHASED, acknowledge trong 3 ngày, không consume. Restore và reconcile khi app resume/kết nối lại; xử lý refund/revocation khi có thông tin mới.
- No-backend v1: dùng client-side verification hợp lý (kiểm tra product/state và chữ ký khi áp dụng), local cached entitlement cho offline sau khi xác minh. Không cấp PRO khi chưa xác minh lần đầu; lỗi network không tự tước entitlement đã xác minh. Đây không phải chống crack/thu hồi tức thời. Nếu cần xác minh mạnh, bổ sung backend nhỏ dùng Google Play Developer API/RTDN, không nhúng service-account secret vào APK.
- Debug fake billing để thử UI và unlock, không xuất hiện trong release. Test giao dịch thật bằng license tester trên Play internal/closed track với package và signing phù hợp; sideload APK đơn thuần không chứng minh Billing hoạt động.
- Local storage: version hóa schema và migration, ghi atomic, không lưu dữ liệu nhạy cảm vào log. Tắt backup hoặc cấu hình exclude custom recordings/entitlements nếu cam kết dữ liệu chỉ ở máy. Không analytics/ads SDK ở v1.

## 10. Build và test không cần Android Studio / điện thoại Android

Owner sử dụng Windows, VS Code/Codex, GitHub Actions và Genymotion Cloud. Không yêu cầu cài JDK/SDK/emulator trên máy nếu chọn cloud build; ADB/gmsaas CLI tùy chọn để debug từ xa.

### 10.1. GitHub Actions

- Commit Gradle Wrapper, version catalog, source/assets và CI workflow. Không commit `local.properties`, keystore, credentials hay file thu âm cá nhân.
- Trigger `push`, `pull_request`, `workflow_dispatch`; Linux runner, checkout, setup JDK, setup Gradle và Android SDK/platform API 36 theo yêu cầu.
- CI chạy `./gradlew --no-daemon lint testDebugUnitTest assembleDebug` (nếu có flavor thì dùng task tương ứng, ví dụ `lintPlayDebug testPlayDebugUnitTest assemblePlayDebug`). Upload APK và test/lint reports bằng upload-artifact.
- Release riêng qua manual workflow: `bundleRelease`/`bundlePlayRelease`, signing bằng upload key được lưu trong GitHub Secrets, không in ra logs; chỉ tạo signed AAB khi cấu hình có thật. Không tự upload production.
- Bật R8/resource shrinking cho release sau khi test; output debug APK để sideload, AAB để phát hành.
- README ghi cách mở Actions, tải artifact, cài APK và chạy lại build. Báo rõ nếu build chưa chạy được do thiếu quyền/tài nguyên; không giả báo thành công.

### 10.2. Genymotion Cloud

1. Truy cập https://www.genymotion.com/product-cloud/ ; tạo virtual Android device có API phù hợp, ưu tiên test API 36 và thêm một phiên bản thấp hơn nếu có.
2. Download APK artifact từ GitHub Actions, upload qua Install Apps widget hoặc dùng ADB từ xa.
3. Dùng Battery widget đổi phần trăm pin và trạng thái sạc; kiểm tra loa, audio routing và mic nếu instance hỗ trợ.
4. Kiểm tra background, notification, overlay, revoke quyền; thu thập Logcat. Nếu thiếu TTS voice/GApps/Google Play, ghi nhận hạn chế và dùng debug fake billing cho UI. Cài Play Store theo hướng dẫn Genymotion khi image hỗ trợ, nhưng kiểm thử giao dịch thật vẫn cần cấu hình Play hợp lệ.
5. Kết thúc/tắt instance ngay sau test để tránh phí cloud phát sinh. Kiểm tra pricing hiện hành trước khi dùng.

Debug commands (sau khi ADB đã kết nối; chỉ chạy trên thiết bị test):

```bash
adb devices
adb install -r app-debug.apk
adb logcat
adb shell dumpsys battery unplug
adb shell dumpsys battery set level 25
adb shell dumpsys battery set level 20
adb shell dumpsys battery set level 5
adb shell dumpsys battery set usb 1
adb shell dumpsys battery set level 100
adb shell dumpsys battery reset
```

Một số image không hỗ trợ tất cả lệnh; dùng Battery widget hoặc debug panel. Test trên cloud không thay thế được kiểm thử thiết bị thật về tiêu thụ pin, OEM task killer, overlay, âm thanh phần cứng.

## 11. Test matrix & definition of done

| ID | Kịch bản | Kết quả bắt buộc |
|---|---|---|
| T01 | Cài mới, mở Home | Thử âm thanh trong 1 thao tác, không tài khoản |
| T02 | 21% → 19% → 19% | LOW_20 phát đúng một lần |
| T03 | 25% → 4% | Chỉ phát cảnh báo nghiêm trọng nhất, không spam |
| T04 | Cắm/rút, 99% → 100% | Đúng clip, đầy pin không lặp |
| T05 | Bật silent/DND/quiet hours | Không tự vượt cài đặt âm thanh |
| T06 | Background, khóa màn, reboot, force-stop | Phản ứng nếu OS cho phép; không restart trái phép; hiển thị đúng trạng thái |
| T07 | Đổi EN/VI, thiếu offline TTS | UI/voice đúng; có fallback rõ ràng |
| T08 | Record/import/cắt/ghép/export | File thật phát được, không mất bản gốc, preview đúng |
| T09 | File lỗi/quá dài/hết dung lượng/cancel | Không crash; lỗi rõ, cleanup |
| T10 | Tắt quyền mic/overlay/notification | Graceful degradation, không tự cấp lại quyền |
| T11 | Skin/widget/overlay, xoay máy | Đúng state; không che UI bảo mật hoặc nhận touch ngoài ý muốn |
| T12 | PRO purchase/pending/cancel/restore/refund | Entitlement đúng; pending không unlock; acknowledge đúng |
| T13 | Offline, process death, xóa dữ liệu | Core chạy offline; settings lưu đúng; có thể xóa clip |
| T14 | Release build | Không fake billing/debug panel/secret; APK/AAB hợp lệ |

- Unit test engine ngưỡng, hysteresis, debounce, event priority, quiet hours qua nửa đêm, mapping, entitlement, validate audio segments; inject clock cho test deterministic.
- Instrumentation/UI test luồng chính và permission denial; chạy trên cloud nếu cấu hình được, không giả đã chạy.
- Mục tiêu dung lượng: download APK/AAB split release dưới 20 MB nếu khả thi; đo APK Analyzer hoặc Play Console, báo số thực tế. Không đánh đổi chức năng hoặc tính ổn định để đạt con số giả.
- Mục tiêu hiệu năng: mở Home nhanh, không giật UI khi export, không busy loop khi idle, giải phóng player/receiver/service đúng lifecycle. Đo CPU/RAM, kích thước và battery drain trên thiết bị thật trước production; không coi là đạt nếu chỉ có ước tính.
- Ghi test report với Android/API, image/hãng máy, PASS/FAIL/NOT RUN và lý do. Cần mượn/thuê thiết bị hoặc tuyển tester Android thật trước khi public; ưu tiên Samsung, Xiaomi, Oppo/Realme và Pixel nếu có.

## 12. Release và pháp lý

Google Play duyệt app không có nghĩa đã hoàn thành nghĩa vụ pháp lý. Không tự đánh dấu các mục dưới đây đã xong nếu owner chưa thực hiện.

- Tạo Google Play Developer account, xác minh danh tính/thiết bị nếu được yêu cầu, payments profile, ngân hàng và thông tin thuế; phí đăng ký tham chiếu $25 một lần.
- Kiểm tra target API, phiên bản Billing, 16 KB page-size compatibility nếu có native libs; Play App Signing, store listing, content rating, Data safety, privacy policy URL công khai.
- Privacy Policy EN/VI mô tả xử lý local, mic, TTS, file, Billing, quyền overlay, xóa dữ liệu, contact thật; Terms of Service và quyền mua PRO. Không khai không thu thập dữ liệu nếu SDK thực tế gửi dữ liệu. Owner duyệt văn bản trước khi công bố.
- Khai báo FGS và Accessibility nếu có, disclosure/consent rõ, video demo theo yêu cầu. Không sử dụng Accessibility nếu không đủ điều kiện; giữ bản Play không có tính năng rủi ro khi chưa được xác minh.
- Tên/nhãn hiệu, license âm thanh, ảnh/icon, mã nguồn/thư viện. Không dùng tài sản bên thứ ba khi chưa có quyền thương mại; không đóng gói nội dung tình dục rõ ràng.
- Tạo sản phẩm PRO trong Play Console, internal testing + license testers. Tài khoản cá nhân mới thuộc diện áp dụng cần closed test với ít nhất 12 tester opt-in liên tục 14 ngày, sau đó xin production access; không đảm bảo được duyệt ngay.
- Owner xác minh nghĩa vụ đăng ký kinh doanh, thuế, kê khai, thương mại điện tử và bảo vệ dữ liệu tại Việt Nam theo mô hình thực tế, nhờ cơ quan thuế/chuyên gia khi cần. Không hardcode ngưỡng thuế vào app hoặc coi Google đã nộp thay mọi loại thuế.
- Không publish, thanh toán tiền cloud, tạo tài khoản hay dùng thông tin thật của owner nếu chưa có sự đồng ý/quyền truy cập.

## 13. Trình tự triển khai cho AI agent

1. **Bootstrap:** kiểm tra repo hiện tại, nếu trống scaffold Gradle/Kotlin/Compose, localization, CI, README; chạy build và sửa lỗi.
2. **Core:** BatterySource + pure event engine + DataStore + Home + bundled/demo audio + monitor; unit test và debug battery simulator. Kiểm tra FGS sớm vì đây là giá trị chính của app.
3. **Voice & Studio:** audio library, TTS, recording/import, trim/merge/effects, preview, assign event; test output thực tế và lỗi.
4. **Skin & widget:** biểu cảm, theme, widget, charging animation; overlay thông thường, thử nghiệm Accessibility ở flavor riêng, test và ghi giới hạn.
5. **Billing:** product, paywall, restore, entitlement cache, fake debug billing, test Play; đảm bảo release không bypass.
6. **Hardening:** lint/tests, offline, Doze, OEM/permissions, performance, R8, privacy/terms/license inventory.
7. **Delivery:** APK debug, signed AAB nếu có khóa thật, hướng dẫn Genymotion/Play, test report, release checklist, danh sách blocker.

### Chỉ thị cuối cho AI coding agent

> Hãy trực tiếp triển khai toàn bộ dự án theo tài liệu này, không chỉ trả lại kế hoạch hoặc UI mockup. Đọc source hiện tại trước khi sửa. Chia công việc theo 7 bước, build/test sau từng bước và sửa lỗi có thể xử lý. Sử dụng API thật, không placeholder logic cho chức năng cốt lõi. Với công việc cần tài khoản, asset có license, signing key, thanh toán hoặc thiết bị thật, tạo cấu hình/hướng dẫn rõ và ghi blocker thay vì giả hoàn thành. Không thêm backend, quảng cáo, đăng ký, AI API hoặc quyền Android ngoài phạm vi nếu không có lý do được nêu rõ. Cuối cùng báo cáo các file đã tạo/sửa, lệnh build, vị trí APK/AAB, kết quả test thực tế, tính năng chưa xác minh và việc owner phải thực hiện. Ưu tiên trải nghiệm mở app dùng ngay và hoạt động ổn định, nhẹ, offline; không hy sinh tuân thủ Google Play để đạt tính năng overlay/chạy nền.

## 14. Tài liệu kỹ thuật chính thức để đối chiếu

- Target SDK: https://developer.android.com/google/play/requirements/target-sdk
- Battery broadcast: https://developer.android.com/reference/android/content/Intent#ACTION_BATTERY_CHANGED
- FGS types: https://developer.android.com/about/versions/14/changes/fgs-types-required
- FGS policy: https://support.google.com/googleplay/android-developer/answer/16559646
- Overlay: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
- Accessibility policy: https://support.google.com/googleplay/android-developer/answer/16558241
- TTS: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Media3 composition: https://developer.android.com/media/media3/transformer/composition
- Media3 transformations: https://developer.android.com/media/media3/transformer/transformations
- Billing: https://developer.android.com/google/play/billing/integrate
- One-time purchase lifecycle: https://developer.android.com/google/play/billing/lifecycle/one-time
- Genymotion deploy: https://docs.genymotion.com/usage/saas/deploy_apps/
- Genymotion Battery widget: https://docs.genymotion.com/features/basic_features/
- Play user data: https://support.google.com/googleplay/android-developer/answer/10144311
- Play testing: https://support.google.com/googleplay/android-developer/answer/14151465

Các yêu cầu SDK/chính sách được đối chiếu khi lập kế hoạch; kiểm tra lại tại thời điểm submit. Các giới hạn thời lượng, minSdk, giá và kiến trúc là quyết định đề xuất cho v1, không phải kết quả đo hoặc cam kết doanh thu.
