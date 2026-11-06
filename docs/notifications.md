# Quota alerts (D8)

DATAra tells the user when measured mobile usage nears or reaches their quota, and when the on-device forecast says it will be reached soon. Alerts are informational: a quota is a tracking amount the user chose, so reaching it never blocks or throttles data.

Code: `app/src/main/java/com/capstone/datara/alerts/`, the center in `ui/notifications/`, the switches in `ui/settings/`.

## The alerts

Decided 2026-11-04 ([quota schema ERD](erd-draft.md)). Each type goes out **at most once per cycle**.

| Type | When | Switch |
|---|---|---|
| `milestone_75` | measured usage ≥ 75% of the quota | Quota milestones |
| `milestone_90` | ≥ 90% | Quota milestones |
| `quota_reached` | ≥ 100%, or the cycle is marked reached (D3) | Quota milestones |
| `forecast_24h` | the PET is within 24 hours | Forecast reminders |
| `forecast_3h` | the PET is within 3 hours | Forecast reminders |

Rules (`AlertRules`, pure and unit-tested):

- **Only the device's current cycle.** An ended cycle (by the user, or replaced) gets nothing more. A reached cycle gets only `quota_reached`, never a forecast alert.
- **Highest level only.** A jump from 70% to 100% (for example after the phone was off) sends "Quota reached" alone, not three alerts. A lower level is never sent after a higher one of the same kind: once the 3-hour alert went out, a PET moving back to 10 hours does not send the 24-hour one.
- **Usage** is D3's measured usage of the cycle (`CycleProgress.usedBytes`, from the 10-minute NetworkStatsManager readings), not the 30-second estimate.
- **Forecast alerts use the latest forecast only**, and only a `ForecastStatus.Ready` for this cycle that is at most 2 hours old with a PET (`reachAt`). Collecting, unavailable, pending and "beyond 30 days" (`reachAt == null`) never trigger one. Since an alert is never re-sent, one noisy hour can cause at most one early alert per level, and a PET that moves away and back sends nothing new.
- **Preferences.** With a switch off nothing of that kind is recorded. Turned back on while the condition still holds, the alert is sent then (once).

## Once per cycle, enforced in Room

`QuotaAlertEngine` writes the alert to Room `notifications` before posting it. The row id is derived from `(cycle_id, notification_type)` (a name-based UUID, so a sync retry upserts), and the table's unique `(cycle_id, notification_type)` index turns a second insert into a no-op (`OnConflictStrategy.IGNORE`). Only a newly inserted row is posted. New rows have `synced_at` null, so they are queued for upload when C3 lands.

## Cadence: no extra wake-ups

The engine is a singleton started with the process (`DataraApplication`), so it runs whenever the telemetry service keeps the process alive. It follows three flows and re-checks when any changes:

- the cycle state (`QuotaCycleManager.state`), recomputed after each 10-minute durable read;
- the forecast status (`ForecastEngine.status`), once per hour;
- the user's preferences (`notification_preferences`).

There is no alarm, worker or polling of its own.

## Delivery

- **Channels** (`QuotaAlertNotifications`): *Quota milestones* (`quota_milestones`) and *Forecast alerts* (`quota_forecasts`), default importance, created at app start so they appear in Android's settings before the first alert. The tracking channels from D4 are separate.
- **Permission.** `POST_NOTIFICATIONS` was already declared and requested for D4 (Tracking setup). No permission was added. When Android notifications are not allowed (permission denied on Android 13+ or turned off in system settings), the alert is still recorded and appears in the in-app inbox; Settings shows "Android notifications are off for DATAra" with **Allow notifications**, which asks once and then opens the app's notification settings.
- **Tap.** The notification opens `MainActivity` with the alert and cycle ids (`AlertNavigation`). The dashboard marks it read and opens the cycle: Quota Details for the current cycle, or read-only details for a past one.

## Wording

`AlertCopy` stores the figures at the moment of sending, so the inbox reads as a history:

- 75%: "75% of your quota used: 750 MB of 1 GB used on “Everyday”. 250 MB left. Nothing is blocked."
- 90%: "90% of your quota used" … "Only 100 MB left. Nothing is blocked."
- Reached: "Quota reached: You have used the 1 GB you set for “Everyday”. DATAra never blocks data, so your connection stays on."
- Forecast: "Quota likely reached within a day / within 3 hours: At your recent pace, “Everyday” may reach 1 GB around 12:30 PM. 900 MB left. This is an estimate."

## Notification center and Settings

- The center (bell on Home and History) reads Room `notifications` for the signed-in user, newest first: All/Unread, unread count and badges, **Mark all as read** (sets `read_at` on unread rows only). Opening an alert sets its `read_at` and offers **View quota details**.
- Settings → Notification preferences: the two switches read and write Room `notification_preferences` (one row per user; both on until changed). Changes queue the row for sync.

## Tests

- `AlertRulesTest`: each level, highest-only, never a lower level after a higher one, preferences, collecting / unavailable / pending / beyond 30 days, another cycle's or a stale forecast, ended and reached cycles.
- `QuotaAlertEngineTest`: once per cycle across repeated checks, usage climbing through the levels, a new cycle, preferences off and back on, forecast moving away and back, the flows driving `start()`, the exact wording, inbox mapping.
- `NotificationsScreenTest` and `SettingsScreenTest` (Compose, instrumented): opening an alert's cycle read-only, mark all read, the empty inbox, the switches.

## Not covered

- Upload of `notifications` and `notification_preferences` (C3).
- Alerts from another device of the same user appear only after sync (C3).
- If Android kills the process and the service, checks resume when telemetry restarts; an alert due meanwhile goes out late, not twice.
