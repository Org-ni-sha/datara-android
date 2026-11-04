# Quota cycles (ROADMAP D3)

A cycle is one quota the user set: `tracking_cycles` in [the ERD](erd-draft.md). This page covers how usage in a cycle is measured, the status rules, and the storage seam D2 replaces.

## Code

| Piece | Where | What |
|---|---|---|
| `TrackingCycle`, `CycleStatus` | `data/cycle/TrackingCycle.kt` | The row, field for field with `tracking_cycles`. Times are epoch millis and amounts are bytes. |
| `TrackingCycleRepository` | `data/cycle/` | Storage seam. Bound to `InMemoryTrackingCycleRepository` in `di/CycleModule`. |
| `UsageReadingRepository` | `data/cycle/` | The 10-minute readings a cycle is measured from. It reads D4's `durable_readings` through `TelemetryRepository`. |
| `CycleOwnerProvider`, `DeviceIdProvider` | `data/cycle/CycleOwnerProvider.kt` | The signed-in user id (Supabase session) and this install's `device_id`. |
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
- **Updates:** usage only changes when a reading is stored, so Details shows "Measured up to …", the end of the latest reading.

How each `measurement_status` counts:

| Status | Counted | Flagged as possibly missing usage |
|---|---|---|
| `OK`, `BASELINE`, `INTERRUPTED` | bytes as recorded | no |
| `GAP` | bytes as recorded (durable totals are cumulative, so a long span is still exact) | no |
| `COUNTER_RESET`, `COUNTER_JUMP`, `UNSUPPORTED` | 0 bytes, as recorded | yes (`lostReadings`) |
| `CLOCK_ERROR`, or `until < since` | skipped: it cannot be placed in the window | yes (`lostReadings`) |

Time inside the cycle that no reading covers is reported as `unmeasuredMillis`. That happens when tracking was off, or when the process died and the in-memory checkpoint was lost. Details then says usage may be missing and offers **Check tracking**. Once D2 persists the checkpoint, the next read covers such a hole as a `GAP` span instead.

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

## Storage seam (for D2)

The interfaces follow DAO conventions: streams the UI follows are `Flow`, and one-shot reads and writes are `suspend`.

```kotlin
interface TrackingCycleRepository {
    fun observeLatest(userId: String, deviceId: String): Flow<TrackingCycle?> // order by start_at desc limit 1
    suspend fun latest(userId: String, deviceId: String): TrackingCycle?
    fun observeAll(userId: String): Flow<List<TrackingCycle>>                 // newest first (History, D7)
    suspend fun insert(cycle: TrackingCycle)
    suspend fun update(cycle: TrackingCycle)                                  // by cycle_id
}

interface UsageReadingRepository {
    fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>> // max(since, until) >= from, by since
}
```

For D2:
- **Cycles:** bind a Room-backed `TrackingCycleRepository` in `CycleModule`. `status` is stored as `CycleStatus.value` (`active`/`reached`/`ended`), which needs a type converter or a mapper. Add the local-only `synced_at`.
- **Index:** add the partial unique index `(device_id) where status = 'active'`.
- **Readings:** `TelemetryUsageReadingRepository` filters `TelemetryRepository.observeDurableReadings()` in memory. With Room, a range query on `durable_readings` is cheaper.
- **Contract:** `QuotaCycleManagerTest` runs against the in-memory repositories. Point it at the Room versions too.

The in-memory telemetry repository now keeps about 35 days of 10-minute readings (previously one day), so a cycle's used total does not drop as old readings are trimmed.

## Not done yet

- **Durable storage:** cycles are lost on process death, and past cycles are not kept. Blocked by D2.
- **Home:** it still shows preview fixtures (D5); Create and End keep those fixtures in step.
- **Forecast:** Details says it is not available yet (D6).
- **Alerts:** 75/90/100% and forecast alerts (D8).
- **Sync** (C3): `devices` row registration is part of it.
- **Device check:** not yet checked on a phone.
