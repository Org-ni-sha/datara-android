# Signed-in UI redesign

The user requested a broader visual redesign using UI UX Pro Max and Material 3, and explicitly authorized relaxing earlier Markdown design constraints. The provisional user-defined quota flow remains UI-only and subject to adviser review.

## Direction

Light backgrounds and white surfaces frame a dark navy forecast hero. Larger typography makes the forecast the first thing to read. Mint surfaces and small lime accents add contrast without replacing navy as the brand accent. Outline icons, consistent spacing and quieter sample labels reduce visual noise.

Home, Create Quota, Quota Details, History, Notifications, Settings and Profile share the revised system. Native Material 3 app bars, navigation items, outlined fields, segmented controls, cards, list items and buttons replace bespoke controls where appropriate. Exact history readings and alert details use bottom sheets. Preview scenarios are in overflow menus.

## Deliberate changes to previous rules

- Replaced the 12–16dp card-radius restriction with a shape hierarchy: 20dp controls, 24dp cards and a 32dp forecast hero.
- Quota forms use native outlined fields and buttons instead of the authentication-specific wrappers. Authentication keeps its existing design.
- Replaced custom bottom-navigation buttons with Material 3 NavigationBar items in a floating rounded surface.
- Added semantic mint/lime supporting tokens. Screens continue to read semantic MaterialTheme roles.

## References consulted

- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3): native components and theme roles.
- [GlassWire for Android](https://www.glasswire.com/glasswire-for-android/): usage-overview and chart hierarchy.
- [Rheobyte](https://rheobyte.jaironlanda.com/): focused usage, history and settings organization.
- [Telecom app concept](https://dribbble.com/shots/27290425-Telecom-App-UI-Design-Data-Usage-Plan-Management): visual hierarchy and data-card composition.

References informed the layout; no third-party artwork was copied into the app.

## Scope and validation

All five Home sample states remain available. Missing access does not invent a remaining balance; quota reached does not imply network blocking. Backend schemas, telemetry, ML and Android permissions are outside this change.

Validation: `gradlew.bat test assembleDebug assembleDebugAndroidTest`. UI test interactions were adjusted for pinned form actions, overflow menus and bottom sheets. Instrumentation APK compilation does not establish that device tests passed.

The temporary review emulator failed to start. Rendered screenshots and device interaction checks remain unverified; check the Compose previews and run instrumentation on a working emulator before visual sign-off.

## Composition refinement

Home now combines forecast, quota name, remaining amount and a slim progress indicator in a single focal card. Duplicate History and Notifications shortcut buttons were removed; bottom navigation and the bell remain. The seven-day sparkline uses the existing independent History fixture, with dates, a total, explicit sample labeling and an accessible reading for every point. It is hidden while collecting or missing access, so it cannot imply that missing telemetry exists.

Screen headings now describe their function instead of using slogans. Secondary screens use quieter section titles, and Create Quota uses a smaller heading. Each Home state has specific copy and action: create, wait for readings, review a forecast, restore access, or begin another quota. Collecting does not show a fabricated measured balance.

Progress indicators animate briefly when values change; hero layout changes and profile save confirmation use finite Compose animations. Compose animation timing follows the system duration scale. There are no looping decorative animations. Native controls retain ripple/pressed feedback, and profile save confirmation announces a polite accessibility update. History retains Vico's chart transitions and exact-readings sheet.

Emulator retry reached Android startup but ADB remained unauthorized. Device UI tests and screenshots still need a working authorized emulator.

## Review on emulator-5554

The user's running API 37 emulator was booted and authorized. The original Espresso 3.5.1 failed before UI assertions because it reflected on a removed InputManager method. Updated test-only catalog entries to Espresso 3.7.0 and AndroidX JUnit 1.3.0; the [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0) document the InputManager fix.

Captured and visually inspected Home's no-quota, forecast, missing-access and quota-reached states on the emulator. Screenshots are local build artifacts under `build/ui-review/screenshots/`. This supersedes the earlier lack of any rendered review; other screen sizes and large-font rendering still require further visual review. A History assertion's incorrectly encoded middle dot was corrected using a Kotlin Unicode escape.

Final result: all 9 Compose UI tests passed on emulator-5554 (API 37), covering Home states, quota creation and cycle confirmation, History filters/read-only details, Notifications, Settings and Profile. The quota form test now dismisses the keyboard before tapping the pinned action to avoid racing IME layout changes. Unit tests and both debug APK builds also passed.

## Stitch Direction C adaptation

Reference project: [DATAra Mobile App UI/UX Design](https://stitch.withgoogle.com/projects/4283214377641370983). Screens and HTML are stored in the ignored `screen-reference/stitch/` directory. This section supersedes earlier descriptions of the navy hero and lime accents.

Adapted white forecast cards, soft blue information panels, teal progress, compact metric grouping and rounded usage bars. Navy remains the primary text/action color. The scoped quota shape scale is now 16dp controls, 20dp cards and a 24dp hero. Floating navigation has a quieter shadow and mint selected states. History summaries and Settings profile cards now use white surfaces; shared tokens carry the visual direction through Notifications, Details and Profile. Create Quota retains native labeled fields, adds an amount-unit suffix and groups guidance beneath a clear heading.

The Home hero uses the existing sample state; its two metric panels distinguish remaining quota from usage across the cycle. Collecting and missing-access states do not manufacture a balance. Its chart retains the existing independent History fixture, with a 4GB total, accessible per-date values, a labeled latest reading and explicit sample disclosure. Earlier bars use a darker tint than the Stitch reference so they remain distinguishable from white. Finite progress and layout transitions remain.

Excluded reference features: promo allocations, app-category traffic, SIM throttling, new filters and exports, model-accuracy metrics, live network badges, last-updated claims, cloud/privacy guarantees, and invented pace recommendations. No new data collection, permissions, backend or ML changes. Kept the existing offline-safe typography instead of importing remote fonts. Source screenshot defects and inconsistent example metrics were not reproduced.

Implementation guidance: UI UX Pro Max accessibility/chart recommendations and [Material 3 color roles and components](https://developer.android.com/develop/ui/compose/designsystems/material3). Screens use semantic theme roles, native components, minimum touch targets and a stacked metric layout at enlarged font sizes.

Validation: 41 unit tests, debug app and instrumentation APK builds passed. All 10 Compose UI tests passed on emulator-5554, including a new 200% font-scale check that keeps quota details reachable. Reviewed screenshots of Home, Create Quota, History, Notifications, Settings and Profile, with the 200% Home view confirming stacked metrics. Artifacts are in `build/ui-review/direction-c/`. These are emulator checks, not a claim of testing every device size or production telemetry.
