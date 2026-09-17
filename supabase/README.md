# Supabase schema (Phase C2)

The Postgres side of DATAra. These files are the source of truth for the backend schema —
the Room entities in D2 mirror them, and the two must not drift.

## Migrations

| File | What it does |
|---|---|
| `migrations/20260917000001_init_schema.sql` | Seven tables, constraints, indexes |
| `migrations/20260917000002_rls_policies.sql` | RLS enabled + per-table policies + grants |
| `migrations/20260917000003_handle_new_user.sql` | Profile row created on sign-up |
| `seed.sql` | The three carriers |

They are re-runnable except for the policy file, which errors on a second run because
`create policy` has no `if not exists`. To re-apply it, drop the policies first.

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

## Type mapping (keep this table current — it is what D2 builds against)

| Postgres table | Room entity | Postgres type | Kotlin type |
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
`timestamptz`; Room stores `Long` epoch millis (UTC). Convert in the sync mapper, not in
the entity — a `String` timestamp in Room makes D7's range queries and D6's feature
extraction needlessly painful.

**UUID primary keys are generated client-side** in Room and reused when syncing, so a
retried batch upsert (C3) updates the same row instead of duplicating it. `data_usage`
additionally has a `(user_id, device_id, usage_date)` unique constraint for that reason —
sync with `onConflict` set, not a plain insert.

## RLS

Every table has RLS enabled and is scoped to `auth.uid()`.

`providers` is the deliberate exception to "scoped by `user_id`": it is shared reference
data with no owner, so it has a read-for-authenticated policy and **no** write policy.
Writes go through the service role (dashboard/SQL editor), never the app.

`promos` carries a nullable `user_id`: `null` means a shared catalog row (D3's carrier
catalog, readable by everyone, not writable from the client), non-null means a promo the
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

## Open decisions left for whoever builds D2/D3

- `associations` is implemented exactly as specified in `AGENTS.md` — one row carrying
  `user_id`, `device_id`, `provider_id`, `promo_id`, `notification_id`, `data_usage_id`
  and `total_data_used_gb`. It behaves more like a per-user "active setup" record than a
  true junction table. If D3 ends up wanting one row per active promo instead, that is a
  schema change to make deliberately, not by accident.
- `promos.expiry_date` is per-activation, not per-catalog-row, which is why it is
  nullable. If a user can hold several promos at once, expiry likely belongs on
  `associations` instead.
