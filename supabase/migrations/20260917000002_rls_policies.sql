-- DATAra — Phase C2: Row Level Security
--
-- Every table is scoped to auth.uid(). providers is the one exception: it is shared
-- reference data (Smart/Globe/DITO), readable by any signed-in user and writable only
-- by the service role, so it has a read policy and deliberately no write policy.
--
-- auth.uid() is wrapped in a scalar subquery so Postgres evaluates it once per statement
-- instead of once per row.

alter table public.users         enable row level security;
alter table public.providers     enable row level security;
alter table public.devices       enable row level security;
alter table public.promos        enable row level security;
alter table public.data_usage    enable row level security;
alter table public.notifications enable row level security;
alter table public.associations  enable row level security;

-- users --------------------------------------------------------------------
create policy users_select_own on public.users
  for select to authenticated using ((select auth.uid()) = id);

create policy users_insert_own on public.users
  for insert to authenticated with check ((select auth.uid()) = id);

create policy users_update_own on public.users
  for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- No delete policy: account deletion goes through Supabase Auth, which cascades here.

-- providers ----------------------------------------------------------------
create policy providers_select_all on public.providers
  for select to authenticated using (true);

-- devices ------------------------------------------------------------------
create policy devices_select_own on public.devices
  for select to authenticated using ((select auth.uid()) = user_id);

create policy devices_insert_own on public.devices
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy devices_update_own on public.devices
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy devices_delete_own on public.devices
  for delete to authenticated using ((select auth.uid()) = user_id);

-- promos -------------------------------------------------------------------
-- Catalog rows (user_id is null) are readable by everyone but writable by no one
-- through the client; a user can only create and edit their own manual entries.
create policy promos_select_catalog_or_own on public.promos
  for select to authenticated using (user_id is null or (select auth.uid()) = user_id);

create policy promos_insert_own on public.promos
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy promos_update_own on public.promos
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy promos_delete_own on public.promos
  for delete to authenticated using ((select auth.uid()) = user_id);

-- data_usage ---------------------------------------------------------------
create policy data_usage_select_own on public.data_usage
  for select to authenticated using ((select auth.uid()) = user_id);

create policy data_usage_insert_own on public.data_usage
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy data_usage_update_own on public.data_usage
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy data_usage_delete_own on public.data_usage
  for delete to authenticated using ((select auth.uid()) = user_id);

-- notifications ------------------------------------------------------------
create policy notifications_select_own on public.notifications
  for select to authenticated using ((select auth.uid()) = user_id);

create policy notifications_insert_own on public.notifications
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy notifications_update_own on public.notifications
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy notifications_delete_own on public.notifications
  for delete to authenticated using ((select auth.uid()) = user_id);

-- associations -------------------------------------------------------------
create policy associations_select_own on public.associations
  for select to authenticated using ((select auth.uid()) = user_id);

create policy associations_insert_own on public.associations
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy associations_update_own on public.associations
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy associations_delete_own on public.associations
  for delete to authenticated using ((select auth.uid()) = user_id);

-- Table-level grants. RLS still filters every row; these only make the tables
-- reachable by the PostgREST roles the Android client authenticates as.
grant select, insert, update, delete on
  public.users, public.devices, public.promos,
  public.data_usage, public.notifications, public.associations
  to authenticated;

grant select on public.providers to authenticated;
