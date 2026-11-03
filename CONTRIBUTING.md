# Contributing to DATAra

This document is the source of truth for how we branch, commit, and merge in this repo. If you're an AI agent working on this codebase, follow these conventions exactly — don't default to generic Git habits.

---

## Branching

`main` is the only permanent branch. **`main` must always build.** Nobody pushes to it directly — everything comes in through a Pull Request.

Create a branch off `main` for every task:

```
git checkout main
git pull
git checkout -b feature/login-screen
```

### Naming: `<type>/<short-description>`

| Type | Use for | Example |
|---|---|---|
| `feature/` | New functionality | `feature/register-screen` |
| `fix/` | Bug fixes | `fix/navgraph-not-called` |
| `style/` | UI/styling tweaks | `style/update-auth-screen` |
| `refactor/` | Restructuring, no behavior change | `refactor/auth-repository` |
| `test/` | Adding or fixing tests | `test/auth-viewmodel` |
| `chore/` | Setup, config, deps, tooling | `chore/add-gitignore` |
| `docs/` | Docs only | `docs/setup-guide` |

Lowercase, hyphens, no spaces. Note: branches use `feature/...`, while commit messages use the abbreviated `feat(...)`. Delete the branch after it's merged.

---

## Commit Messages — Conventional Commits

```
<type>(<scope>): <short summary, present tense>
```

| Type | Meaning |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Neither a fix nor a feature |
| `chore` | Build config, dependencies, tooling |
| `docs` | Documentation only |
| `test` | Adding/fixing tests |
| `style` | Formatting only, no logic change |

**Scope** = the part of the app touched (`auth`, `harvester`, `dashboard`, `db`, `nav`, `quota`, `settings`, etc.).

### Examples

```
feat(auth): wire up register screen to Supabase signUp
fix(nav): call DataraNavGraph from MainActivity setContent
feat(quota): add quota cycle details screen
fix(db): correct foreign key on Association entity
chore(gradle): add supabase-kt and ktor dependencies
refactor(viewmodel): extract AuthUiState into sealed interface
```

Rules:
- Imperative, present tense: "add," not "added."
- Keep the summary line under ~60 characters; put extra detail in a second paragraph if needed.
- One logical change per commit. If the message needs "and" to describe two unrelated things, split it into two commits.
- No `wip`, `fix stuff`, or similar — even on a short-lived branch, the history needs to be readable by teammates and by whoever reviews the PR.

---

## Pull Requests

Every change goes through a PR, even small ones — this is the safety net against `main` breaking before a demo.

1. Push your branch: `git push -u origin feature/login-screen`
2. Open a PR: base = `main`, compare = your branch. Use the PR template — fill in what changed, why, and anything the reviewer should specifically check.
3. Wait for the **Android CI** check to pass (see below).
4. **The lead programmer (@saybbbb) approves before merging.** Approvals from anyone else do not unlock the merge.
5. Merge using **Squash and merge** — collapses messy in-progress commits into one clean commit on `main`.
6. Delete the branch after merge.

`main` is protected by two repository rulesets (Settings → Rules → Rulesets):

- **Protect main** — applies to everyone, no exceptions: PRs required, squash merge only, the Android CI check must pass on an up-to-date branch, no deletion, no force pushes, no direct pushes.
- **Lead review** — one approval, and it must come from a code owner ([`.github/CODEOWNERS`](.github/CODEOWNERS): the lead). GitHub never lets anyone approve their own PR, so the `leads` team (the lead only) may bypass this ruleset when merging a pull request. The lead's own PRs therefore still need green CI, but no second approval; the bypass is recorded on the PR.

### Continuous integration (CI)

Every PR and every push to `main` runs [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) on GitHub Actions. It runs the same checks you would run locally:

```
./gradlew testDebugUnitTest assembleDebug lintDebug
```

- **Where to see it:** the checks box at the bottom of the PR, or the **Actions** tab of the repo. It takes a few minutes; the first run is slower while Gradle downloads are cached.
- **If it fails (red ❌):** click **Details** next to the check, open the step marked ❌ and read the last lines of its log. Fix it on your branch and push — CI re-runs automatically.
  - *Unit tests* — a test broke; the `unit-test-report` artifact has the details.
  - *Lint* — an error from Android's code checker; the `lint-report` artifact lists it with an explanation. Warnings do not fail the build, errors do.
- **Try the PR build:** download the `datara-debug-apk` artifact from the run's page. CI has no `local.properties`, so that build cannot sign in — use it for checking UI, not accounts.
- Run the command above before pushing to catch problems early.

---

## Keeping a branch up to date

If your branch sits open for more than a day or two while `main` moves:

```
git checkout feature/login-screen
git fetch origin
git rebase origin/main
```

Resolve any conflicts, then `git add <files>` and `git rebase --continue`. If rebasing isn't comfortable yet, `git merge origin/main` into your branch works too — just be consistent about which one you use.

---

## Linking commits to Issues

If there's a GitHub Issue for the task, reference it in the PR description: `Closes #12`. This auto-closes the issue on merge and gives a visible, timestamped record of who built what — useful reference if contribution is ever questioned during the defense.

---

## Secrets

Never commit `local.properties`, API keys, or Supabase credentials. If a diff you're about to commit includes any of these, stop and flag it instead of proceeding.