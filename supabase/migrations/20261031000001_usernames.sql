-- DATAra — usernames as an alternative login identifier
--
-- Email stays the account's identity in Supabase Auth and the only recovery channel. A username
-- is a second way to type in who you are at login: the `username-login` Edge Function resolves it
-- to the email server-side, so the email never reaches a signed-out client.
--
-- Usernames are stored canonical (lowercase), so a plain unique index is case-insensitive in
-- practice. The column stays nullable: rows created before this migration, and every new sign-up
-- until the user finishes the username screen, have no username yet. The app's session gate
-- routes those users to that screen.

alter table public.users
  add column if not exists username text;

alter table public.users
  add constraint users_username_format
  check (username is null or username ~ '^[a-z0-9_]{3,20}$');

-- Also blocks the race where two users claim the same name at once: the second update fails
-- with 23505 and the client reports the name as taken.
create unique index if not exists users_username_key on public.users (username);

-- Availability check for the username screen. RLS only lets a user read their own row, so a
-- normal select cannot see other users' names; this definer function answers yes/no and never
-- returns anything else. Granted to signed-in users only — the screen is shown after sign-up.
create or replace function public.is_username_available(candidate text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select not exists (
    select 1 from public.users where username = lower(trim(candidate))
  );
$$;

revoke all on function public.is_username_available(text) from public, anon;
grant execute on function public.is_username_available(text) to authenticated;
