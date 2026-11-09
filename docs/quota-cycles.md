# Quota cycles (ROADMAP D3)

A cycle is one quota the user set: `tracking_cycles` in [the ERD](erd-draft.md). This page covers how usage in a cycle is measured, the status rules, and how cycles are stored.

## Code

| Piece | Where | What |
|---|---|---|
| `TrackingCycle`, `CycleStatus` | `data/cycle/TrackingCycle.kt` | The row, field for field with `tracking_cycles`. Times are epoch millis and amounts are bytes. |
| `TrackingCycleRepository` | `data/cycle/` | Storage seam. `di/CycleModule` binds `RoomTrackingCycleRepository` (D2's `TrackingCycleDao`); `InMemoryTrackingCycleRepository` is for JVM tests. |
| `UsageReadingRepository` | `data/cycle/` | The 10-minute readings a cycle is measured from: `RoomUsageReadingRepository` reads every `durable_readings` row since `start_at` (`TelemetryDao.observeReadingsSince`). |
| `CycleOwnerProvider` | `data/cycle/CycleOwnerProvider.kt` | The signed-in user id (Supabase session) and this install's `device_id` for that account, from D2's `DeviceRepository`. |
| `CycleMath` | `domain/cycle/` | Pure usage arithmetic. |
| `CycleRules` | `domain/cycle/` | Pure status transitions and the table's check constraints. |
| `QuotaCycleManager` | `domain/cycle/` | The use cases: observe progress (`StateFlow<CycleState>`), create, end, and restore the latest cycle on start. |
| `QuotaCycleViewModel`, `QuotaDetailsModel` | `ui/quota/` | Create Quota and Quota Details. |

## Measuring usage

There is no `baseline_bytes` (see the ERD). Used in cycle is the sum of the 10-minute NetworkStatsManager readings in `[start_at, end)`:
- **End of the window:** `ended_at` once the cycle has ended; otherwise open-ended, since readings never extend past now.
- **Readings that straddle `start_at` or `ended_at`:** split proportionally by time. This is the only estimate, and it is reported as `estimatedBytes`.
- **Remaining** = `quota_bytes - used`. It goes negative past the quota and is clamped at 0 only for display.
- **Percent used** = `used / quota_bytes × 100`, unclamped.
- **`reached_at`:** the end of the reading in which the running sum reached `quota_bytes` (`>=`), clamped to the window.
- **Updates:** usage only changes when a reading is stored, so Details shows "Updated …", the end of the latest reading.

How each `measurement_status` counts:

| Status | Counted | Flagged as possibly missing usage |
|---|---|---|
| `OK`, `BASELINE`, `INTERRUPTED` | bytes as recorded | no |
| `GAP` | bytes as recorded (durable totals are cumulative, so a long span is still exact) | no |
| `COUNTER_RESET`, `COUNTER_JUMP`, `UNSUPPORTED` | 0 bytes, as recorded | yes (`lostReadings`) |
| `CLOCK_ERROR`, or `until < since` | skipped: it cannot be placed in the window | yes (`lostReadings`) |

Time inside the cycle that no reading covers is reported as `unmeasuredMillis`. That happens when tracking was off before the first reading of the cycle, or when no durable checkpoint bridged a stop. Details then says usage may be missing and offers **Check tracking**. Since D2 persists the checkpoint, a restart or a stop is normally covered by the next read as one `GAP` span, with exact bytes.

## Status rules

```
active  --usage reaches quota-->  reached   (reached_at set)
active  --user ends----------->   ended     (ended_at = now)
reached --user ends----------->   reached   (status kept, ended_at = now)
```

- **Final states:** `ended` never changes again, and `reached` never returns to `active`.
- **`ended_at`:** never before `start_at`, even if the clock went back.
- **Recording a reached quota:** `QuotaCycleManager` records it in the app process, not in a screen. It is injected into `DataraApplication`, so it also runs when only the telemetry service is alive. When the user ends a cycle, a crossing that was not recorded yet is recorded first.
- **Creating a cycle:** this ends the current one at the same instant (an active cycle becomes `ended`; a reached one keeps `reached` and gets `ended_at`). A device therefore never has two active cycles, matching the partial unique index.
- **Writes:** all writes go through one mutex.

## Storage

The interfaces follow DAO conventions: streams the UI follows are `Flow`, and one-shot reads and writes are `suspend`.

```kotlin
interface TrackingCycleRepository {
    fun observeLatest(userId: String, deviceId: String): Flow<TrackingCycle?> // TrackingCycleDao.observeLatest
    suspend fun latest(userId: String, deviceId: String): TrackingCycle?
    fun observeAll(userId: String): Flow<List<TrackingCycle>>                 // newest first (History, D7)
    suspend fun insert(cycle: TrackingCycle, endingPrevious: TrackingCycle? = null) // TrackingCycleDao.replace, one transaction
    suspend fun update(cycle: TrackingCycle)
}

interface UsageReadingRepository {
    fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>> // TelemetryDao.observeReadingsSince
}
```

- **Domain model vs entity:** `data/cycle/TrackingCycle` (status as the `CycleStatus` enum) maps to the Room entity `data/local/entity/TrackingCycle` (status as text) in `RoomTrackingCycleRepository`. Every write clears `synced_at`, queueing the row for upload (C3).
- **One active cycle:** replacing a cycle updates the old row and inserts the new one in one transaction, so Room's one-active-cycle trigger never fires and a crash cannot leave half a replacement.
- **Readings:** not `TelemetryRepository.observeDurableReadings()`, which shows only the most recent day; a cycle can be longer.
- **Contract:** `QuotaCycleManagerContract` (src/sharedTest) runs on the JVM against the in-memory repositories (`QuotaCycleManagerTest`) and on a device against in-memory Room (`RoomQuotaCycleManagerTest`).

## Not done yet

- **History:** past cycles are stored but not listed yet (D7).
- **Home:** it still shows preview fixtures (D5); Create and End keep those fixtures in step.
- **Forecast:** Details says it is not available yet (D6).
- **Alerts:** 75/90/100% and forecast alerts (D8).
- **Sync** (C3): cycles are queued for upload (`synced_at` null), but nothing uploads yet.
