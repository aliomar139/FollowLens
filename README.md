# FollowLens

**Know who unfollowed you on Instagram, and when.**

FollowLens scans your followers and following lists on a schedule, diffs each scan
against the last, and keeps an append-only log of every change. No server, no
account, no third party: the app talks to Instagram's web endpoints directly and
stores everything on the device.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square">
  <img alt="Language" src="https://img.shields.io/badge/language-Java%2017-orange?style=flat-square">
  <img alt="Build" src="https://img.shields.io/badge/build-Gradle%208.9-02303A?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue?style=flat-square">
</p>

> [!WARNING]
> For personal and educational use. Automating access to Instagram may violate its
> Terms of Service and can result in rate limiting or account restrictions. The app
> can only read what your own session can already see. Use it at your own risk.

---

## Features

|  | |
|---|---|
| **Unfollow detection** | A notification the moment a scan finds someone gone, once per scan rather than once per account. |
| **Append-only history** | Every follow and unfollow is recorded as an event, so history survives future scans instead of being overwritten. |
| **Five list views** | Followers, following, mutuals, and both non-reciprocal edges, each searchable and filterable. |
| **Scan timeline** | Changes grouped by the scan that found them, with a sparkline of follower count over time. |
| **Runs unattended** | A daily background scan through WorkManager. Pull to refresh for an immediate one. |
| **Local only** | Room database on the device. The session key is held in encrypted storage and sent to nobody but Instagram. |

## How it works

A scan is all-or-nothing. Both lists are paged fully into memory, then committed
inside a single Room transaction, so an interrupted or rate-limited scan writes
nothing and cannot leave a half-updated graph that would read as mass unfollows.

Each committed scan is diffed against the previous one. The first scan is marked
as a baseline and reports no changes, since everyone would otherwise appear as a
new follower.

## Getting started

### 1. Provide a session

Paste your `sessionid` cookie into the first screen. No password is entered.
Reusing a session your browser already established avoids Instagram's new-device
login check. The field accepts the bare cookie value, a `sessionid=...` pair, or an
entire cookie header, whichever you happened to copy.

### 2. Let it scan

The first scan establishes the baseline. From then on the app scans once a day in
the background and notifies you when someone unfollows. A manual refresh is subject
to a 10 minute cooldown to stay well inside Instagram's rate limits.

## Building

**Requirements**

| | |
|---|---|
| JDK | 17 or newer. Java 17 is the source and target level; the suite is verified on 21 and 25. |
| Android SDK | `compileSdk` / `targetSdk` 35, `minSdk` 26 (Android 8.0) |
| Gradle | 8.9 via the included wrapper, with Android Gradle Plugin 8.7.3 |

Point the build at your SDK by creating `local.properties` in the repository root.
The file is git-ignored because the path is specific to your machine:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

Gradle runs on whichever JDK `JAVA_HOME` names, or the first `java` on your `PATH`.
To select a different one for this project only, set it in `gradle.properties`:

```properties
org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
```

> [!NOTE]
> `org.gradle.java.home` has no effect in `local.properties`. Android Studio reads
> that file, but Gradle itself does not; on the command line the setting is silently
> ignored.
>
> Gradle 8.9 officially supports up to Java 22. Newer JDKs build and test cleanly
> here, but they are outside the range Gradle guarantees, so a JDK in the 17 to 22
> window is the safer choice for a reproducible build.

**Tasks**

```bash
./gradlew :app:installDebug          # build and install on a connected device
./gradlew :app:testDebugUnitTest     # unit tests
./gradlew :app:assembleRelease       # unsigned release APK
```

## Testing

110 unit tests run on the JVM with no device or emulator. Robolectric provides the
Android runtime for the Room and scheduling tests, and MockWebServer stands in for
Instagram so the network layer is tested against real HTTP without touching the
live API.

```bash
./gradlew :app:testDebugUnitTest
```

## Architecture

Layered so that each package has one reason to change. The HTTP client knows
nothing about Android or Room, the persistence layer knows nothing about HTTP, and
`scan` is the only package that touches both.

```
app/src/main/java/com/kira/followlens/
├─ auth/     session key parsing and encrypted storage
├─ net/      Instagram web client, rate limit and throttle handling
├─ data/     Room entities, DAO, repository, and graph diffing
├─ scan/     scan orchestration, scheduling, and status
├─ notify/   unfollow notifications, once per scan
└─ ui/       dashboard, change history, list views, and motion
icons/       launcher icon sources used to generate the mipmaps
docs/        the design spec and implementation plan the app was built from
```

**Stack.** OkHttp and Gson for the network layer, Room for persistence,
WorkManager for scheduling, AndroidX DynamicAnimation for spring-based motion.
Pure Java throughout, with no Kotlin on the classpath.

## Distribution

Sideloaded APK. An app whose purpose is automating Instagram access with a session
cookie is unlikely to be accepted on Google Play.

## License

[MIT](LICENSE) © aliomar139
