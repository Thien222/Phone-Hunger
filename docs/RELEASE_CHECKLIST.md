# Release checklist

- [ ] Confirm final application ID, app name/trademark and owner contact.
- [ ] Review and host the English/Vietnamese privacy policy; review terms and Data safety answers against the final dependency set.
- [ ] Create one-time non-consumable Play product `phonehunger_pro_lifetime`; configure price by country and test with license accounts.
- [ ] Create upload key and set GitHub Actions signing secrets; check signed AAB with `jarsigner -verify` and Play internal testing.
- [ ] Submit Play Console foreground-service `specialUse` declaration with real explanation and demo. Google Play may reject battery monitoring as an FGS use; remove the FGS from a Play variant if rejected and state the reduced background behavior clearly.
- [ ] Test overlay permission flow and ensure overlay never obstructs system controls on supported devices.
- [ ] Test T01–T14 on Android API 26 and 36 plus real Pixel, Samsung, Xiaomi, Oppo/Realme devices where available.
- [ ] Confirm offline EN/VI TTS behavior on devices with and without installed offline voice packages.
- [ ] Review audio asset rights, app icon/listing assets, support email, store listing, content rating and applicable tax/business obligations.
- [ ] Recheck target API and Billing version against current Play requirements at submission time.

No AccessibilityService is included in the Play build. The app does not replace the system battery icon. The current release bundle is unsigned until an owner upload key is supplied.
