-- DATAra — C2 follow-up: quota schema
--
-- Replaces the legacy promo-based tables with the approved user-defined quota design
-- (docs/erd-draft.md, decisions of 2026-11-04). Everything is in one file on purpose: each
-- migration runs in its own transaction, and splitting tables from their RLS would leave a
-- window where a new table is reachable through the Data API without row security.
--
-- Conventions (must hold for D2's Room entities too):
--   Postgres  : snake_case plural tables  (tracking_cycles)
--   Room      : PascalCase singular entity (TrackingCycle)
--   Timestamps: timestamptz here, Long epoch-millis UTC in Room, converted in the sync mapper.
--   Amounts   : bytes as bigint (Kotlin Long). MB is display-only, 1 MB = 1,000,000 bytes.
--   Keys      : UUIDs generated on the phone and reused on sync, so a retried upload upserts.
--
-- Ownership: every child row carries user_id, and its parent foreign keys include user_id
-- (e.g. (device_id, user_id) -> devices). A plain FK check ignores RLS, so without this a user
-- could attach rows to another user's device or cycle by guessing its UUID.

-- 1. Safety guard -------------------------------------------------------------------------
-- The app has never written to the legacy tables (D2 and sync do not exist yet). If that is
-- not true on this database, abort the whole migration instead of dropping data.
-- providers is excluded: it only holds the three seeded carrier names.
do $$
declare
  legacy_rows bigint;
begin
  select (select count(*) from public.promos)
       + (select count(*) from public.associations)
       + (select count(*) from public.data_usage)
       + (select count(*) from public.notifications)
    into legacy_rows;
  if legacy_rows > 0 then
    raise exception 'quota_schema: legacy tables hold % row(s); plan a data copy instead of dropping them', legacy_rows;
  end if;
end;
$$;

-- 2. Drop the legacy tables -----------------------------------------------------------------
-- associations references all the others, so it goes first. Policies and indexes go with
-- their tables.
drop table public.associations;
drop table public.promos;
drop table public.providers;
drop table public.data_usage;
drop table public.notifications;

-- 3. Alter the kept tables ------------------------------------------------------------------
-- The column holds Build.VERSION.SDK_INT (e.g. 34), not the marketing version ("14").
alter table public.devices rename column android_version to android_sdk_int;

-- Target for the composite ownership FKs below. device_id alone is already unique.
alter table public.devices
  add constraint devices_device_user_key unique (device_id, user_id);

-- Optional, typed in by the user, never read from the SIM and never a login identifier.
-- Stored as E.164 for PH mobiles; the app accepts 09XX XXX XXXX and normalizes it.
-- Fails (and rolls back the migration) if an existing value does not match.
alter table public.users
  add constraint users_phone_number_e164
  check (phone_number is null or phone_number ~ '^\+639[0-9]{9}$');

-- 4. New tables -----------------------------------------------------------------------------

-- One quota the user set, and how it ended.
create table public.tracking_cycles (
  cycle_id    uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users (id) on delete cascade,
  device_id   uuid not null,
  name        text,
  quota_bytes bigint not null,
  start_at    timestamptz not null,
  -- End of the 10-minute reading in which measured usage crossed quota_bytes.
  reached_at  timestamptz,
  ended_at    timestamptz,
  -- reached, then ended by the user: status stays 'reached' and ended_at is set.
  status      text not null default 'active',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint tracking_cycles_device_fk foreign key (device_id, user_id)
    references public.devices (device_id, user_id) on delete cascade,
  constraint tracking_cycles_cycle_user_key unique (cycle_id, user_id),
  constraint tracking_cycles_quota_positive check (quota_bytes > 0),
  constraint tracking_cycles_status_valid check (status in ('active', 'reached', 'ended')),
  constraint tracking_cycles_reached_has_time check (status <> 'reached' or reached_at is not null),
  constraint tracking_cycles_reached_after_start check (reached_at is null or reached_at >= start_at),
  constraint tracking_cycles_ended_after_start check (ended_at is null or ended_at >= start_at)
);

-- At most one active cycle per device.
create unique index tracking_cycles_one_active_per_device
  on public.tracking_cycles (device_id) where status = 'active';

-- One row per 10-minute window, rolled up on the phone from its 30-second samples.
-- No cycle_id: cycles are found by time window, and a window can straddle a cycle start.
create table public.data_usage (
  data_usage_id      uuid primary key default gen_random_uuid(),
  user_id            uuid not null references public.users (id) on delete cascade,
  device_id          uuid not null,
  interval_start_at  timestamptz not null,
  interval_end_at    timestamptz not null,
  bytes_rx           bigint not null,
  bytes_tx           bigint not null,
  -- Worst status in the window: COUNTER_JUMP > COUNTER_RESET > UNSUPPORTED > GAP >
  -- INTERRUPTED > BASELINE > OK (harvester schema version 3 values).
  measurement_status text not null,
  network_type       text,
  -- Median of the known values; null when none. The phone's -999 sentinel is never uploaded.
  signal_dbm         integer,
  carrier            text,
  schema_version     integer not null,
  constraint data_usage_device_fk foreign key (device_id, user_id)
    references public.devices (device_id, user_id) on delete cascade,
  constraint data_usage_device_interval_key unique (device_id, interval_start_at),
  constraint data_usage_interval_order check (interval_end_at > interval_start_at),
  constraint data_usage_bytes_non_negative check (bytes_rx >= 0 and bytes_tx >= 0),
  constraint data_usage_signal_not_sentinel check (signal_dbm is null or signal_dbm <> -999),
  constraint data_usage_measurement_status_valid check (measurement_status in (
    'OK', 'BASELINE', 'COUNTER_RESET', 'COUNTER_JUMP',
    'UNSUPPORTED', 'GAP', 'INTERRUPTED', 'CLOCK_ERROR'
  ))
);

-- The prediction log: one row per active cycle per hour, both models in every row (E3).
create table public.forecasts (
  forecast_id            uuid primary key default gen_random_uuid(),
  user_id                uuid not null references public.users (id) on delete cascade,
  device_id              uuid not null,
  cycle_id               uuid not null,
  -- Start of the hour.
  predicted_at           timestamptz not null,
  remaining_bytes        bigint not null,
  -- The model the user was shown.
  active_model           text not null,
  global_next_hour_bytes bigint not null,
  -- PET from the hour-by-hour projection; null when it passes the 30-day cap.
  global_reach_at        timestamptz,
  -- Both local_* columns stay null until the local layer has learned enough.
  local_next_hour_bytes  bigint,
  local_reach_at         timestamptz,
  -- The ONNX file that produced the global prediction.
  model_version          text not null,
  -- Feature values, so every prediction can be checked later.
  inputs                 jsonb not null,
  constraint forecasts_device_fk foreign key (device_id, user_id)
    references public.devices (device_id, user_id) on delete cascade,
  constraint forecasts_cycle_fk foreign key (cycle_id, user_id)
    references public.tracking_cycles (cycle_id, user_id) on delete cascade,
  constraint forecasts_cycle_hour_key unique (cycle_id, predicted_at),
  constraint forecasts_active_model_valid check (active_model in ('global', 'local')),
  constraint forecasts_local_shown_has_prediction
    check (active_model <> 'local' or local_next_hour_bytes is not null)
);

-- Fixed alerts, each sent at most once per cycle.
create table public.notifications (
  notification_id   uuid primary key default gen_random_uuid(),
  user_id           uuid not null references public.users (id) on delete cascade,
  cycle_id          uuid,
  notification_type text not null,
  title             text not null,
  message           text not null,
  sent_at           timestamptz not null default now(),
  -- null = unread
  read_at           timestamptz,
  -- Deleting a cycle keeps its alerts in the inbox, detached (PG15+ column list).
  constraint notifications_cycle_fk foreign key (cycle_id, user_id)
    references public.tracking_cycles (cycle_id, user_id) on delete set null (cycle_id),
  constraint notifications_cycle_type_key unique (cycle_id, notification_type),
  constraint notifications_type_valid check (notification_type in (
    'milestone_75', 'milestone_90', 'quota_reached', 'forecast_24h', 'forecast_3h'
  ))
);

-- The two Settings switches, one row per user.
create table public.notification_preferences (
  user_id                 uuid primary key references public.users (id) on delete cascade,
  -- milestone_75, milestone_90, quota_reached
  milestones_enabled      boolean not null default true,
  -- forecast_24h, forecast_3h
  forecast_alerts_enabled boolean not null default true,
  updated_at              timestamptz not null default now()
);

-- Backup of the per-user local layer (residual correction), so a reinstall does not restart
-- personalization from zero.
create table public.user_model_state (
  user_id           uuid primary key references public.users (id) on delete cascade,
  active_model      text not null default 'global',
  -- The residual correction: a few numbers as JSON.
  parameters        jsonb not null default '{}'::jsonb,
  -- The phone it was learned on; not part of the key.
  device_id         uuid,
  hours_seen        integer not null default 0,
  global_recent_mae double precision,
  local_recent_mae  double precision,
  updated_at        timestamptz not null default now(),
  -- Only device_id is cleared when the device row goes; user_id is the primary key.
  constraint user_model_state_device_fk foreign key (device_id, user_id)
    references public.devices (device_id, user_id) on delete set null (device_id),
  constraint user_model_state_active_model_valid check (active_model in ('global', 'local')),
  constraint user_model_state_hours_non_negative check (hours_seen >= 0)
);

-- 5. Indexes for RLS and lookups ------------------------------------------------------------
-- Unique keys above already index (device_id, interval_start_at), (cycle_id, predicted_at),
-- (cycle_id, notification_type) and (cycle_id, user_id).
create index tracking_cycles_user_start_idx on public.tracking_cycles (user_id, start_at desc);
create index tracking_cycles_device_user_idx on public.tracking_cycles (device_id, user_id);
-- History (D7) and feature extraction (D6) read windows latest-first per user.
create index data_usage_user_interval_idx on public.data_usage (user_id, interval_start_at desc);
create index data_usage_device_user_idx on public.data_usage (device_id, user_id);
create index forecasts_user_predicted_idx on public.forecasts (user_id, predicted_at desc);
create index forecasts_device_user_idx on public.forecasts (device_id, user_id);
create index forecasts_cycle_user_idx on public.forecasts (cycle_id, user_id);
create index notifications_user_sent_idx on public.notifications (user_id, sent_at desc);
create index notifications_cycle_user_idx on public.notifications (cycle_id, user_id);
create index user_model_state_device_user_idx on public.user_model_state (device_id, user_id);

-- 6. Row Level Security ---------------------------------------------------------------------
-- auth.uid() is wrapped in a scalar subquery so Postgres evaluates it once per statement
-- instead of once per row (same style as 20260917000002).
alter table public.tracking_cycles          enable row level security;
alter table public.data_usage               enable row level security;
alter table public.forecasts                enable row level security;
alter table public.notifications            enable row level security;
alter table public.notification_preferences enable row level security;
alter table public.user_model_state         enable row level security;

-- tracking_cycles ----------------------------------------------------------------------------
create policy tracking_cycles_select_own on public.tracking_cycles
  for select to authenticated using ((select auth.uid()) = user_id);

create policy tracking_cycles_insert_own on public.tracking_cycles
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy tracking_cycles_update_own on public.tracking_cycles
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy tracking_cycles_delete_own on public.tracking_cycles
  for delete to authenticated using ((select auth.uid()) = user_id);

-- data_usage ---------------------------------------------------------------------------------
create policy data_usage_select_own on public.data_usage
  for select to authenticated using ((select auth.uid()) = user_id);

create policy data_usage_insert_own on public.data_usage
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy data_usage_update_own on public.data_usage
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy data_usage_delete_own on public.data_usage
  for delete to authenticated using ((select auth.uid()) = user_id);

-- forecasts ----------------------------------------------------------------------------------
create policy forecasts_select_own on public.forecasts
  for select to authenticated using ((select auth.uid()) = user_id);

create policy forecasts_insert_own on public.forecasts
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy forecasts_update_own on public.forecasts
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy forecasts_delete_own on public.forecasts
  for delete to authenticated using ((select auth.uid()) = user_id);

-- notifications ------------------------------------------------------------------------------
create policy notifications_select_own on public.notifications
  for select to authenticated using ((select auth.uid()) = user_id);

create policy notifications_insert_own on public.notifications
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy notifications_update_own on public.notifications
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy notifications_delete_own on public.notifications
  for delete to authenticated using ((select auth.uid()) = user_id);

-- notification_preferences -------------------------------------------------------------------
create policy notification_preferences_select_own on public.notification_preferences
  for select to authenticated using ((select auth.uid()) = user_id);

create policy notification_preferences_insert_own on public.notification_preferences
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy notification_preferences_update_own on public.notification_preferences
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy notification_preferences_delete_own on public.notification_preferences
  for delete to authenticated using ((select auth.uid()) = user_id);

-- user_model_state ---------------------------------------------------------------------------
create policy user_model_state_select_own on public.user_model_state
  for select to authenticated using ((select auth.uid()) = user_id);

create policy user_model_state_insert_own on public.user_model_state
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy user_model_state_update_own on public.user_model_state
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy user_model_state_delete_own on public.user_model_state
  for delete to authenticated using ((select auth.uid()) = user_id);

-- 7. Grants ---------------------------------------------------------------------------------
-- Supabase's default privileges grant new public tables to anon and authenticated with every
-- privilege, including TRUNCATE (which ignores RLS). Start from nothing, then grant the four
-- the client needs, to authenticated only; RLS still filters every row.
revoke all on
  public.tracking_cycles, public.data_usage, public.forecasts,
  public.notifications, public.notification_preferences, public.user_model_state
  from anon, authenticated, public;

grant select, insert, update, delete on
  public.tracking_cycles, public.data_usage, public.forecasts,
  public.notifications, public.notification_preferences, public.user_model_state
  to authenticated;
