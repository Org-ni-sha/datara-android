# Signed-in UI design and review

## Current direction (2026-10-29)

User-defined usage-quota forecasting is the approved product direction. The current implementation remains a sample UI; real telemetry, persistence, inference and alert delivery are pending. This document describes the final Direction C adaptation and supersedes earlier navy-hero, lime-accent, circular-gauge and larger-radius designs.

Home, Create Quota, Quota Details, History, Notifications, Settings and Profile use the scoped light QuotaTheme: off-white backgrounds, white surfaces and forecast hero, navy text/actions, accessible teal progress, soft blue information panels and mint selected navigation. Authentication and launch retain dark navy. There is no theme toggle. Typography remains offline-safe SansSerif through MaterialTheme styles.

Use semantic theme roles and native Material 3 app bars, navigation items, outlined fields, segmented controls, cards, lists, buttons and bottom sheets. Shapes are 16dp controls/inset panels, 20dp cards and a 24dp hero; small icon/label containers use 8–14dp. Floating Home/History/Settings navigation has a quiet shadow. Profile selects Settings.

## Composition and behavior

- Home combines the prominent forecast, quota name, metrics and slim progress indicator in one hero. View quota details stays within that card. Preview scenarios live in the overflow menu; the bell and bottom navigation provide secondary destinations.
- No-quota, collecting, forecast, missing-access, reached and ended presentations have appropriate copy/actions. Collecting and missing-access states do not invent measured balances. Reaching a quota never implies network blocking.
- Seven-day rounded usage bars use an independent History fixture totaling 4 GB, with dates, a latest reading, accessible values and sample disclosure. They do not claim to reflect actions on the current Home cycle and are hidden when collecting or missing access.
- Create Quota uses labeled amount/unit fields and grouped guidance. History exact readings and notification details use sheets. Enlarged-font Home metrics stack vertically, and controls retain minimum touch targets.
- Progress/layout changes and profile save feedback use finite Compose animations that follow system duration scale. Profile feedback has a polite accessibility announcement. There are no looping decorative animations; native pressed feedback and chart transitions remain.

The user explicitly permitted revising older Markdown styling constraints. The current shape hierarchy, native Material 3 wrappers and light signed-in palette replace those older restrictions. Official supplied branding is covered in [brand assets](brand-assets.md).

## Inspiration and boundaries

The primary reference was [DATAra’s Stitch project](https://stitch.withgoogle.com/projects/4283214377641370983), Direction C. Screens/HTML are local ignored references under `screen-reference/stitch/`. White cards, soft blue grouping, teal indicators and compact metrics were adapted to DATAra’s existing behavior; reference metrics and features were not copied blindly.

Excluded features include promo allocations, app-category traffic, SIM throttling, new exports, public model-accuracy metrics, live network badges, unsupported sync/privacy guarantees and invented pace recommendations. Signal and speed remain planned model inputs, not implemented live badges. No permissions, schema or ML work was part of the redesign.

Other references consulted during implementation:

- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3): components and semantic theme roles.
- [GlassWire for Android](https://www.glasswire.com/glasswire-for-android/): usage overview/chart hierarchy.
- [Rheobyte](https://rheobyte.jaironlanda.com/): usage/history/settings organization.
- [Telecom concept](https://dribbble.com/shots/27290425-Telecom-App-UI-Design-Data-Usage-Plan-Management): visual grouping.

UI UX Pro Max informed accessibility, chart readability and interaction decisions. No third-party reference artwork was copied into the app; the logo is user-supplied.

## Historical verification and remaining checks

The following records prior implementation runs, summarized on 2026-10-29. This documentation edit does not rerun or newly certify them.

- 41 unit tests passed; debug application and instrumentation APK builds passed.
- Direction C review: 10 Compose UI tests passed on emulator-5554 (API 37), including 200% font-scale Home. Home, Create Quota, History, Notifications, Settings and Profile screenshots were inspected in `build/ui-review/direction-c/`.
- Subsequent branding review: 11 Compose UI tests passed on the same emulator, adding session loading at 200% font scale. Native launch, loading, Login and branded Home were inspected in `build/ui-review/branding/`. Cold launch reached Login; a later launch reused the running activity.
- Earlier emulator startup/ADB failures were superseded by the successful emulator-5554 review. Espresso was updated to 3.7.0 with AndroidX JUnit 1.3.0 to address API 37 InputManager compatibility; see the [release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

Screenshots are ignored local build artifacts, not committed evidence visible to GitHub reviewers. Attach relevant screenshots to UI PRs before visual approval. API 26 launch behavior, themed launcher appearance, additional device sizes and physical-device checks remain outstanding. Sample-state tests do not verify telemetry, inference, persistence or real OS notification delivery.
