# DATAra UI Screen Implementation Guide

> Current implementation record for the DATAra frontend, including the Prompt Ultra sequence 1-4 work.
>
> Last documented: October 15, 2026

## 1. Purpose of this document

This document explains how the current DATAra application screens were designed and implemented, why the code is structured the way it is, which roadmap requirements the UI represents, and what remains intentionally unimplemented.

The screens covered here are:

- Dashboard
- History
- Settings
- Usage Access onboarding and review
- Notification permission introduction and review
- Promo Setup
- Notification Center
- Profile Management
- Privacy and Terms
- Hidden Developer Diagnostics
- Authentication and session-restoration accessibility improvements
- The shared floating bottom navigation
- The navigation and theme changes needed to support those screens

This is a description of the **current worktree**, not a claim that the corresponding roadmap phases are functionally complete. The screens are currently frontend presentation layers. Room, device telemetry, promo persistence, notification delivery, cloud synchronization, and ONNX prediction are not connected.

## Prompt Ultra implementation update

The recommended frontend sequence items 1-4 are now represented in the worktree.

### First-run and promo setup

- Successful registration enters Usage Access, Notification introduction, and Promo Setup in sequence.
- Usage Access explains Android's special access model, what DATAra intends to measure, privacy limits, current status, skip behavior, and the frontend-only recovery state.
- The notification introduction explains PET warnings, 50/80/90 percent milestones, promo reminders, and the future visible tracking notification without requesting Android permission.
- Promo Setup provides honest carrier-unavailable/loading/error/ready states, a carrier-filtered Smart/Globe/DITO sample catalog, search, category chips, selection, baseline correction, and validated manual promo details. Carrier entry is never manual.
- A completed promo can update primitive in-memory navigation state. Dashboard clearly marks it as a preview that is not permanently saved.

### Supporting screens

- Notification Center includes empty/loading/error/ready states, unread count, local mark-read actions, priority labels, and preview examples for PET, 50/80/90 percent milestones, and promo expiry.
- Profile supports view/edit/cancel/local-save states. Email is read-only, the phone is optional, validation is visible, and persistence is explicitly unavailable.
- Privacy and Terms use readable DATAra-specific sections and explicitly distinguish planned local processing and future synchronization from active systems.
- Developer Diagnostics has complete runtime/metric/tensor fields. Runtime values are unavailable; sample values only appear in previews. The route is accessible only after five taps on the Settings app-version row.

### Navigation and responsive shell

- Dashboard, History, and Settings share one adaptive `Scaffold`: a Material 3 navigation bar on compact widths and a navigation rail on expanded widths.
- Top-level navigation uses single-top navigation, state save/restore, and Dashboard as the authenticated root to limit duplicate destinations.
- The Dashboard bell opens Notification Center; Add Promo opens Promo Setup; all Settings rows now open their intended frontend destinations.
- Sign-out confirmation is shared between Dashboard and Settings, and sign-out clears authenticated destinations from the back stack.

### Modernization, accessibility, and localization

- Material 3 buttons, icon buttons, navigation items, switches, filter chips, and dialogs provide standard feedback and touch behavior.
- Navigation items expose tab roles, Settings rows expose switch roles, and balance/history indicators expose progress semantics.
- Important errors and success messages use polite live regions. Non-decorative controls have descriptions, and chart data has a textual summary.
- Content width is constrained on larger displays, metric groups adapt between one and two columns, and authentication inputs/OTP cells avoid narrow-screen fixed-width overflow.
- Authentication fields retain form values across configuration changes, provide autofill content types, use 48dp controls, include the required name in registration eligibility, and use “Forgot password?”.
- Navigable-screen UI text is centralized in `strings.xml`.

### Deliberately unavailable

The implementation does **not** add Room, repositories, telemetry, SIM/carrier APIs, real usage access, notification permission requests, notification delivery, WorkManager, foreground services, cloud synchronization, ONNX inference, or permanent profile/promo/settings storage. Runtime routes use empty or unavailable state instead of presenting sample data as real.

## 2. Decisions made during the conversation

The following decisions guided the work:

1. The files in `design_references/` were used only for visual inspiration. The screens were not copied directly from the references.
2. The UI was expanded to cover the functions described by `ROADMAP.md`, while staying within the existing DATAra dark visual identity.
3. Dashboard, History, and Settings were built as frontend screens before their database and device integrations were available.
4. Runtime screens must show honest empty or unavailable states instead of pretending that sample data is real.
5. Rich sample data is kept in Compose previews so the completed design can still be inspected.
6. Existing authentication, session restoration, and sign-out behavior were preserved.
7. DATAra remains dark-only. No theme switch was added.
8. Testing and rebuilding were kept light because the development computer is resource-limited.

## 3. Roadmap relationship

The screens visually prepare the following Android roadmap phases:

| Phase | Requirement represented by the UI | Current status |
|---|---|---|
| D3 | Active promo and estimated balance setup | Frontend Promo Setup and in-memory preview handoff implemented; persistence is not connected |
| D4 | Usage access, carrier detection, telemetry status, and network readings | Onboarding/review UI and honest unavailable states implemented; device functionality is not connected |
| D5 | Dashboard, PET hero, data balance, live network strip, promo card, and adaptive navigation | Presentation layer implemented |
| D6 | Global/local PET result and prediction status | UI states exist; inference is not implemented |
| D7 | Usage chart, daily average, peak period, breakdown, and time filters | Presentation layer and Vico chart implemented; data source is not connected |
| D8 | Warning preferences and notification center | Notification Center and local preference interactions implemented; delivery and persistence are not connected |
| D9 | Profile entry, settings, sign-out, and hidden diagnostics | Supporting screens and hidden unavailable diagnostics implemented; persistence/runtime systems are not connected |

The roadmap checkboxes should remain unchecked until their data, behavior, persistence, and required supporting screens are complete.

## 4. Files created and changed

### New screen files

| File | Purpose |
|---|---|
| [`DashboardScreen.kt`](app/src/main/java/com/capstone/datara/ui/dashboard/DashboardScreen.kt) | Replaces the old authentication-era Dashboard placeholder with the D5 presentation |
| [`HistoryScreen.kt`](app/src/main/java/com/capstone/datara/ui/history/HistoryScreen.kt) | Implements the D7 analytics presentation and Vico chart |
| [`SettingsScreen.kt`](app/src/main/java/com/capstone/datara/ui/settings/SettingsScreen.kt) | Implements D9 Settings, supporting-route entry points, and the five-tap diagnostics unlock |
| [`DataraBottomNavigation.kt`](app/src/main/java/com/capstone/datara/ui/navigation/DataraBottomNavigation.kt) | Shared Home, History, and Settings floating navigation |
| [`SetupIntroScreens.kt`](app/src/main/java/com/capstone/datara/ui/onboarding/SetupIntroScreens.kt) | Usage Access and notification onboarding/review presentations |
| [`PromoSetupScreen.kt`](app/src/main/java/com/capstone/datara/ui/promos/PromoSetupScreen.kt) | Carrier-aware sample catalog, selection, correction, and manual promo form |
| [`NotificationCenterScreen.kt`](app/src/main/java/com/capstone/datara/ui/notifications/NotificationCenterScreen.kt) | Local-only alert history states and read interactions |
| [`ProfileScreen.kt`](app/src/main/java/com/capstone/datara/ui/profile/ProfileScreen.kt) | Local-only profile view/edit flow |
| [`LegalScreens.kt`](app/src/main/java/com/capstone/datara/ui/legal/LegalScreens.kt) | Privacy and Terms information screens |
| [`DeveloperDiagnosticsScreen.kt`](app/src/main/java/com/capstone/datara/ui/diagnostics/DeveloperDiagnosticsScreen.kt) | Hidden diagnostics presentation with unavailable runtime values |
| [`DataraCommonComponents.kt`](app/src/main/java/com/capstone/datara/ui/components/DataraCommonComponents.kt) | Shared headers, cards, notices, badges, and sign-out dialog |

### Changed supporting files

| File | Change |
|---|---|
| [`NavGraph.kt`](app/src/main/java/com/capstone/datara/ui/navigation/NavGraph.kt) | Wires authentication, onboarding, supporting screens, top-level state restoration, and in-memory promo preview state |
| [`strings.xml`](app/src/main/res/values/strings.xml) | Centralizes navigable-screen labels, status copy, validation, and accessibility text |
| [`DataraAuthComponents.kt`](app/src/main/java/com/capstone/datara/ui/auth/components/DataraAuthComponents.kt) | Adds autofill semantics, accessible password controls, and Material button feedback |
| [`Color.kt`](app/src/main/java/com/capstone/datara/ui/theme/Color.kt) | Added elevated surfaces, soft status colors, warning color, and divider token |
| [`libs.versions.toml`](gradle/libs.versions.toml) | Added Vico version and library aliases |
| [`app/build.gradle.kts`](app/build.gradle.kts) | Added the Vico Compose and Material 3 modules |
| [`.gitignore`](.gitignore) | Added `design_references/` so local design material is not committed accidentally |

The old `DashboardPlaceholderScreen.kt` was removed because its only purpose was to verify authentication and auto-login during D1.

## 5. Shared screen architecture

The new screens follow a state-driven Compose pattern:

```text
NavGraph route
    |
    v
Route-level screen composable
    |-- obtains only currently available runtime dependencies
    |-- supplies UI state
    |-- supplies navigation/event callbacks
    v
Content composable
    |-- renders the state
    |-- contains no repository or database calls
    v
Small section composables

Compose Preview
    |-- supplies sample UI state directly
    v
The same content composable
```

This separation matters for two reasons:

- The visual content can be previewed without Hilt, authentication, a database, or a device service.
- A future ViewModel can replace the current empty state without forcing a redesign of the cards and sections.

### State hoisting

Navigation and actions are represented as callbacks such as `onHistoryClick`, `onSettingsClick`, `onAddPromoClick`, and `onSignedOut`. The screen does not create a `NavController` internally. `NavGraph.kt` decides what each navigation callback does.

### Honest runtime state

The runtime routes do not use the rich preview samples:

- Dashboard receives `DashboardUiState()`.
- History receives `HistoryUiState.Empty`.
- Settings receives the real signed-in email and app version, but other statuses remain explicit placeholders.

This prevents sample promo, balance, prediction, signal, or history values from being shown as if they came from the user's phone.

## 6. Dashboard implementation

### 6.1 Purpose

The Dashboard is the app's main destination and the visual implementation of roadmap D5. PET, or Probable Exhaustion Time, is intentionally the strongest element because prediction is DATAra's core purpose.

### 6.2 UI state models

`DashboardScreen.kt` defines presentation models rather than using database or domain models directly.

#### `PetUiState`

`PetUiState` is a sealed interface covering every currently designed prediction state:

| State | Meaning | Presentation |
|---|---|---|
| `NoPromo` | A prediction cannot be produced without an active promo | Setup explanation and Add active promo action |
| `Collecting` | Usage history is still being collected for personalization | Day progress and learning status |
| `Available` | A PET result can be displayed | Large remaining-time value, exhaustion timestamp, and model label |
| `Loading` | A prediction is being calculated | Updating copy without a fabricated value |
| `Error` | Prediction failed or is unavailable | Error explanation supplied by the state |

The `Available` state holds:

- `timeRemaining`
- `exhaustionTime`
- `modelLabel`, intended for Global or Local model labeling

The composable does not decide which model should be active. That future decision belongs in prediction/domain logic.

#### `DataBalanceUiModel`

This model contains:

- Remaining data in GB
- Total promo allowance in GB
- Consumed data in GB
- Whether the balance is estimated

The `isEstimated` flag allows the UI to disclose that a device-calculated value is not the carrier's official balance.

#### `NetworkUiModel`

This model contains:

- Carrier label
- Human-readable signal label
- Signal level from 0 to 4
- Current speed text
- Whether mobile data is active

#### `ActivePromoUiModel`

This model contains:

- Promo name
- Carrier name
- Human-readable time remaining
- Expiry date

#### `DashboardUiState`

This is the complete screen state. It combines the PET state, balance, network status, active promo, and last-updated text.

### 6.3 Route-level behavior

`DashboardScreen` obtains `AuthViewModel` through Hilt. It currently uses the ViewModel for only two real behaviors:

1. It reads the authenticated email and converts the part before `@` into a display-friendly greeting.
2. It calls the existing authentication sign-out operation.

It then calls `DashboardContent` with an empty `DashboardUiState`. There is no `DashboardViewModel` yet.

### 6.4 Component hierarchy

```text
DashboardScreen
`-- DashboardContent
    |-- DashboardHeader
    |   |-- DATAra label and greeting
    |   |-- Notification action
    |   `-- Profile avatar and sign-out menu
    |-- PetHeroCard
    |-- DataBalanceCard
    |-- LiveNetworkSection
    |   |-- Carrier and signal card
    |   `-- Live speed card
    |-- ActivePromoCard
    |-- UsageInsightCard, only for an available PET
    `-- DataraBottomNavigation
```

`DashboardContent` uses a full-screen `Box`. A `LazyColumn` supplies scrolling content, while the floating bottom navigation is aligned over the bottom of the box. The list reserves `124.dp` at its bottom to keep its last item visible above the navigation.

### 6.5 Header

The header includes:

- DATAra branding
- A greeting derived from the email prefix
- A subtitle describing the data outlook
- Notification button
- Circular profile initial
- Dropdown menu containing Sign out

The profile menu uses local `remember` state only. The Dashboard sign-out currently occurs immediately after the menu action is selected.

### 6.6 PET hero

`PetHeroCard` is visually stronger than the supporting cards through:

- A deeper blue gradient
- A blue outline
- `24.dp` rounded corners
- Large PET typography
- A text-and-color status badge

The badge is derived from `PetUiState`:

- Available: positive green and the supplied model label
- Collecting: warning color and Learning
- Loading: warning color and Updating
- Error: red and Unavailable
- No promo: soft blue and Setup needed

The UI includes an on-device estimate label when a last-updated value exists. This prepares the screen for the roadmap's offline inference requirement without claiming that inference currently runs.

### 6.7 Data balance

With no balance, the card presents a setup state and delegates to `onAddPromoClick`.

With a balance, it calculates:

```text
consumed fraction = consumed GB / total GB
```

The fraction is clamped between 0 and 1. Its progress accent changes at the following thresholds:

- Below 75% consumed: green
- At least 75% consumed: warning amber
- At least 90% consumed: red

The card also shows remaining, used, and total values. Values are formatted with one decimal place and a GB suffix.

### 6.8 Live network

The live network section contains two equal-width cards:

1. Carrier and signal
2. Live speed

Custom Canvas graphics draw the signal bars and speed gauge. When data is unavailable, the cards say Detecting, Waiting for mobile signal, No reading yet, or display a dash. No device values are invented.

### 6.9 Active promo

The promo card supports two states:

- No active promo, with an Add action
- Active promo, with carrier initials, plan name, expiry date, and remaining validity

All Dashboard promo actions use the same `onAddPromoClick` boundary so one future Promo Setup route can serve the entire screen.

### 6.10 Usage insight

The insight card appears only when PET is available. It is currently a static visual placeholder that says usage is steady. It must later receive a structured insight model; it should not remain hardcoded when real telemetry is introduced.

### 6.11 Dashboard previews

Two previews are provided:

- `DashboardReadyPreview` uses a private sample state containing a Globe promo, balance, network speed, and PET result.
- `DashboardEmptyPreview` uses the same default empty state as runtime.

The ready sample is never passed to the installed app.

### 6.12 Dashboard interactions today

| Action | Current behavior |
|---|---|
| History bottom item | Navigates to History |
| Settings bottom item | Navigates to Settings |
| Profile avatar | Opens local dropdown |
| Sign out | Calls real `AuthViewModel.signOut` and clears authenticated navigation |
| Add active promo / Set up / Add | Callback exists, but `NavGraph` does not yet supply a destination |
| Notification bell | Callback exists, but `NavGraph` does not yet supply a destination |

## 7. History implementation

### 7.1 Purpose

The History screen implements the visual portion of roadmap D7:

- Hourly and daily time-series presentation
- 24-hour, 7-day, and promo-cycle ranges
- Total consumption
- Daily average
- Peak usage period
- Trend summary
- Detailed usage breakdown

### 7.2 UI models

#### `HistoryRange`

The three supported display ranges are:

- `DAY`: 24h
- `WEEK`: 7 days
- `PROMO`: Promo cycle

#### `UsagePointUiModel`

A chart point contains a display label and a value in MB.

#### `UsageBreakdownUiModel`

A breakdown entry contains:

- Period label
- Supporting description
- Consumed MB
- Normalized share used to draw its bar

#### `HistorySummaryUiModel`

The ready-state summary contains:

- Total MB
- Average per day in MB
- Peak period
- Highest-use day
- Trend label
- Chart points
- Breakdown entries
- Last-updated label

#### `HistoryUiState`

The screen supports:

- `Empty`
- `Loading`
- `Ready`
- `Error`

These states allow Room-backed data to be connected later without changing the main screen composition.

### 7.3 Route-level behavior

`HistoryScreen` is deliberately presentation-only. It does not request a ViewModel or repository and always supplies `HistoryUiState.Empty` at runtime.

The selected visual range is currently local `remember` state inside `HistoryContent`.

### 7.4 Component hierarchy

```text
HistoryScreen
`-- HistoryContent
    |-- HistoryHeader
    |-- RangeSelector
    |-- EmptyHistoryCard, LoadingHistoryCard, or ErrorHistoryCard
    |-- Ready content
    |   |-- UsageChartCard
    |   |   `-- UsageLineChart
    |   |-- SummaryMetrics
    |   |-- TrendInsightCard
    |   |-- BreakdownHeader
    |   |-- BreakdownRow list
    |   `-- EstimatedDataNotice
    `-- DataraBottomNavigation
```

Like Dashboard, it uses an overlaid floating navigation bar and `124.dp` of list clearance.

### 7.5 Header and range selector

The header uses the phrase "Your data rhythm" to make the analytics page understandable without sounding overly technical. Its status shows On-device for a ready state and Waiting otherwise.

The range selector is a custom three-segment control. Selecting a range updates its visual state and the relevant headings.

At present, selecting a range does **not** query or aggregate different records. Every ready range would still receive the same summary object. This is an intentional frontend boundary, not finished filtering behavior.

### 7.6 Empty, loading, and error states

- Empty explains that history will appear after tracking starts.
- Loading explains that local records are being prepared.
- Error displays a supplied message and a Retry action.

Runtime currently uses Empty. Retry currently has an empty callback.

### 7.7 Vico chart

The chart uses Vico 3.3.1, added through the Gradle version catalog as required by repository conventions.

`UsageLineChart` creates a remembered `CartesianChartModelProducer`. A `LaunchedEffect(points)` updates its line series whenever the supplied point list changes.

The chart includes start and bottom axes. The card separately displays the first, middle, and last point labels below the chart. The current Vico series receives Y values only, so those display labels are not yet a timestamp-aware axis formatter.

For accessibility, the chart exposes a text description generated from its points. A future large dataset will need a shorter summary rather than announcing every point.

### 7.8 Summary and breakdown

The ready state displays:

- Daily average card
- Peak usage card
- Trend insight card
- Period-by-period usage rows
- Estimated-data notice

Values under 1024 MB remain in MB. Values at or above 1024 MB are converted to GB with one decimal place.

Each breakdown row combines an exact value with a visual progress bar. The value means the information is not represented by color or bar length alone.

### 7.9 History previews

- `HistoryReadyPreview` uses seven sample points and sample summary/breakdown values.
- `HistoryEmptyPreview` shows the honest runtime state.

Preview values are not connected to runtime.

### 7.10 History interactions today

| Action | Current behavior |
|---|---|
| Select 24h / 7 days / Promo cycle | Updates only local selected-range presentation |
| Retry | Callback exists but is empty at runtime |
| Home bottom item | Pops back to Dashboard |
| Settings bottom item | Navigates to Settings |

## 8. Settings implementation

### 8.1 Purpose

Settings is a frontend shell for the account, notification, permission, synchronization, privacy, diagnostics, and sign-out controls described across roadmap D4, D8, and D9.

It intentionally labels unavailable systems instead of fabricating live statuses.

### 8.2 Two-layer structure

`SettingsScreen` is the route-level composable. It obtains:

- The signed-in email from `AuthViewModel`
- `BuildConfig.VERSION_NAME`
- The real sign-out action

`SettingsContent` renders the page and owns temporary UI-only state.

### 8.3 Local state

The following values use `rememberSaveable`:

| Value | Initial state | Purpose |
|---|---:|---|
| PET warnings | Enabled | Presentation of the future PET alert preference |
| Consumption milestones | Enabled | Presentation of future 50%, 80%, and 90% alerts |
| Promo reminders | Disabled | Presentation of a future expiry reminder |
| Sign-out dialog | Hidden | Controls confirmation visibility |
| Version tap count | 0 | Reveals diagnostics after five taps |

`rememberSaveable` can restore these values during some UI recreation, but it is not permanent preference storage. The toggles do not currently control any notification service.

### 8.4 Component hierarchy

```text
SettingsScreen
`-- SettingsContent
    |-- SettingsHeader
    |-- AccountCard
    |-- Notifications section
    |   |-- PET warning toggle
    |   |-- Consumption milestone toggle
    |   |-- Promo reminder toggle
    |   `-- System notification access action
    |-- Data and permissions section
    |   |-- Data tracking status
    |   |-- Usage access action
    |   `-- Carrier detection status
    |-- CloudSyncCard
    |-- Privacy and information section
    |   |-- Privacy action
    |   |-- Terms action
    |   `-- VersionSettingRow
    |-- DeveloperDiagnosticsCard, after five version taps
    |-- SignOutButton
    |-- Measurement disclaimer
    |-- DataraBottomNavigation
    `-- SignOutConfirmationDialog, when requested
```

### 8.5 Account card

The account card uses the first character of the authenticated email as an avatar and displays the full email. It exposes a Manage callback for a future Profile screen.

The account name and optional secondary phone number are not yet available here.

### 8.6 Notification preferences

The screen includes switches for:

- PET warnings
- Consumption milestones at 50%, 80%, and 90%
- Promo-expiry reminders

These switches currently update only local Compose state. System notification access is labeled Not connected in this design build.

### 8.7 Data and permissions

The page honestly reports:

- Data tracking: Not active
- Usage access: Review
- Carrier detection: Waiting

No permission request, special-settings intent, carrier detection, or telemetry service is implemented by this screen.

### 8.8 Cloud synchronization

The sync card reports Not connected and displays no pending-record count. It has no Sync now action yet. This avoids suggesting that cloud synchronization works before the local telemetry and backend sync layers exist.

### 8.9 Privacy and terms

The Privacy and Terms rows open dedicated content screens with working back navigation. Their copy is DATAra-specific and clearly labels the content as project/draft information rather than a production legal claim.

### 8.10 Hidden diagnostics

Tapping the version row five times opens a dedicated hidden Developer Diagnostics screen. Runtime values are unavailable for:

- Active model mode and runtime status
- Inference latency
- Last prediction time
- MAE, RMSE, and R-squared
- Input tensor shape, type, and feature order

Rich sample values exist only in a Compose preview. The route is absent from bottom navigation and normal Settings rows.

### 8.11 Sign-out

Settings opens a confirmation dialog before calling `AuthViewModel.signOut`. After completion, `NavGraph` navigates to Login and clears the authenticated back stack.

The dialog says that local history is not deleted. That statement describes intended behavior; local history itself is not implemented yet.

### 8.12 Settings preview

`SettingsPreview` supplies a sample email and version. It does not require a real session because it calls `SettingsContent` directly.

### 8.13 Settings interactions today

| Action | Current behavior |
|---|---|
| Notification switches | Change temporary local state only |
| Version row | Counts taps and opens dedicated hidden diagnostics after five |
| Sign out | Opens confirmation, then performs real auth sign-out |
| Home / History | Navigate through `NavGraph` |
| Manage Profile | Opens the local-only Profile screen |
| Usage Access | Opens the Usage Access review screen |
| System Notification Access | Opens the notification introduction/review screen |
| Privacy / Terms | Open their dedicated information screens |
| Cloud sync | Status display only; no action |

## 9. Shared floating bottom navigation

`DataraBottomNavigation.kt` defines three top-level destinations:

- Home
- History
- Settings

The bar is:

- `72.dp` high
- Positioned inside `20.dp` screen margins
- Rounded to a `24.dp` floating container
- Filled with the elevated navy surface
- Outlined with the divider color
- Split evenly between three items

Selected items use `DataraBlueSoft`; unselected items use secondary text color.

The icons are drawn with Compose `Canvas`:

- Home: roof and body outline
- History: clock outline and hands
- Settings: concentric circles and gear teeth

This kept the icon style consistent without adding another icon dependency.

### Current navigation accessibility limitation

Selection is mainly communicated through color. The items do not yet expose `Role.Tab` and explicit selected semantics. The icon description may also repeat the visible text label for screen-reader users.

## 10. Navigation graph behavior

`DataraRoute` currently defines:

```text
login
register
forgot_password
dashboard
history
settings
```

### Authentication behavior preserved

- Successful Login and Register clear authentication screens and navigate to Dashboard.
- Back from Dashboard does not return to a live Login screen.
- Sign-out navigates to Login and clears the authenticated stack.

### Top-level behavior

- Dashboard navigates to History or Settings with `launchSingleTop`.
- History and Settings return Home by popping to Dashboard.
- History and Settings navigate to each other with `launchSingleTop`.

`launchSingleTop` only prevents duplication when the requested destination is already at the top. Alternating History and Settings can still accumulate duplicate destinations.

The next frontend navigation pass should use one shared top-level helper containing the equivalent of:

```kotlin
popUpTo(DataraRoute.DASHBOARD) {
    saveState = true
}
launchSingleTop = true
restoreState = true
```

The exact `popUpTo` target should be verified against the final navigation structure.

## 11. Theme and visual language

### 11.1 Existing foundation

The screens reuse DATAra's established:

- Dark navy background
- Dark card/input surface
- Electric blue brand color
- Neon green positive accent
- White primary text
- Muted secondary text
- Material typography backed by the offline-safe platform sans-serif family

### 11.2 Tokens added for the screens

`Color.kt` gained:

| Token | Use |
|---|---|
| `DataraCardElevated` | Floating navigation and raised surfaces |
| `DataraCardHighlight` | Highlighted card option |
| `DataraBlueSoft` | Selected navigation, labels, and lower-intensity blue emphasis |
| `DataraGreenSoft` | Positive status text |
| `DataraWarning` | Caution, review, and high-consumption states |
| `DataraDivider` | Dividers, inactive bars, and subtle borders |

### 11.3 Visual hierarchy

- PET receives the largest type and most pronounced surface.
- Supporting metrics use quieter bordered cards.
- Blue marks primary information and selection.
- Green marks normal or positive state.
- Amber marks review, peak, or warning state.
- Red is reserved for errors, critical consumption, and sign-out.
- Status badges include text so meaning is not conveyed by color alone.

### 11.4 Insets and scrolling

`MainActivity` enables edge-to-edge rendering. Each top-level screen applies status-bar padding. The floating navigation applies navigation-bar padding. Long content is placed in a `LazyColumn`.

The current implementation manually overlays the bottom navigation and reserves a fixed `124.dp`. A later pass should use `Scaffold` so content insets are derived rather than repeated.

## 12. What is real and what is presentation-only

| Area | Real today | Presentation-only today |
|---|---|---|
| Authentication | Login, Register, password recovery, restored session, current email, and sign-out | None of the auth status is mocked by these screens |
| Top-level navigation | Dashboard, History, and Settings routes | Missing secondary destinations |
| Dashboard | Greeting email and sign-out | PET, promo, balance, signal, speed, insight, and notification badge |
| History | Navigation and local range selection | Usage records, filtering, chart data, metrics, trends, and Retry |
| Settings | Email, version, local toggle interaction, diagnostics unlock, sign-out | Permission state, tracking, carrier, sync, persistent preferences, and model metrics |
| Vico chart | Actual chart rendering code | Runtime data source |
| Compose previews | Actual visual rendering | All sample values are preview-only |

The UI does not currently implement:

- Room or any other local database
- Data-usage telemetry
- Usage-access verification
- Android notification permission
- Foreground service or WorkManager
- Carrier detection
- Promo persistence
- Alert scheduling or delivery
- Notification history
- Cloud synchronization
- ONNX inference
- Global/local model switching
- Real ML diagnostics

## 13. Known limitations and audit findings

### 13.1 Highest-priority functional gaps

1. Dashboard Add Promo and notification actions look enabled but have no destination.
2. Settings Profile, Usage Access, Notification Settings, Privacy, and Terms rows look enabled but have no destination.
3. History range changes are cosmetic only.
4. Settings toggles do not persist or control alerts.
5. Manual cloud sync is required by D9 but has no action.
6. The notification bell always draws an unread dot.
7. Dashboard's insight and History's trend appearance are hardcoded rather than state-driven.
8. Top-level navigation can accumulate destinations.
9. Dashboard sign-out is immediate while Settings asks for confirmation.

### 13.2 Accessibility gaps

- Some custom actions are smaller than the recommended `48.dp` touch target.
- Bottom navigation lacks tab role and selected semantics.
- Custom progress bars lack progress semantics.
- Settings toggle rows expose both a clickable parent and an independently clickable Switch.
- Headings are visual only and do not have heading semantics.
- Error and success changes are not announced with live regions.
- Chart semantics can become too verbose with many points.
- Some visible labels and Canvas descriptions may cause duplicate screen-reader announcements.

### 13.3 Adaptive-layout gaps

- The screens are optimized for a portrait phone.
- There is no maximum content width on tablets.
- There is no navigation rail on expanded widths.
- Side-by-side network and summary cards can become cramped on small widths or with large font scaling.
- The fixed navigation height and fixed list clearance may not adapt well to very large text.

### 13.4 Localization and copy gaps

- Most user-facing copy is hardcoded in Kotlin.
- Dynamic values are created through string interpolation instead of formatted resources.
- Units and range labels are not localized.
- `strings.xml` currently contains only the application name.
- Existing authentication copy includes "Forget Password" instead of "Forgot password?".

### 13.5 Theme consistency gaps

A few gradients use raw color values inside screen files. These should become semantic theme tokens so colors remain centralized.

The screen cards also use several `18.dp` to `24.dp` radii. This is the actual current visual language, even though an older `AGENTS.md` guideline describes `12.dp` to `16.dp` cards. That design rule should be reconciled intentionally rather than silently changing every screen.

## 14. Agreed next frontend sequence

Backend integration remains out of scope. The next frontend-only work should proceed in this order.

### Sequence 1: first-run and promo setup

Build design-only screens for:

- Usage Access explanation and review
- Notification Permission introduction
- Detected-carrier promo catalog
- Manual promo entry
- Estimated starting-balance correction

Suggested presentation flow:

```text
Register success
    -> Usage Access introduction
    -> Notification introduction
    -> Promo setup
    -> Dashboard
```

Returning Login and restored sessions can continue to Dashboard until real first-run persistence exists.

### Sequence 2: supporting screens

Build:

- Notification Center
- Profile Management
- Privacy and Data Collection
- Terms and Conditions
- Dedicated hidden Developer Diagnostics screen

Runtime values must remain empty or unavailable. Sample values belong in previews.

### Sequence 3: wire every visible action

Add routes and connect:

- Dashboard Add Promo -> Promo Setup
- Dashboard bell -> Notification Center
- Settings Manage Profile -> Profile
- Settings Usage Access -> Usage Access introduction
- Settings Notification Access -> Notification introduction
- Settings Privacy -> Privacy
- Settings Terms -> Terms
- Five version taps -> hidden Diagnostics

No production-visible control should silently call an empty callback. An unavailable action should be disabled or explain its status.

### Sequence 4: frontend modernization

Without connecting the backend:

- Move top-level layouts to `Scaffold`.
- Correct the top-level back stack and restore destination state.
- Use Material 3 controls where they improve semantics and feedback.
- Add `48.dp` targets, roles, selected state, headings, progress semantics, and live regions.
- Support compact and expanded widths.
- Use a navigation rail or appropriate adaptive navigation on larger displays.
- Move user-facing strings into resources.
- Add Autofill metadata and saved form state to Authentication.
- Make the notification badge, insights, trend direction, and statuses state-driven.
- Keep the dark-only brand rather than redesigning solely to follow a trend.

## 15. How to connect data later without redesigning the UI

Although backend work is outside the current frontend sequence, the screen architecture was chosen to make future integration straightforward.

### Dashboard

1. Add `DashboardViewModel`.
2. Expose `StateFlow<DashboardUiState>`.
3. Collect it lifecycle-aware in `DashboardScreen`.
4. Map promo, telemetry, and prediction domain objects into the existing UI models.
5. Add structured notification and insight state.
6. Keep model selection and calculations outside composables.

### History

1. Add `HistoryViewModel` with `StateFlow<HistoryUiState>`.
2. Move selected range into the ViewModel or a saveable state holder.
3. Make range events request the appropriate hourly, daily, or promo-cycle aggregation.
4. Calculate totals, peaks, trends, and breakdown shares outside the composable.
5. Bind timestamps to the chart axis rather than relying on list positions.

### Settings

1. Introduce `SettingsUiState` and explicit events.
2. Read persisted preferences rather than initializing switches in the composable.
3. Supply actual permission, tracking, carrier, pending-record, and sync status.
4. Expose sync progress and failure states.
5. Keep developer metrics hidden and map them from the future inference diagnostics source.

General rule: repositories and device APIs belong below the ViewModel. The content composables should continue to receive display-ready state and callbacks.

## 16. Previewing and lightweight verification

### Compose previews

The quickest way to inspect the screens without installing the app is to open:

- `DashboardScreen.kt` and select either Dashboard preview
- `HistoryScreen.kt` and select either History preview
- `SettingsScreen.kt` and select Settings preview

The ready previews are the only places where rich sample usage data should appear during this frontend-only stage.

### Lightweight compile check

From the `datara-android` directory on Windows:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Run this once when needed. Do not run `clean` or repeat heavy Gradle tasks on the resource-limited computer unless a failure requires it.

### Rebuild and install on the connected device

```powershell
adb devices
.\gradlew.bat installDebug
adb shell am start -n com.capstone.datara/.MainActivity
```

`installDebug` rebuilds the changed code and installs the new debug APK. A separate clean build is normally unnecessary.

## 17. Documentation and roadmap maintenance

After the frontend work is reviewed and merged:

- Update the old `AGENTS.md` statement that still describes `DashboardPlaceholderScreen` as the current route.
- Do not mark D5, D7, or D9 fully complete only because the screens render.
- Mark a roadmap phase complete only when its required behaviors, state sources, persistence, and secondary flows are implemented.
- Keep this guide synchronized when screen responsibilities or routes change.

## 18. Summary

The work completed during this conversation establishes a coherent DATAra frontend foundation:

- Dashboard makes PET the centerpiece and defines honest prediction, balance, network, and promo states.
- History defines the complete visual analytics structure and a working Vico chart surface without claiming that telemetry exists.
- Settings exposes the planned controls while clearly labeling unavailable systems.
- A shared floating navigation and expanded theme tokens unify the three destinations.
- State models, callback boundaries, and previews prepare the UI for future ViewModels without tying the design to unfinished data layers.

The supporting frontend routes, interaction wiring, adaptive navigation, core accessibility pass, and navigable-screen localization are now present. The next phase should connect these state boundaries to reviewed device and data-layer implementations. Database, telemetry, real permissions, notification delivery, synchronization, and ML integration remain deliberately outside this frontend scope.
