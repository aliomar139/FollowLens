# FollowLens for Android — Design

Date: 2026-08-05
Status: approved

## Goal

A standalone, pure-Java Android app that tracks an Instagram account's follower
graph: it captures the followers and following lists, diffs each scan against the
previous one, shows the results, and notifies the user when someone unfollows.

The app is self-contained. It does not talk to the Python backend in this repo,
and the desktop dashboard keeps working unchanged.

## Why Android is not just a port

Two things the desktop version cannot do:

1. **Cookie capture without DevTools.** The user logs in to Instagram in a
   `WebView` and the app reads the `sessionid` cookie via `CookieManager`. This
   replaces the manual copy-paste in the README. Because the session cookie
   already encodes the user id (`sessionid.split(":")[0]`, see
   `backend/igweb.py:56`), the app never needs a username or a profile lookup.
2. **Notifications.** An unfollow dashboard you have to remember to open is a
   worse product than a notification. This is the main reason to be on a phone.

## Scope

### In scope for v1

- WebView login and `sessionid` capture, stored encrypted
- Scan of the signed-in user's own followers and following lists
- Snapshot diffing with an append-only change log
- Dashboard: follower/following counts, mutuals, one-way follows both directions
- Change history grouped by scan
- A single summary notification per scan with changes
- Scheduled background scanning plus manual refresh

### Explicitly out of scope for v1

- Multiple tracked accounts (own account only)
- Account comparison / Venn overlap
- JSON export
- Username search and change-feed filters
- Google Play distribution (see Distribution)
- Any Kotlin (enforced by omitting the Kotlin Gradle plugin entirely)

## Architecture

`android/` is added alongside `backend/` and `frontend/` in this repo, as a
standalone Gradle project with a single `:app` module.

Packages, each with one responsibility:

| Package | Responsibility | Depends on |
| --- | --- | --- |
| `net` | Instagram web client: pagination, retry, header spoofing. Port of `igweb.py`. | OkHttp, Gson |
| `auth` | WebView login, cookie extraction, encrypted session storage, user-id derivation | AndroidX Security |
| `data` | Room database, entities, DAOs, and the repository that commits a scan | Room |
| `scan` | Scan orchestration and cooldown. Port of `scanner.py`. | `net`, `data`, `notify` |
| `notify` | Notification channel and the post-scan summary notification | — |
| `ui` | Activities, `RecyclerView` adapters, `ViewModel`s | `data` |

`net` knows nothing about Room or Android; it is a plain Java HTTP client
testable on the JVM. `data` knows nothing about HTTP. `scan` is the only package
that touches both.

### Build configuration

- `minSdk 26` — `NotificationChannel` requires API 26, which sets the floor
- `compileSdk` / `targetSdk 35`, not 36. Both platforms are installed locally,
  but 35 pairs with a conservative, known-good AGP version; bumping to 36 later
  is a two-line change once the build is proven.
- Java 17 source/target via the Android Gradle Plugin
- No Kotlin plugin
- Dependencies: OkHttp, Gson, Room, WorkManager, AndroidX Security
  (`EncryptedSharedPreferences`), Material Components, JUnit 4, MockWebServer,
  Robolectric

The local default JDK is 25, which is newer than Gradle 8.12 and AGP support.
The build therefore pins the Gradle wrapper and runs on Android Studio's bundled
JDK, verified present at `C:\Program Files\Android\Android Studio\jbr`
(OpenJDK 21.0.10), set via `org.gradle.java.home` in a git-ignored
`local.properties`.

The first implementation task is assembling an empty app to confirm this, before
any logic is written.

## Data model

Room, version 1. Four tables.

**`account`** — one row in v1.
`id` (TEXT PK, Instagram user id), `username`, `added_at`.

**`scan`** — one row per completed scan. Failed scans are never inserted.
`id` (INTEGER PK autogenerate), `account_id`, `started_at`, `finished_at`,
`followers_count`, `following_count`, `is_baseline`.

**`edge`** — current graph state, and only current state.
Composite PK (`account_id`, `kind`, `user_id`) where `kind` is `FOLLOWER` or
`FOLLOWING`. Plus `username`, `first_seen_scan_id`, `last_seen_scan_id`.

An account that disappears from a list has its `edge` row **deleted**, with the
removal recorded in `change_event`. So `edge` always answers "who is in this list
right now" with no filtering, and history lives entirely in the event log.

**`change_event`** — append-only log.
`id` (INTEGER PK), `account_id`, `scan_id`, `kind`, `direction` (`ADDED` /
`REMOVED`), `user_id`, `username`, `occurred_at`.

`kind` and `direction` are stored as TEXT via a Room `TypeConverter` over Java
enums, so the tables stay readable when inspected directly.

### Why events rather than stored snapshots

The desktop version keeps every snapshot as a file and diffs adjacent pairs. On
a phone that scales badly: a 5000-follower account is roughly 200 KB per
snapshot, two lists per scan, so frequent scanning reaches gigabytes within a
year.

Storing current state plus deltas is compact, and it is also more reliable —
desktop history is only as complete as whichever snapshot files still exist on
disk, whereas an append-only log has no such dependency.

### Derived views

Computed by SQL against `edge`, not stored:

- **Mutuals** — `user_id` present as both `FOLLOWER` and `FOLLOWING`
- **Not following back** — `FOLLOWING` minus `FOLLOWER`
- **Fans** — `FOLLOWER` minus `FOLLOWING`

## Data flow

1. `ScanWorker` (a WorkManager `Worker`; `doWork()` already runs off the main
   thread, so no coroutines and no `ListenableFuture` plumbing) wakes up.
2. Cooldown check against `last_scan_at` in preferences. Skipped when the user
   pulled to refresh with force.
3. Read the session from `EncryptedSharedPreferences`. If it is missing, the
   worker stops and posts a notification that opens the login screen — a
   background worker cannot show UI itself.
4. `IgWebClient.following(uid)` then `IgWebClient.followers(uid)`, both fully
   into memory.
5. Diff against `edge` and commit — see the transaction rule below.
6. Post one summary notification if the scan was not a baseline and something
   changed.
7. UI updates on its own; `RecyclerView`s observe `LiveData` from the DAOs.

### The transaction rule

`backend/igweb.py:28-35` aborts pagination when a page fails, because a
truncated list is indistinguishable from a mass unfollow. Mobile networks make
mid-scan failures routine rather than rare, so this rule gets stricter here:

**A scan either commits completely or writes nothing.** Both lists are fetched
in full before the database is touched, and the commit — the `scan` row, the
`edge` updates, and the `change_event` inserts — happens inside one Room
`@Transaction`. A partial scan writes no rows and posts no notification.

This is the one bug in this app that would actively lie to the user, so it is
pinned by a test that injects a failure mid-pagination and asserts the database
is byte-for-byte unchanged.

### Behaviour carried over deliberately

- **Followers multi-pass union** (`igweb.py:130-152`). The followers endpoint
  paginates inconsistently, so repeated full passes are unioned until a pass
  adds nothing, capped at three. This compensates for a real Instagram quirk.
- **Randomised inter-request delay** (`delay` + `jitter`), and the three-attempt
  429 backoff.
- **First scan is a baseline**: `edge` rows are written, no `change_event` rows,
  no notification.

Sleeping is behind a `Sleeper` interface injected into `IgWebClient`, so tests
run instantly instead of waiting out real delays.

## Notifications

One notification per scan summarising the delta (`3 new followers, 1
unfollowed`), tapping through to the change history. Never one per changed user —
a 40-account swing must not produce 40 notifications.

`POST_NOTIFICATIONS` is requested on API 33+ after the first successful scan,
not at launch, so the prompt arrives with visible context.

## Scheduling

A WorkManager `PeriodicWorkRequest` with a `NetworkType.CONNECTED` constraint,
on a **15-minute interval** — the platform minimum
(`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`), and as close to continuous
as WorkManager allows. There is no settings screen in v1, so the interval is a
constant; making it user-configurable is a later, additive change.

15 minutes exceeds the desktop's 600-second cooldown, so periodic runs never trip
the cooldown gate — that gate exists to throttle manual refreshes. The cooldown
is likewise a constant (600 seconds) in v1. Doze may delay execution, which is
acceptable for this workload.

**Request volume.** 15-minute scanning is roughly 96 scans per day, each a full
paginated walk of both lists plus up to three follower passes. This is the usage
profile most likely to attract rate limiting, and the 429 handling exists
precisely because of it. If Instagram starts returning 429 persistently, the
first mitigation is lengthening this interval, not adding more retries.

## Error handling

| Condition | Behaviour |
| --- | --- |
| No session stored | Notification that opens the login screen; app launch routes there directly |
| HTTP 401 | Clear the stored cookie, notify that re-login is needed |
| HTTP 429 after retries | `Result.retry()` with backoff; surface a rate-limit message |
| Non-200 mid-pagination | Abort, write nothing, `Result.retry()` |
| Network unavailable | WorkManager constraint defers the run |
| Cooldown active | Report remaining seconds, no request issued |

## Testing

Everything runs on the JVM. No emulator is required, which matters because no
AVD is currently configured on this machine.

- **`net`** — JUnit 4 with OkHttp `MockWebServer`: multi-page pagination,
  `next_max_id` termination, the followers union across passes, the 429
  retry/backoff path, 401 mapping, abort-on-non-200, and `sessionid` parsing.
- **`data`** — Robolectric with an in-memory Room database: diff correctness,
  baseline handling, derived-view queries, and the all-or-nothing transaction.
- **`scan`** — the orchestration path against a fake client and a real in-memory
  database, including the cooldown gate.

Implementation follows TDD: tests for each unit land before its implementation.

## CI

A new `.github/workflows/android.yml` runs `./gradlew :app:testDebugUnitTest`
under JDK 21. The existing Python workflow gets a `paths` filter so it no longer
runs on Android-only changes, and vice versa.

## Distribution

An app whose purpose is automating Instagram access with a lifted session cookie
is very likely to be rejected from Google Play under the unauthorized-access and
deceptive-behaviour policies, independent of the Instagram Terms of Service
caveat the README already carries. Distribution is therefore sideloaded APKs
from GitHub Releases. The README's existing disclaimer applies unchanged.

## Security

The `sessionid` is a full account session. It is stored in
`EncryptedSharedPreferences`, never in plain `SharedPreferences`, never logged,
and excluded from Android auto-backup via `android:allowBackup="false"`.

## Risks

1. **Endpoint parity.** Whether Instagram's endpoints behave identically for
   OkHttp as for `requests` given the same headers is unproven. Mitigated by
   making the `IgWebClient` port the first real task, verified against a live
   session early.
2. **Cookie extraction.** `CookieManager.getCookie()` returns all cookies as one
   string and requires the `WebView` to have accepted them; parsing and login
   flow need verification on a real device.
3. **Toolchain.** JDK 25 versus Gradle 8.12 and AGP, addressed by pinning the
   wrapper and building an empty app first.
4. **Session longevity.** Instagram may invalidate long-lived sessions, forcing
   periodic re-login. Handled as the 401 path; frequency is unknown.
