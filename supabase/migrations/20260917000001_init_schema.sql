-- DATAra — Phase C2: database schema definition
--
-- Conventions (must hold for D2's Room entities too):
--   Postgres  : snake_case plural tables  (data_usage)
--   Room      : PascalCase singular entity (DataUsage)
--   Timestamps: timestamptz here, Long epoch-millis UTC in Room, converted in the sync mapper.
--   GB values : double precision, matching Kotlin Double.
--   Primary keys are UUIDs generated client-side in Room and reused on sync, so a retried
--   batch upsert is idempotent rather than duplicating rows.

create table if not exists public.users (
  id          uuid primary key references auth.users (id) on delete cascade,
  email       text not null,
  name        text,
  -- Optional secondary profile field only (D9); the login identifier is email.
  phone_number text,
  created_at  timestamptz not null default now()
);

create table if not exists public.providers (
  provider_id   uuid primary key default gen_random_uuid(),
  provider_name text not null unique,
  created_at    timestamptz not null default now()
);

create table if not exists public.devices (
  device_id       uuid primary key default gen_random_uuid(),
  user_id         uuid not null references public.users (id) on delete cascade,
  device_name     text,
  device_model    text not null,
  android_version integer not null,
  created_at      timestamptz not null default now()
);

-- user_id null marks a shared catalog promo (D3's carrier catalog); a non-null user_id
-- marks a promo the user entered manually, visible only to them.
create table if not exists public.promos (
  promo_id          uuid primary key default gen_random_uuid(),
  user_id           uuid references public.users (id) on delete cascade,
  provider_id       uuid references public.providers (provider_id) on delete restrict,
  promo_name        text not null,
  data_amount_gb    double precision not null,
  promo_price       numeric(10, 2),
  validity_days     integer not null,
  -- Null on catalog rows; set once a user activates or manually enters the promo.
  expiry_date       timestamptz,
  promo_description text,
  created_at        timestamptz not null default now(),
  constraint promos_data_amount_positive check (data_amount_gb > 0),
  constraint promos_validity_positive check (validity_days > 0)
);

create table if not exists public.data_usage (
  data_usage_id     uuid primary key default gen_random_uuid(),
  user_id           uuid not null references public.users (id) on delete cascade,
  device_id         uuid not null references public.devices (device_id) on delete cascade,
  usage_date        timestamptz not null,
  data_consumed_gb  double precision not null default 0,
  data_remaining_gb double precision,
  signal_strength   integer,
  created_at        timestamptz not null default now(),
  constraint data_usage_snapshot_unique unique (user_id, device_id, usage_date)
);

create table if not exists public.notifications (
  notification_id   uuid primary key default gen_random_uuid(),
  user_id           uuid not null references public.users (id) on delete cascade,
  notification_type text not null,
  title             text not null,
  message           text not null,
  date_sent         timestamptz not null default now(),
  is_read           boolean not null default false
);

create table if not exists public.associations (
  association_id     uuid primary key default gen_random_uuid(),
  user_id            uuid not null references public.users (id) on delete cascade,
  device_id          uuid references public.devices (device_id) on delete cascade,
  provider_id        uuid references public.providers (provider_id) on delete set null,
  promo_id           uuid references public.promos (promo_id) on delete set null,
  notification_id    uuid references public.notifications (notification_id) on delete set null,
  data_usage_id      uuid references public.data_usage (data_usage_id) on delete set null,
  total_data_used_gb double precision not null default 0,
  created_at         timestamptz not null default now()
);

create index if not exists devices_user_id_idx on public.devices (user_id);
create index if not exists promos_user_id_idx on public.promos (user_id);
create index if not exists promos_provider_id_idx on public.promos (provider_id);
-- Serves D7's history queries (latest-first within a window) and D6's feature extraction.
create index if not exists data_usage_user_date_idx on public.data_usage (user_id, usage_date desc);
create index if not exists notifications_user_date_idx on public.notifications (user_id, date_sent desc);
create index if not exists associations_user_id_idx on public.associations (user_id);
