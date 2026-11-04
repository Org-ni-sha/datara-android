# Supabase schema

These files describe the **quota schema** for the approved user-defined quota direction
(confirmed 2026-10-29), built from [`docs/erd-draft.md`](../docs/erd-draft.md) (lead's decisions
of 2026-11-04; the DB admin's review is still pending). The D2 Room entities
(`app/src/main/java/com/capstone/datara/data/local/entity/`) match these tables column for column.
Never edit an applied migration.

The quota migration (`20261104000001`) replaced the legacy promo-based tables from the first C2
pass. It was applied to the hosted project with `supabase db push` on 2026-11-04 — see
[Applying the quota migration](#applying-the-quota-migration).

## Migrations

| File | What it does |
|---|---|
| `migrations/20260917000001_init_schema.sql` | Legacy: seven promo-based tables (five dropped by `20261104000001`) |
| `migrations/20260917000002_rls_policies.sql` | Legacy: RLS + policies + grants for those seven tables |
| `migrations/20260917000003_handle_new_user.sql` | Profile row created on sign-up |
| `migrations/20261031000001_usernames.sql` | `users.username` (lowercase, unique, `^[a-z0-9_]{3,20}$`) + `is_username_available()` |
| `migrations/20261104000001_quota_schema.sql` | Quota schema: drops the legacy tables, renames `devices.android_version`, adds the `users.phone_number` check, creates the six quota tables with RLS and grants |
| `functions/username-login/` | Edge Function: signs in by username without revealing the email |
| `seed.sql` | Empty — the quota schema has no shared reference data |

They are re-runnable except for the policy file, which errors on a second run because
`create policy` has no `if not exists`, and the usernames file, whose `add constraint` does the
same. `20261104000001` is not re-runnable at all (it drops and renames); it runs once, in one
transaction, and aborts without changing anything if a legacy table it drops still holds rows.

## Tables

| Table | One row per | Key rules |
|---|---|---|
| `users` | account (from the `on_auth_user_created` trigger) | `phone_number` optional, E.164 PH mobile `^\+639[0-9]{9}$` |
| `devices` | app install | `android_sdk_int` is `Build.VERSION.SDK_INT` |
| `tracking_cycles` | quota the user set | `quota_bytes > 0`; `status` in `active`/`reached`/`ended`; `reached` needs `reached_at`; `reached_at`, `ended_at` ≥ `start_at`; one `active` cycle per device (partial unique index) |
| `data_usage` | 10-minute window per device | unique `(device_id, interval_start_at)`; `measurement_status` in the harvester's eight values; `signal_dbm` null when unknown, never `-999`; bytes ≥ 0; end > start |
| `forecasts` | active cycle per hour | unique `(cycle_id, predicted_at)`; `active_model` in `global`/`local`; `local_*` null until the local layer has learned; `local` shown needs `local_next_hour_bytes` |
| `notifications` | alert sent | `notification_type` in `milestone_75`, `milestone_90`, `quota_reached`, `forecast_24h`, `forecast_3h`; unique `(cycle_id, notification_type)`; `read_at` null = unread |
| `notification_preferences` | user (`user_id` is the PK) | two switches, both default `true` |
| `user_model_state` | user (`user_id` is the PK) | `active_model` in `global`/`local`; `device_id` cleared (not cascaded) when its device row is deleted |

**Ownership foreign keys.** Child rows reference their parents with composite keys that include
`user_id`: `(device_id, user_id) → devices`, `(cycle_id, user_id) → tracking_cycles`. A plain FK
check runs without RLS, so a single-column FK would let a user attach rows to someone else's
device or cycle by guessing its UUID. The composite key makes that a `23503` FK violation.

**Delete behavior.** Deleting a user cascades to everything. Deleting a device cascades to its
cycles, usage and forecasts; deleting a cycle cascades to its forecasts and detaches (sets
`cycle_id` null on) its notifications. `user_model_state.device_id` is set null. The column-list
form `on delete set null (col)` needs Postgres 15 or later.

**`updated_at`** has a `now()` default but no trigger: Room is the writer, and the sync mapper
sends the phone's value.

## Usernames and Google sign-in

Email stays the account identity and the only recovery channel. A username is an alternative
login identifier: the app sends username + password to the `username-login` Edge Function, which
looks up the email with the service role, signs in on the server and returns session tokens only.
The function answers `invalid_credentials` for an unknown username and for a wrong password alike.

Deploy (once per change to the function):

```bash
supabase functions deploy username-login --no-verify-jwt
```

`--no-verify-jwt` is required: callers are signed out. `SUPABASE_URL`, `SUPABASE_ANON_KEY` and
`SUPABASE_SERVICE_ROLE_KEY` are provided to the function automatically; set nothing by hand.

Known limitation: Auth rate-limits sign-in per IP, and every username login reaches Auth from the
function's IP rather than the phone's. Repeated failures from one person can therefore throttle
username logins for others, and the per-device limit no longer applies. Acceptable for the
development phase; revisit before a public release (e.g. a per-username attempt counter).

Google sign-in uses Credential Manager on the device and `signInWith(IDToken)`; no redirect URL
or deep link is involved. Setup:

1. Google Cloud Console → APIs & Services → Credentials: create a **Web application** OAuth
   client and an **Android** OAuth client (package `com.capstone.datara` + your debug keystore's
   SHA-1 from `./gradlew signingReport`).
2. Supabase dashboard → Authentication → Providers → Google: enable it, enter the Web client ID
   and secret, and leave nonce checks on.
3. Put the Web client ID in `local.properties` as `GOOGLE_WEB_CLIENT_ID`. Without it the Google
   button is hidden.

Google accounts get a `public.users` row from the same trigger (Google supplies the `name`
metadata key) and are sent to the username screen on first sign-in.

Password rules (2026-10-31): the app requires at least 8 characters with a letter and a number
(`AuthValidator.isPasswordValid`), and resend is limited to once a minute. Match them on the
hosted project — dashboard → Authentication → Providers → Email: **Minimum password length 8**,
**Password requirements: Letters and digits**; and Authentication → Rate Limits / email settings:
one email per **60 seconds**. `config.toml` already has the same values for a local stack.

Email templates live in `templates/` and are versioned here; the hosted project needs them
pasted in by hand (dashboard → Authentication → Email Templates):

| Template | File | Subject |
|---|---|---|
| Reset Password | `templates/recovery.html` | `Your DATAra password reset code` |

It shows `{{ .Token }}` (the 6-digit code the app verifies) and says the code lasts 1 hour, so
keep **Email OTP Expiration** at 3600 seconds or update the wording. The logo loads from this
public repo's `main` branch on GitHub; if the repo goes private or the PNG moves, update the
`<img src>`.

Email confirmation is deliberately **off** while the app is in development. Turn it on before
release (dashboard → Authentication → Sign In / Providers → Email); the app will then need a
"check your inbox" step after Register, because sign-up will no longer return a session.

## CLI setup

The Supabase CLI is a per-developer tool, not a build dependency — it is not in the Gradle
version catalog and nothing in the Android build calls it. Install it once per machine.

**Windows** (no winget package exists; this is the pinned-binary approach):

```powershell
$v = '2.117.0'
$dest = "$env:LOCALAPPDATA\supabase\bin"
New-Item -ItemType Directory -Force $dest | Out-Null
Invoke-WebRequest "https://github.com/supabase/cli/releases/download/v$v/supabase_${v}_windows_amd64.zip" -OutFile "$env:TEMP\supabase.zip"
Expand-Archive "$env:TEMP\supabase.zip" -DestinationPath $dest -Force
# then add $dest to your user PATH and reopen the terminal
```

**macOS / Linux:** `brew install supabase/tap/supabase`, or the matching release archive.

Verify with `supabase --version` — the team is on **2.117.0**.

## Applying migrations to the hosted project

```bash
supabase login                        # opens a browser, stores a personal access token
supabase link --project-ref <ref>     # <ref> is the subdomain of your SUPABASE_URL
supabase db push --dry-run            # always look before pushing
supabase db push --include-seed       # applies migrations, then seed.sql
```

`link` writes the project ref into `supabase/.temp/`, which is git-ignored, so no
project identifier or token is committed.

### Applying the quota migration

Run this in the dashboard SQL editor first. Push only if **every** count is 0; otherwise stop and
plan a data copy (the migration also aborts on non-empty legacy tables, but do not rely on that):

```sql
select (select count(*) from public.promos) promos,
       (select count(*) from public.associations) associations,
       (select count(*) from public.data_usage) data_usage,
       (select count(*) from public.notifications) notifications,
       (select count(*) from public.users
         where phone_number is not null
           and phone_number !~ '^\+639[0-9]{9}$') bad_phone_numbers;
```

Then:

```bash
supabase db push --dry-run   # must list only 20261104000001_quota_schema.sql
supabase db push
```

Adding a schema change later:

```bash
supabase migration new <short_name>   # creates the next timestamped file
# write the SQL, then:
supabase db push --dry-run && supabase db push
```

Write the change as a **new migration**. Never edit an already-applied file — the CLI
tracks applied migrations by filename, so an edited file is silently skipped on the next
push and the live schema quietly diverges from the repo.

If you would rather not install anything, the SQL editor in the dashboard still works:
paste the files in filename order. That path does not record anything in the CLI's
migration history, so pick one approach and keep to it.

**The local Docker stack is optional.** The CLI's main job here is migration management
against the hosted project, but `supabase db start` + `supabase db reset` (Docker required)
proves a migration applies cleanly before it is pushed. `config.toml` is
required by the CLI and its `[auth]` / `[api]` sections describe a local stack that is
not run — they are not the live project's settings, so do not treat that file as a record
of production configuration.

## RLS verification

Policies that *look* right are not evidence. This is the check that closes C2, run against the
live project through PostgREST — the same path the Android client uses, so it tests what the app
actually experiences rather than what the SQL appears to say.

Last run against the hosted project **2026-09-17**, on the **legacy** schema (`data_usage` and
`providers` there are the since-dropped legacy tables), all seven checks passing:

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | B selects A's `data_usage` row by id | 0 rows | `[]` |
| 2 | B updates A's row | 0 rows changed | `[]` |
| 3 | B deletes A's row | 0 rows deleted | `[]` |
| 4 | B inserts a row with `user_id` = A | `42501` policy violation | violation raised |
| 5 | A re-reads the row afterwards | unchanged | `1.5` unchanged |
| 6 | B selects A's `public.users` row | 0 rows | `[]` |
| 7 | B selects `providers` | 3 carriers | Smart, Globe, DITO |

Checks 2 and 3 are the ones worth keeping: PostgREST returns HTTP 200 with an empty array when
RLS filters every candidate row, so a naive "did it 200?" test would call a *blocked* write a
success. Assert on the returned representation being empty, not on the status code.

Also confirmed in the same run: the `on_auth_user_created` trigger creates the `public.users` row
on sign-up and populates `name` from `raw_user_meta_data`, which is what `AuthRepository.signUp`
sends.

After the quota migration is pushed, re-run checks 1–6 against the new `data_usage` (insert a
`devices` row and a 10-minute window as A) and drop check 7, since `providers` no longer exists.

To re-run: sign up two accounts (`mailer_autoconfirm` is on, so the sign-up response carries a
usable `access_token`), have A insert a `devices` row and then a `data_usage` row, and drive
the checks with each account's bearer token. Delete the test rows afterwards; the test *accounts*
need a service_role key or Dashboard → Authentication → Users to remove.

## Type mapping (for the D2 Room entities)

Room entity names are the table names in `PascalCase` singular: `User`, `Device`,
`TrackingCycle`, `DataUsage`, `Forecast`, `Notification`, `NotificationPreferences`,
`UserModelState`.

| Postgres type | Kotlin (Room) | Columns |
|---|---|---|
| `uuid` | `String` (`String?` when nullable) | every PK and FK; nullable: `notifications.cycle_id`, `user_model_state.device_id` |
| `bigint` | `Long` (`Long?` when nullable) | `quota_bytes`, `bytes_rx`, `bytes_tx`, `remaining_bytes`, `global_next_hour_bytes`; nullable: `local_next_hour_bytes` |
| `integer` | `Int` (`Int?` when nullable) | `android_sdk_int`, `schema_version`, `hours_seen`; nullable: `signal_dbm` |
| `double precision` | `Double?` | `global_recent_mae`, `local_recent_mae` |
| `boolean` | `Boolean` | `milestones_enabled`, `forecast_alerts_enabled` |
| `text` with a `check` list | `String` (constants in Kotlin) | `tracking_cycles.status`, `data_usage.measurement_status`, `forecasts.active_model`, `user_model_state.active_model`, `notifications.notification_type` |
| `text` | `String` / `String?` | `name`, `title`, `message`, `carrier`, `network_type`, `model_version`, … |
| `jsonb` | `String` (serialized JSON) | `forecasts.inputs`, `user_model_state.parameters` |
| `timestamptz` | `Long` epoch millis UTC (`Long?` when nullable) | every `*_at` column |

**Timestamps are the one place the types do not match literally.** Postgres uses
`timestamptz`; Room uses `Long` epoch millis (UTC). Convert in the sync mapper, not in
the entity — a `String` timestamp in Room makes D7's range queries and D6's feature
extraction needlessly painful.

**Local-only column:** each synced Room entity adds `synced_at: Long?` (null = waiting to
upload). It is not a Postgres column.

**Sync convention:** generate UUID primary keys on the phone and reuse them when syncing, so a
retried batch upsert (C3) updates the same row instead of duplicating it. The natural keys
`data_usage (device_id, interval_start_at)`, `forecasts (cycle_id, predicted_at)` and
`notifications (cycle_id, notification_type)` are unique too, so sync must use proper conflict
handling rather than assume repeated plain inserts are idempotent.

## RLS

Every table has RLS enabled and policies scoped to `auth.uid()` (`users` by `id`, everything else
by `user_id`), for `authenticated` only. There is no shared reference table any more: the legacy
`providers` exception is gone, and the carrier is stored on each `data_usage` row.

The quota tables are granted to `authenticated` only; the migration revokes Supabase's default
`anon` grants on them. `users` has no delete policy (account deletion goes through Supabase Auth,
which cascades).

Because synced rows stay keyed to `user_id`, everything in this schema is
**pseudonymized, not anonymized** — use that wording in the manuscript. Only the A4 CSV
export is anonymized.

### Smoke test after applying

From the SQL editor (which runs as service role and bypasses RLS), confirm policies exist:

```sql
select tablename, policyname, cmd from pg_policies
where schemaname = 'public' order by tablename, cmd;

select tablename, rowsecurity from pg_tables
where schemaname = 'public' order by tablename;
```

The real check is from the app with two different accounts: user A must not be able to
read user B's `data_usage` rows.

**Local check of the quota migration (2026-11-04).** On a local stack (`supabase db start`, then
`supabase db reset`, CLI 2.117.0, Postgres 17.6), all five migrations and the seed applied
cleanly and `supabase db advisors --local` reported no issues. A two-user SQL script that acts as
`authenticated` with each user's JWT claims passed 34 checks:
- user B reads, updates and deletes none of user A's rows in any quota table
- B's insert with A's `user_id` is rejected (`42501`)
- B cannot attach a cycle or an alert to A's device or cycle (`23503`)
- `anon` has no grant
- every check constraint and unique key behaves as listed
- the device and user delete cascades behave as described

The migration's guard was also shown to abort when a legacy table held a row. This is not a
substitute for the PostgREST check above on the hosted project.
