# FollowLens for Android

An Android app that tracks who follows you on Instagram and tells you when someone
unfollows. Pure Java, no server: the app talks to Instagram's web endpoints
directly, stores every scan locally in Room, and keeps an append-only log of
changes so history survives across scans.

> **Disclaimer.** This project is intended for personal and educational use.
> Automating access to Instagram may violate its Terms of Service and can lead to
> rate-limiting or account restrictions. You can only read accounts your own
> session can already access. Use responsibly and at your own risk.

## Setup

Paste your `sessionid` cookie into the app's first screen. No password is entered,
and reusing a session your browser already established avoids Instagram's
new-device login check. The field accepts the bare cookie value, a `sessionid=...`
pair, or the whole cookie header — whatever you happened to copy.

The app scans once a day in the background and notifies you when someone
unfollows. Refresh runs one on demand whenever you want the current picture.

## Building

The build needs **JDK 21** — the Gradle setup here does not accept a newer JDK.
Create `local.properties` (git-ignored) pointing at your SDK and at the JDK
bundled with Android Studio:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
```

Then:

```bash
./gradlew :app:installDebug          # build and install on a connected device
./gradlew :app:testDebugUnitTest     # unit tests (JUnit, Robolectric, MockWebServer)
```

Distribution is by sideloaded APK. An app whose purpose is automating Instagram
access with a session cookie is unlikely to be accepted on Google Play.

- `compileSdk` / `targetSdk` 35, `minSdk` 26, Java 17 source level
- OkHttp + Gson for the network layer, Room for storage, WorkManager for scheduling

## Project structure

```
app/src/main/java/com/kira/followlens/
├─ auth/     # session key parsing and encrypted storage
├─ net/      # Instagram web client, rate-limit and throttle handling
├─ data/     # Room entities, DAO, repository, and graph diffing
├─ scan/     # scan orchestration, scheduling, and status
├─ notify/   # unfollow notifications, once per scan
└─ ui/       # dashboard, change history, list views, and motion
icons/       # launcher icon sources used to generate the mipmaps
```

## Credits

The Instagram web client here is a Java port of the one from `follow-lens`, an
earlier Python/Flask project of mine. The Android app is standalone — it does not
talk to that server.

## License

Released under the [MIT License](LICENSE). © aliomar139
