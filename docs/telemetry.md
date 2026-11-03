# Usage telemetry (ROADMAP D4)

DATAra measures device-wide mobile data on data-harvester's schedule (ROADMAP Decision 1). Recorded intervals therefore mean the same thing in the app as in the training data. This page covers what is built, what waits on D2/D3, and the contract the Room repository must keep.

## What runs

`telemetry/TelemetryService` is a foreground service of type `specialUse`. That type has no runtime cap and may restart from `BOOT_COMPLETED`; `dataSync` is capped at 6 h/day on Android 15+.

| Loop | Source | When | Code |
|---|---|---|---|
| Primary | `TrafficStats` mobile RX/TX, plus network type, signal dBm and carrier | every 30 s, and on every Wi-Fi ↔ mobile switch | `PrimaryTick`, `TelemetryCollector.collectPrimary` |
| Durable | `NetworkStatsManager.querySummaryForDevice(TYPE_MOBILE)`, cumulative since UTC midnight | every 10 min (first read 30 s after start), and on a switch | `DurableTick`, `TelemetryCollector.collectDurable` |
| Watchdog | WorkManager, 15 min | restarts the service if the user left tracking on; takes no measurements | `TelemetryWatchdogWorker` |
| Boot | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` | same restart rule | `TelemetryBootReceiver` |

The user starts tracking on the **Tracking setup** screen (`ui/tracking`). They reach it from Home's "About Usage Access" button and from Settings → Tracking status. Signing out stops tracking.

**Resuming:** opening the app resumes tracking the user left on (`MainActivity.onStart`), so the "Open DATAra to resume" notification is true. If the service stops unexpectedly (not a timeout), tracking stays off until the user starts it again, as in the harvester.

**Mobile data coming up:** the service also takes a sample the moment a cellular data network comes up (see the deviation below).

## Measurement rules

`MeasurementRules` and `PrimaryTick` mirror data-harvester's files of the same names (schema version 3, harvester v1.6.7 rules). `MeasurementRulesTest` and `PrimaryTickTest` repeat the harvester's cases. If a rule changes, change it in both repositories together.

- **Status values:** `OK`, `BASELINE`, `COUNTER_RESET`, `COUNTER_JUMP`, `UNSUPPORTED`, `GAP`, `INTERRUPTED`, `CLOCK_ERROR`.
- **Gaps:** a 30 s interval over 75 s, or a 10 min interval over 21 min, is a `GAP`. It keeps its real bytes.
- **Reboots:** detected when boot time (wall clock minus `elapsedRealtime`) moves by more than 60 s. The first reading after a reboot re-baselines with 0 bytes instead of recording a negative delta.
- **Mobile interface down:** counters fall when mobile data drops. The baseline is held for up to 10 minutes, so only real usage counts when the interface returns.
- **Implausible jumps:** a delta above 100 MiB/s is recorded as `COUNTER_JUMP` with 0 bytes.
- **Network switches:** an interval closed by a switch is labelled with the network it ran on. An interval that moved any mobile bytes always gets a cellular label.
- **Network type:** `5G` (NR standalone), `LTE`, `3G`, `2G` or `CELLULAR`. Without `READ_PHONE_STATE` the type is `CELLULAR`, as in the harvester.
- **Signal:** dBm of the first reported cell, `-999` when unknown. It comes from `TelephonyCallback` on API 31+ and `PhoneStateListener` below. Android 8–9 only reports GSM ASU, so LTE dBm is unknown there.

Differences from the harvester:
- Durable totals are device-wide, not per app.
- Zero-usage durable spans are kept.
- `CellInfo.UNAVAILABLE` is stored as `-999`.
- Battery, screen and charging state are not collected: they are not model inputs.
- **Interface-returned rule (behaviour change):** each checkpoint records whether a cellular data network was up at its reading. If the baseline was read while mobile data was down and the next reading finds it up, that interval is `COUNTER_RESET` with 0 bytes and re-baselines.
  - **Why:** phones that tear mobile data down on Wi-Fi (MIUI's default, `mobile_data_always_on = 0`) report low mobile counters while it is down. When it returns they report the interface's lifetime total.
  - **What 1.6.7 did with it:** on a POCO F3, harvester 1.6.7 counted **1.2 GB in 29 s** after Wi-Fi → mobile, while NetworkStatsManager measured 23.5 MB. At ~41 MB/s it was under the jump ceiling, and the drop hold never saw a drop.
  - **With this rule:** the same test summed to 13,853,039 B of 30 s samples against 13,853,374 B durable.
  - **Data-harvester:** the training data likely contains these spikes. Adopt the rule there (and bump its schema version) or filter them in B1.

## Permissions

These are three separate mechanisms. Each manifest entry has a comment saying why it is there.

| Grant | Mechanism | Needed to start? |
|---|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Special access in Android Settings, checked with `AppOpsManager` | Yes |
| `POST_NOTIFICATIONS` (Android 13+) | Runtime dialog | Yes |
| `READ_PHONE_STATE` | Runtime dialog | No; without it the network type is `CELLULAR` |
| Battery-optimization exemption | Settings dialog | No; without it Android 12+ may refuse watchdog restarts, and the user gets a "tap to resume" notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` | Install time | Automatic |

Some vendor builds (MIUI and others) also need Autostart enabled before background restarts work. Without it, MIUI refused the `MY_PACKAGE_REPLACED` restart on the test phone.

## Storage seam (for D2)

Samples go through `data/telemetry/TelemetryRepository`. Hilt binds it to `InMemoryTelemetryRepository` in `di/TelemetryModule`.

- **What the in-memory version loses:** everything, checkpoints included, when the process dies. The next sample is then a `BASELINE`.
- **What it keeps:** only the most recent day.

For D2, replace the binding with a Room-backed implementation that keeps these rules:
- `commitPrimary` and `commitDurable` store the rows **and** their checkpoint in one transaction. That way a crash can never count bytes twice (see `crash before commit` in `PrimaryTickTest`).
- Row ids are client-generated UUIDs (`UsageSample.id`, `DurableUsageReading.id`), so sync retries upsert.
- Times are epoch millis. Statuses are the strings above.

`TelemetryCollectorTest` runs against the repository interface; point it at the Room implementation too.

## Not done yet

- Durable persistence and cloud sync (D2, C3).
- Remaining quota against the active cycle baseline (needs D3 durable cycles).
- Reconciling `TrafficStats` sums against the durable totals.
- Behaviour under Doze on a real device.
- Vendor battery managers: MIUI blocks the boot/update receiver and background restarts while DATAra's Autostart is off (`process is not permitted to auto start`). Opening the app resumes tracking.
- Home and History still show sample data; D5 and D7 connect them.
