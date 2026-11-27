# Implementation status — 27 November 2026

The repository started with only `PhoneHunger_IMPLEMENTATION_PLAN.md`. This status distinguishes implemented source from device-verified behavior.

| Phase | Implemented | Still open |
|---|---|---|
| 1. Bootstrap | Gradle Kotlin DSL, pinned versions, wrapper, Compose UI, EN/VI resources, CI, original vector icon | Final app ID |
| 2. Core | Battery broadcast source, pure event engine, service, quiet hours, thresholds, volume, original SFX, offline TTS fallback, widget, debug-only battery simulator, tests | Runtime validation on Android; no bundled spoken recordings |
| 3. Voice / Studio | Four pitch/rate personalities, offline voice picker, offline TTS file creation, mic recording, SAF import, private library, decoded PCM waveform, trim, sequential merge, speed, pitch, gain, fades, rendered preview, export, event assignment with random selection, delete | Robust codec matrix tests |
| 4. Skin / overlay | Battery face/color/size with charging pulse, widget, opt-in non-touchable overlay with left/right position below status bar | Experimental status bar/Accessibility overlay |
| 5. Billing | Product query, localized price, one-time purchase, pending/owned handling, acknowledge, restore, offline last-owned cache | Play Console product and real transaction tests; no backend verification; no debug fake billing |
| 6. Hardening | Lint, eight JVM tests, Android UI test source and compiled test APK, release R8, no direct network permission, verified no debug simulator strings in release DEX | Run cloud instrumentation workflow after pushing to GitHub; OEM/Doze/force-stop tests, performance/battery measurements, owner legal review |
| 7. Delivery | Debug APK, unsigned release AAB, GitHub Actions for CI and signed AAB with secrets, documentation | Signed AAB and Play internal testing require owner keystore/account |

The app is **not ready for public Google Play submission**. The foreground-service `specialUse` declaration must be accepted by Play; a rejection may require changing the core background behavior. Additional runtime and purchase tests are needed. The plan's bundled EN/VI speech asset requirement is unmet because no licensed recordings were available; runtime offline TTS and original SFX are implemented instead. No claim of Google Play approval is made.
