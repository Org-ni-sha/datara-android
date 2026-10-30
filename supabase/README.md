# Supabase schema (Phase C2)

These files describe the **applied legacy promo-based schema**, not the final schema for the approved user-defined quota direction (confirmed 2026-10-29). Room entities do not exist yet. Future persistence and sync mappings must agree with the relevant migrations.

Quota/cycle storage, usage relationships and notification links require a separately designed C2 follow-up migration with RLS and compatibility handling. Do not delete `promos` or repurpose `associations` silently, and never edit applied migrations. Current quota UI state is sample/session state with no database integration. Auth and its profile trigger remain in use. See [ROADMAP](../ROADMAP.md) for pending work.

## Migrations

| File | What it does |
|---|---|
| `migrations/20260917000001_init_schema.sql` | Seven tables, constraints, indexes |
| `migrations/20260917000002_rls_policies.sql` | RLS enabled + per-table policies + grants |
| `migrations/20260917000003_handle_new_user.sql` | Profile row created on sign-up |
| `migrations/20261031000001_usernames.sql` | `users.username` (lowercase, unique, `^[a-z0-9_]{3,20}$`) + `is_username_available()` |
| `functions/username-login/` | Edge Function: signs in by username without revealing the email |
| `seed.sql` | The three carriers |

They are re-runnable except for the policy file, which errors on a second run because
`create policy` has no `if not exists`, and the usernames file, whose `add constraint` does the
same. To re-apply either, drop the policies or the `users_username_format` constraint first.

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

**The local Docker stack (`supabase start`) is deliberately not set up.** This repo uses
the CLI for migration management against the hosted project only. `config.toml` is
required by the CLI and its `[auth]` / `[api]` sections describe a local stack that is
not run — they are not the live project's settings, so do not treat that file as a record
of production configuration.

## RLS verification

Policies that *look* right are not evidence. This is the check that closes C2, run against the
live project through PostgREST — the same path the Android client uses, so it tests what the app
actually experiences rather than what the SQL appears to say.

Last run **2026-09-17**, all seven checks passing:

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

To re-run: sign up two accounts (`mailer_autoconfirm` is on, so the sign-up response carries a
usable `access_token`), have A insert a `devices` row and then a `data_usage` row, and drive
checks 1–7 with each account's bearer token. Delete the test rows afterwards; the test *accounts*
need a service_role key or Dashboard → Authentication → Users to remove.

## Legacy type mapping (reference for future persistence design)

| Postgres table | Planned legacy entity name (not implemented) | Postgres type | Kotlin mapping |
|---|---|---|---|
| `users` | `User` | `uuid` | `String` |
| `providers` | `Provider` | `text` | `String` |
| `devices` | `Device` | `integer` (`android_version`) | `Int` |
| `promos` | `Promo` | `double precision` (`data_amount_gb`) | `Double` |
| `promos` | `Promo` | `numeric(10,2)` (`promo_price`) | `Double` |
| `data_usage` | `DataUsage` | `double precision` | `Double` |
| `data_usage` | `DataUsage` | `integer` (`signal_strength`) | `Int?` |
| `notifications` | `Notification` | `boolean` (`is_read`) | `Boolean` |
| `associations` | `Association` | `uuid` (nullable FKs) | `String?` |

**Timestamps are the one place the types do not match literally.** Postgres uses
`timestamptz`; the planned Room mapping uses `Long` epoch millis (UTC). Convert in the sync mapper, not in
the entity — a `String` timestamp in Room makes D7's range queries and D6's feature
extraction needlessly painful.

**Planned sync convention:** generate UUID primary keys client-side in Room and reuse them when syncing, so a
retried batch upsert (C3) updates the same row instead of duplicating it. `data_usage`
additionally has a `(user_id, device_id, usage_date)` unique constraint for that reason —
future sync must use appropriate conflict handling rather than assume repeated plain inserts are idempotent.

## RLS

Every table has RLS enabled and is scoped to `auth.uid()`.

`providers` is the deliberate exception to "scoped by `user_id`": it is shared reference
data with no owner, so it has a read-for-authenticated policy and **no** write policy.
Writes go through the service role (dashboard/SQL editor), never the app.

`promos` carries a nullable `user_id`: `null` means a shared catalog row (the former D3 design, readable under the authenticated catalog policy, not writable from the client), non-null means a promo the
user entered manually, visible only to them.

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

## Legacy design issues to account for in quota migration planning

- `associations` is implemented according to the original promo design — one row carrying
  `user_id`, `device_id`, `provider_id`, `promo_id`, `notification_id`, `data_usage_id`
  and `total_data_used_gb`. It behaves more like a per-user "active setup" record than a
  true junction table. The approved quota direction needs explicit cycle relationships instead; define them before D2/D3 persistence and preserve existing data deliberately.
- `promos.expiry_date` is per-activation, not per-catalog-row, which is why it is
  nullable. This historical modeling issue does not introduce promo expiry into the new quota flow. Quota lifecycle fields must be designed separately.
