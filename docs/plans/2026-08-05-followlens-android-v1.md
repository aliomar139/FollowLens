# FollowLens Android v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A pure-Java Android app that scans the signed-in Instagram account's followers and following lists on a 15-minute schedule, diffs each scan against the last, and notifies the user when someone unfollows.

**Architecture:** A single Gradle `:app` module under `android/`, layered so the HTTP client (`net`) knows nothing about Android or Room, the persistence layer (`data`) knows nothing about HTTP, and `scan` is the only package that touches both. Scans are all-or-nothing: both lists are fetched fully into memory, then committed inside one Room transaction.

**Tech Stack:** Java 17, Android Gradle Plugin, OkHttp, Gson, Room, WorkManager, AndroidX Security (`EncryptedSharedPreferences`), Material Components. Tests: JUnit 4, OkHttp MockWebServer, Robolectric.

**Spec:** `docs/specs/2026-08-05-followlens-android-design.md`

## Global Constraints

- **No Kotlin.** The Kotlin Gradle plugin is never added to any build file. Every source file is `.java`.
- `minSdk 26`, `compileSdk 35`, `targetSdk 35`.
- Java 17 `sourceCompatibility` and `targetCompatibility`.
- All Gradle commands run from the `android/` directory using `./gradlew`.
- Gradle runs on JDK 21 at `C:\Program Files\Android\Android Studio\jbr`, set via `org.gradle.java.home` in `android/local.properties`. The machine default JDK is 25 and is too new for this AGP.
- Application ID and Java package root: `com.kira.followlens`.
- The `sessionid` is a full account session. Never log it, never write it to plain `SharedPreferences`, never include it in a crash report.
- Every test runs on the JVM. No test requires an emulator or a connected device — no AVD is configured on this machine.
- Instagram request headers must match `backend/igweb.py:59-68` exactly. `x-ig-app-id` is `936619743392459`.
- Scan interval: 15 minutes (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`). Manual-refresh cooldown: 600 seconds. Follower passes: 3. Base delay 3.0s, jitter 2.5s.

---

### Task 1: Gradle scaffold that assembles

Nothing else can be verified until an empty app builds on this machine with this JDK. This task deliberately contains no application logic.

**Files:**
- Create: `android/settings.gradle`
- Create: `android/build.gradle`
- Create: `android/gradle.properties`
- Create: `android/local.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/app/build.gradle`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/test/java/com/kira/followlens/SanityTest.java`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable `:app` module. Every later task adds sources under `android/app/src/main/java/com/kira/followlens/` and tests under `android/app/src/test/java/com/kira/followlens/`.

- [ ] **Step 1: Generate the Gradle wrapper**

From the repo root:

```bash
mkdir -p android
cd android
gradle wrapper --gradle-version 8.9
```

This uses the system Gradle 8.12 once, only to write the wrapper. Every later command uses `./gradlew`, which is Gradle 8.9.

- [ ] **Step 2: Write the build files**

`android/settings.gradle`:

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = 'FollowLens'
include ':app'
```

`android/build.gradle`:

```groovy
plugins {
    id 'com.android.application' version '8.7.3' apply false
}
```

`android/gradle.properties`:

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
```

`android/local.properties` (machine-specific, git-ignored):

```properties
sdk.dir=C\:\\Users\\admin\\AppData\\Local\\Android\\Sdk
org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
```

`android/app/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.kira.followlens'
    compileSdk 35

    defaultConfig {
        applicationId 'com.kira.followlens'
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName '0.1.0'
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 3: Write the manifest and strings**

`android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true" />

</manifest>
```

`android/app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">FollowLens</string>
</resources>
```

- [ ] **Step 4: Write a sanity test**

`android/app/src/test/java/com/kira/followlens/SanityTest.java`:

```java
package com.kira.followlens;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SanityTest {

    @Test
    public void javaVersionIsAtLeast17() {
        int major = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]);
        assertEquals(21, major);
    }
}
```

- [ ] **Step 5: Run the build and the test**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, and `SanityTest` passes.

If AGP complains that Gradle 8.9 is too old, bump `gradle-wrapper.properties` to `8.11.1`. If it complains about `compileSdk 35`, the installed platform is missing — install it with `sdkmanager "platforms;android-35"`. Do not "fix" a JDK error by removing `org.gradle.java.home`; that reverts to JDK 25 and fails differently.

- [ ] **Step 6: Ignore machine-specific and build output**

Append to `.gitignore`:

```gitignore
# Android
android/local.properties
android/.gradle/
android/build/
android/app/build/
*.apk
*.keystore
```

- [ ] **Step 7: Commit**

```bash
git add android .gitignore
git commit -m "build(android): scaffold pure-Java Gradle project

Empty :app module that assembles on JDK 21 from Android Studio's bundled
JBR, since the machine default JDK 25 is too new for AGP 8.7."
```

---

### Task 2: Instagram web client

The port of `backend/igweb.py`. This is the riskiest task — whether the endpoints behave identically for OkHttp as for Python `requests` is unproven — so it comes before any UI or storage work. Plain Java, no Android APIs, fully testable on the JVM.

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/net/Sleeper.java`
- Create: `android/app/src/main/java/com/kira/followlens/net/IgException.java`
- Create: `android/app/src/main/java/com/kira/followlens/net/IgWebClient.java`
- Create: `android/app/src/main/java/com/kira/followlens/net/SessionId.java`
- Test: `android/app/src/test/java/com/kira/followlens/net/SessionIdTest.java`
- Test: `android/app/src/test/java/com/kira/followlens/net/IgWebClientTest.java`
- Modify: `android/app/build.gradle`

**Interfaces:**
- Consumes: the buildable module from Task 1.
- Produces:
  - `interface Sleeper { void sleep(long millis); }` with `Sleeper.REAL` and `Sleeper.NONE` constants.
  - `SessionId.userIdOf(String sessionId)` returns `String`.
  - `IgWebClient(okhttp3.HttpUrl baseUrl, String sessionId, Sleeper sleeper, double delaySeconds, double jitterSeconds)`.
  - `IgWebClient.following(String uid)` returns `Map<String, String>` of user id to username.
  - `IgWebClient.followers(String uid, int maxPasses)` returns `Map<String, String>`.
  - `IgException.RateLimited`, `IgException.Fetch`, `IgException.SessionExpired`, all extending `IgException extends Exception`.

- [ ] **Step 1: Add dependencies**

In `android/app/build.gradle`, replace the `dependencies` block:

```groovy
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.11.0'

    testImplementation 'junit:junit:4.13.2'
    testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
}
```

- [ ] **Step 2: Write the failing session-id test**

`SessionIdTest.java`:

```java
package com.kira.followlens.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SessionIdTest {

    @Test
    public void extractsUserIdBeforeFirstColon() {
        assertEquals("12345", SessionId.userIdOf("12345:abcdefgh:99"));
    }

    @Test
    public void urlDecodesBeforeSplitting() {
        assertEquals("12345", SessionId.userIdOf("12345%3Aabcdefgh%3A99"));
    }

    @Test
    public void rejectsEmptySession() {
        assertThrows(IllegalArgumentException.class, () -> SessionId.userIdOf(""));
    }

    @Test
    public void rejectsSessionWithoutUserId() {
        assertThrows(IllegalArgumentException.class, () -> SessionId.userIdOf(":abc"));
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*SessionIdTest'
```

Expected: compile failure, `cannot find symbol class SessionId`.

- [ ] **Step 4: Implement `SessionId` and `Sleeper`**

`SessionId.java`:

```java
package com.kira.followlens.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/** Derives the Instagram user id that is embedded in a sessionid cookie. */
public final class SessionId {

    private SessionId() {
    }

    /**
     * A sessionid looks like "12345:abcdef:99" (sometimes percent-encoded).
     * The leading segment is the account's own user id, which is why this app
     * never needs to ask for a username.
     */
    public static String userIdOf(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionid is empty");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(sessionId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 always available", e);
        }
        String userId = decoded.split(":")[0];
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("sessionid has no leading user id");
        }
        return userId;
    }
}
```

`Sleeper.java`:

```java
package com.kira.followlens.net;

/** Indirection over Thread.sleep so tests do not wait out real request delays. */
public interface Sleeper {

    Sleeper REAL = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    };

    Sleeper NONE = millis -> {
    };

    void sleep(long millis);
}
```

- [ ] **Step 5: Run the session-id test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*SessionIdTest'
```

Expected: 4 tests pass.

- [ ] **Step 6: Write the failing client tests**

`IgWebClientTest.java`:

```java
package com.kira.followlens.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IgWebClientTest {

    private MockWebServer server;
    private IgWebClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new IgWebClient(server.url("/"), "12345:secret:99", Sleeper.NONE, 0, 0);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static MockResponse page(String nextMaxId, String... idUsernamePairs) {
        StringBuilder users = new StringBuilder();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            if (users.length() > 0) {
                users.append(',');
            }
            users.append("{\"pk\":\"").append(idUsernamePairs[i])
                    .append("\",\"username\":\"").append(idUsernamePairs[i + 1]).append("\"}");
        }
        String next = nextMaxId == null ? "null" : "\"" + nextMaxId + "\"";
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"users\":[" + users + "],\"next_max_id\":" + next + "}");
    }

    @Test
    public void followingFollowsPaginationUntilNextMaxIdIsNull() throws Exception {
        server.enqueue(page("cursor1", "1", "alice"));
        server.enqueue(page(null, "2", "bob"));

        Map<String, String> result = client.following("999");

        assertEquals(2, result.size());
        assertEquals("alice", result.get("1"));
        assertEquals("bob", result.get("2"));

        RecordedRequest first = server.takeRequest();
        assertTrue(first.getPath().contains("/api/v1/friendships/999/following/"));
        assertTrue(server.takeRequest().getPath().contains("max_id=cursor1"));
    }

    @Test
    public void sendsInstagramHeadersAndSessionCookie() throws Exception {
        server.enqueue(page(null, "1", "alice"));

        client.following("999");

        RecordedRequest request = server.takeRequest();
        assertEquals("936619743392459", request.getHeader("x-ig-app-id"));
        assertEquals("XMLHttpRequest", request.getHeader("x-requested-with"));
        assertTrue(request.getHeader("user-agent").contains("Mozilla/5.0"));
        assertTrue(request.getHeader("cookie").contains("sessionid=12345:secret:99"));
        assertTrue(request.getHeader("cookie").contains("ds_user_id=12345"));
    }

    @Test
    public void followersUnionsRepeatedPassesAndStopsWhenAPassAddsNothing() throws Exception {
        // Pass 1 sees alice, pass 2 additionally sees bob, pass 3 adds nothing.
        server.enqueue(page(null, "1", "alice"));
        server.enqueue(page(null, "1", "alice", "2", "bob"));
        server.enqueue(page(null, "1", "alice", "2", "bob"));

        Map<String, String> result = client.followers("999", 3);

        assertEquals(2, result.size());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void followersStopsEarlyWhenSecondPassAddsNothing() throws Exception {
        server.enqueue(page(null, "1", "alice"));
        server.enqueue(page(null, "1", "alice"));

        Map<String, String> result = client.followers("999", 3);

        assertEquals(1, result.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void retriesOnceAfter429ThenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(page(null, "1", "alice"));

        Map<String, String> result = client.following("999");

        assertEquals(1, result.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void throwsRateLimitedAfterThreeConsecutive429s() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(IgException.RateLimited.class, () -> client.following("999"));
    }

    @Test
    public void throwsSessionExpiredOn401() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(IgException.SessionExpired.class, () -> client.following("999"));
    }

    @Test
    public void abortsOnNon200SoAPartialListIsNeverReturned() {
        server.enqueue(page("cursor1", "1", "alice"));
        server.enqueue(new MockResponse().setResponseCode(500));

        // The first page succeeded, but a truncated list is indistinguishable
        // from a mass unfollow, so the whole walk must fail.
        assertThrows(IgException.Fetch.class, () -> client.following("999"));
    }
}
```

- [ ] **Step 7: Run the client tests and confirm they fail**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*IgWebClientTest'
```

Expected: compile failure, `cannot find symbol class IgWebClient`.

- [ ] **Step 8: Implement `IgException`**

`IgException.java`:

```java
package com.kira.followlens.net;

/** Failures that callers must distinguish, because each has a different remedy. */
public class IgException extends Exception {

    public IgException(String message) {
        super(message);
    }

    /** Instagram kept returning HTTP 429 after retries. Back off and try later. */
    public static class RateLimited extends IgException {
        public RateLimited(String message) {
            super(message);
        }
    }

    /**
     * A friendship page returned an unexpected status. Pagination fails loudly
     * on purpose: a silently skipped page truncates the list, and a truncated
     * list looks exactly like a batch of accounts that unfollowed.
     */
    public static class Fetch extends IgException {
        public Fetch(String message) {
            super(message);
        }
    }

    /** The session cookie is no longer valid. The user must log in again. */
    public static class SessionExpired extends IgException {
        public SessionExpired(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 9: Implement `IgWebClient`**

`IgWebClient.java`:

```java
package com.kira.followlens.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Minimal Instagram web client backed by a session cookie. Port of
 * backend/igweb.py: same endpoints, same headers, same retry and pagination
 * behaviour.
 */
public class IgWebClient {

    public static final String APP_ID = "936619743392459";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final int MAX_PAGES_PER_PASS = 40;
    private static final int FOLLOWING_PAGE_SIZE = 200;
    private static final int FOLLOWERS_PAGE_SIZE = 100;

    private final HttpUrl baseUrl;
    private final String cookieHeader;
    private final Sleeper sleeper;
    private final double delaySeconds;
    private final double jitterSeconds;
    private final OkHttpClient http;
    private final Random random = new Random();
    private final Gson gson = new Gson();

    /**
     * @param baseUrl       normally https://www.instagram.com/, injectable so
     *                      tests can point at a MockWebServer
     * @param sessionId     the sessionid cookie value from a logged-in browser
     * @param sleeper       Sleeper.REAL in production, Sleeper.NONE in tests
     * @param delaySeconds  base delay between paginated requests
     * @param jitterSeconds extra random delay added on top of delaySeconds
     */
    public IgWebClient(HttpUrl baseUrl, String sessionId, Sleeper sleeper,
                       double delaySeconds, double jitterSeconds) {
        this.baseUrl = baseUrl;
        this.sleeper = sleeper;
        this.delaySeconds = delaySeconds;
        this.jitterSeconds = jitterSeconds;
        this.cookieHeader = "sessionid=" + sessionId
                + "; ds_user_id=" + SessionId.userIdOf(sessionId);
        this.http = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Returns the full following list as user id to username. */
    public Map<String, String> following(String uid) throws IgException, IOException {
        Map<String, String> out = new LinkedHashMap<>();
        walkOnce(uid, "following", FOLLOWING_PAGE_SIZE, out);
        return out;
    }

    /**
     * Returns the full followers list as user id to username.
     *
     * The followers endpoint paginates inconsistently, so this runs repeated
     * full passes and unions the results, stopping early once a pass adds no
     * new accounts.
     */
    public Map<String, String> followers(String uid, int maxPasses)
            throws IgException, IOException {
        Map<String, String> out = new LinkedHashMap<>();
        int previousSize = -1;
        for (int pass = 0; pass < maxPasses; pass++) {
            walkOnce(uid, "followers", FOLLOWERS_PAGE_SIZE, out);
            if (out.size() == previousSize) {
                break;
            }
            previousSize = out.size();
            pause();
        }
        return out;
    }

    /** Walks every page of one list once, merging into {@code out}. */
    private void walkOnce(String uid, String kind, int pageSize, Map<String, String> out)
            throws IgException, IOException {
        String maxId = null;
        for (int i = 0; i < MAX_PAGES_PER_PASS; i++) {
            HttpUrl.Builder url = baseUrl.newBuilder()
                    .addPathSegments("api/v1/friendships/" + uid + "/" + kind + "/")
                    .addQueryParameter("count", String.valueOf(pageSize));
            if (maxId != null) {
                url.addQueryParameter("max_id", maxId);
            }

            JsonObject body;
            try (Response response = get(url.build())) {
                if (response.code() != 200) {
                    throw new IgException.Fetch(kind + " page for " + uid + " returned HTTP "
                            + response.code()
                            + "; aborting so a partial list is not stored as a full one.");
                }
                body = gson.fromJson(response.body().string(), JsonObject.class);
            }

            JsonArray users = body.getAsJsonArray("users");
            if (users != null) {
                for (JsonElement element : users) {
                    JsonObject user = element.getAsJsonObject();
                    out.put(user.get("pk").getAsString(), user.get("username").getAsString());
                }
            }

            JsonElement next = body.get("next_max_id");
            if (next == null || next.isJsonNull()) {
                return;
            }
            maxId = next.getAsString();
            pause();
        }
    }

    /** GET with up to two backoff retries on HTTP 429. */
    private Response get(HttpUrl url) throws IgException, IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            Response response = http.newCall(new Request.Builder()
                    .url(url)
                    .header("x-ig-app-id", APP_ID)
                    .header("x-asbd-id", "129477")
                    .header("x-ig-www-claim", "0")
                    .header("user-agent", USER_AGENT)
                    .header("x-requested-with", "XMLHttpRequest")
                    .header("accept", "*/*")
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("referer", "https://www.instagram.com/")
                    .header("cookie", cookieHeader)
                    .build()).execute();

            if (response.code() == 429) {
                response.close();
                if (attempt == 2) {
                    throw new IgException.RateLimited(
                            "Instagram returned 429 (too many requests). Wait a bit and retry.");
                }
                sleeper.sleep((long) (15_000L * (attempt + 1) + random.nextDouble() * 8_000));
                continue;
            }
            if (response.code() == 401) {
                response.close();
                throw new IgException.SessionExpired(
                        "Session invalid or expired (401). Log in again.");
            }
            return response;
        }
        throw new IllegalStateException("unreachable");
    }

    private void pause() {
        sleeper.sleep((long) ((delaySeconds + random.nextDouble() * jitterSeconds) * 1000));
    }
}
```

- [ ] **Step 10: Run all `net` tests and confirm they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*net*'
```

Expected: 12 tests pass (4 `SessionIdTest`, 8 `IgWebClientTest`).

- [ ] **Step 11: Commit**

```bash
git add android/app/build.gradle android/app/src/main/java/com/kira/followlens/net android/app/src/test/java/com/kira/followlens/net
git commit -m "feat(android): port the Instagram web client to Java

Mirrors backend/igweb.py including the followers multi-pass union and the
abort-on-non-200 rule that stops a truncated list being mistaken for a
mass unfollow. Base URL and Sleeper are injected so MockWebServer tests
run without real delays."
```

---

### Task 3: Room storage, diffing, and the all-or-nothing commit

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/data/ListKind.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/ChangeDirection.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/Converters.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/AccountEntity.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/ScanEntity.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/EdgeEntity.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/ChangeEventEntity.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/FollowLensDao.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/FollowLensDatabase.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/GraphDiff.java`
- Create: `android/app/src/main/java/com/kira/followlens/data/ScanRepository.java`
- Test: `android/app/src/test/java/com/kira/followlens/data/GraphDiffTest.java`
- Test: `android/app/src/test/java/com/kira/followlens/data/ScanRepositoryTest.java`
- Modify: `android/app/build.gradle`

**Interfaces:**
- Consumes: nothing from Task 2. `data` never imports `net`.
- Produces:
  - `enum ListKind { FOLLOWER, FOLLOWING }`, `enum ChangeDirection { ADDED, REMOVED }`.
  - `GraphDiff.of(Set<String> previousIds, Set<String> currentIds)` returns `GraphDiff` with `Set<String> added()` and `Set<String> removed()`.
  - `ScanRepository(FollowLensDatabase db)` with:
    - `void ensureAccount(String accountId, String username)`
    - `long commitScan(String accountId, Map<String,String> followers, Map<String,String> following, long startedAt, long finishedAt)` returning the new scan id
    - `boolean hasAnyScan(String accountId)`
    - `List<ChangeEventEntity> changesForScan(long scanId)`
  - `FollowLensDatabase.inMemory(Context)` static factory for tests.

- [ ] **Step 1: Add Room and Robolectric**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.room:room-runtime:2.6.1'
    annotationProcessor 'androidx.room:room-compiler:2.6.1'

    testImplementation 'org.robolectric:robolectric:4.13'
    testImplementation 'androidx.test:core:1.6.1'
```

- [ ] **Step 2: Write the failing diff test**

`GraphDiffTest.java`:

```java
package com.kira.followlens.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class GraphDiffTest {

    private static Set<String> ids(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void reportsAddedAndRemoved() {
        GraphDiff diff = GraphDiff.of(ids("1", "2"), ids("2", "3"));

        assertEquals(ids("3"), diff.added());
        assertEquals(ids("1"), diff.removed());
    }

    @Test
    public void reportsNothingWhenUnchanged() {
        GraphDiff diff = GraphDiff.of(ids("1", "2"), ids("2", "1"));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    public void treatsEmptyPreviousAsEverythingAdded() {
        GraphDiff diff = GraphDiff.of(Collections.emptySet(), ids("1", "2"));

        assertEquals(ids("1", "2"), diff.added());
        assertTrue(diff.removed().isEmpty());
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*GraphDiffTest'
```

Expected: compile failure, `cannot find symbol class GraphDiff`.

- [ ] **Step 4: Implement `GraphDiff`**

`GraphDiff.java`:

```java
package com.kira.followlens.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The delta between a stored list and a freshly fetched one. */
public final class GraphDiff {

    private final Set<String> added;
    private final Set<String> removed;

    private GraphDiff(Set<String> added, Set<String> removed) {
        this.added = Collections.unmodifiableSet(added);
        this.removed = Collections.unmodifiableSet(removed);
    }

    public static GraphDiff of(Set<String> previousIds, Set<String> currentIds) {
        Set<String> added = new HashSet<>(currentIds);
        added.removeAll(previousIds);
        Set<String> removed = new HashSet<>(previousIds);
        removed.removeAll(currentIds);
        return new GraphDiff(added, removed);
    }

    public Set<String> added() {
        return added;
    }

    public Set<String> removed() {
        return removed;
    }
}
```

- [ ] **Step 5: Run the diff test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*GraphDiffTest'
```

Expected: 3 tests pass.

- [ ] **Step 6: Write the entities, converters, DAO, and database**

`ListKind.java`:

```java
package com.kira.followlens.data;

/** Which of an account's two lists a row belongs to. */
public enum ListKind {
    FOLLOWER,
    FOLLOWING
}
```

`ChangeDirection.java`:

```java
package com.kira.followlens.data;

/** Whether an account joined or left a list. */
public enum ChangeDirection {
    ADDED,
    REMOVED
}
```

`Converters.java`:

```java
package com.kira.followlens.data;

import androidx.room.TypeConverter;

/** Stores enums as readable TEXT rather than ordinals. */
public class Converters {

    @TypeConverter
    public String fromListKind(ListKind kind) {
        return kind == null ? null : kind.name();
    }

    @TypeConverter
    public ListKind toListKind(String value) {
        return value == null ? null : ListKind.valueOf(value);
    }

    @TypeConverter
    public String fromDirection(ChangeDirection direction) {
        return direction == null ? null : direction.name();
    }

    @TypeConverter
    public ChangeDirection toDirection(String value) {
        return value == null ? null : ChangeDirection.valueOf(value);
    }
}
```

`AccountEntity.java`:

```java
package com.kira.followlens.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "account")
public class AccountEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String username;

    public long addedAt;
}
```

`ScanEntity.java`:

```java
package com.kira.followlens.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One row per completed scan. Failed scans are never inserted. */
@Entity(tableName = "scan")
public class ScanEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String accountId;

    public long startedAt;

    public long finishedAt;

    public int followersCount;

    public int followingCount;

    /** True for an account's first scan, which produces no change events. */
    public boolean isBaseline;
}
```

`EdgeEntity.java`:

```java
package com.kira.followlens.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Current graph state, and only current state. An account that leaves a list has
 * its row deleted and the removal recorded in change_event, so this table always
 * answers "who is in this list right now" with no filtering.
 */
@Entity(tableName = "edge", primaryKeys = {"accountId", "kind", "userId"})
public class EdgeEntity {

    @NonNull
    public String accountId = "";

    @NonNull
    public ListKind kind = ListKind.FOLLOWER;

    @NonNull
    public String userId = "";

    public String username;

    public long firstSeenScanId;

    public long lastSeenScanId;
}
```

`ChangeEventEntity.java`:

```java
package com.kira.followlens.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Append-only log of every membership change, the source of all history. */
@Entity(tableName = "change_event")
public class ChangeEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String accountId;

    public long scanId;

    public ListKind kind;

    public ChangeDirection direction;

    public String userId;

    public String username;

    public long occurredAt;
}
```

`FollowLensDao.java`:

```java
package com.kira.followlens.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FollowLensDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAccount(AccountEntity account);

    @Query("SELECT * FROM account LIMIT 1")
    AccountEntity account();

    @Insert
    long insertScan(ScanEntity scan);

    @Query("SELECT COUNT(*) FROM scan WHERE accountId = :accountId")
    int scanCount(String accountId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertEdges(List<EdgeEntity> edges);

    @Query("DELETE FROM edge WHERE accountId = :accountId AND kind = :kind AND userId IN (:userIds)")
    void deleteEdges(String accountId, ListKind kind, List<String> userIds);

    @Query("SELECT userId FROM edge WHERE accountId = :accountId AND kind = :kind")
    List<String> edgeIds(String accountId, ListKind kind);

    /**
     * Every current row of one list. The commit loads these once rather than
     * issuing a point query per account, which for a 5000-follower list is the
     * difference between one query and five thousand.
     */
    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = :kind")
    List<EdgeEntity> edgesNow(String accountId, ListKind kind);

    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = :kind AND userId = :userId")
    EdgeEntity edge(String accountId, ListKind kind, String userId);

    @Query("SELECT COUNT(*) FROM edge")
    int edgeCount();

    @Query("SELECT isBaseline FROM scan WHERE id = :scanId")
    boolean isBaseline(long scanId);

    @Insert
    void insertChangeEvents(List<ChangeEventEntity> events);

    @Query("SELECT * FROM change_event WHERE scanId = :scanId ORDER BY id")
    List<ChangeEventEntity> changesForScan(long scanId);

    @Query("SELECT COUNT(*) FROM change_event")
    int changeEventCount();

    @Query("SELECT * FROM change_event WHERE accountId = :accountId ORDER BY occurredAt DESC, id DESC")
    LiveData<List<ChangeEventEntity>> changeFeed(String accountId);

    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = :kind ORDER BY username")
    LiveData<List<EdgeEntity>> edges(String accountId, ListKind kind);

    /** Accounts present in both lists. */
    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = 'FOLLOWER'"
            + " AND userId IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWING') ORDER BY username")
    LiveData<List<EdgeEntity>> mutuals(String accountId);

    /** You follow them, they do not follow you back. */
    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = 'FOLLOWING'"
            + " AND userId NOT IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWER') ORDER BY username")
    LiveData<List<EdgeEntity>> notFollowingBack(String accountId);

    /** They follow you, you do not follow them back. */
    @Query("SELECT * FROM edge WHERE accountId = :accountId AND kind = 'FOLLOWER'"
            + " AND userId NOT IN (SELECT userId FROM edge WHERE accountId = :accountId"
            + " AND kind = 'FOLLOWING') ORDER BY username")
    LiveData<List<EdgeEntity>> fans(String accountId);

    @Query("SELECT * FROM scan WHERE accountId = :accountId ORDER BY finishedAt DESC LIMIT 1")
    LiveData<ScanEntity> latestScan(String accountId);
}
```

`FollowLensDatabase.java`:

```java
package com.kira.followlens.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {AccountEntity.class, ScanEntity.class, EdgeEntity.class,
                ChangeEventEntity.class},
        version = 1,
        exportSchema = false)
@TypeConverters(Converters.class)
public abstract class FollowLensDatabase extends RoomDatabase {

    private static volatile FollowLensDatabase instance;

    public abstract FollowLensDao dao();

    public static FollowLensDatabase get(Context context) {
        if (instance == null) {
            synchronized (FollowLensDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            FollowLensDatabase.class, "followlens.db").build();
                }
            }
        }
        return instance;
    }

    /** For tests: a throwaway database that allows queries on the test thread. */
    public static FollowLensDatabase inMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context, FollowLensDatabase.class)
                .allowMainThreadQueries()
                .build();
    }
}
```

- [ ] **Step 7: Add `androidx.lifecycle` for `LiveData`**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.lifecycle:lifecycle-livedata:2.8.7'
```

- [ ] **Step 8: Write the failing repository test**

`ScanRepositoryTest.java`:

```java
package com.kira.followlens.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScanRepositoryTest {

    private static final String ACCOUNT = "999";

    private FollowLensDatabase db;
    private ScanRepository repository;

    private static Map<String, String> users(String... idUsernamePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            map.put(idUsernamePairs[i], idUsernamePairs[i + 1]);
        }
        return map;
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = FollowLensDatabase.inMemory(context);
        repository = new ScanRepository(db);
        repository.ensureAccount(ACCOUNT, "me");
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void firstScanIsBaselineAndProducesNoChangeEvents() {
        long scanId = repository.commitScan(ACCOUNT,
                users("1", "alice"), users("2", "bob"), 100L, 200L);

        assertTrue(repository.changesForScan(scanId).isEmpty());
        assertEquals(2, db.dao().edgeCount());
    }

    @Test
    public void secondScanRecordsAddedAndRemovedFollowers() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        long scanId = repository.commitScan(ACCOUNT,
                users("2", "bob"), users(), 300L, 400L);

        List<ChangeEventEntity> changes = repository.changesForScan(scanId);
        assertEquals(2, changes.size());

        ChangeEventEntity added = changes.stream()
                .filter(c -> c.direction == ChangeDirection.ADDED).findFirst().orElseThrow();
        assertEquals("bob", added.username);
        assertEquals(ListKind.FOLLOWER, added.kind);

        ChangeEventEntity removed = changes.stream()
                .filter(c -> c.direction == ChangeDirection.REMOVED).findFirst().orElseThrow();
        assertEquals("alice", removed.username);
    }

    @Test
    public void removedAccountsAreDeletedFromEdgeNotKeptAsStaleRows() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        repository.commitScan(ACCOUNT, users(), users(), 300L, 400L);

        assertEquals(0, db.dao().edgeCount());
        assertTrue(db.dao().edgeIds(ACCOUNT, ListKind.FOLLOWER).isEmpty());
    }

    @Test
    public void unchangedAccountKeepsFirstSeenScanId() {
        long first = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);
        long second = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 300L, 400L);

        assertTrue(repository.changesForScan(second).isEmpty());

        EdgeEntity edge = db.dao().edge(ACCOUNT, ListKind.FOLLOWER, "1");
        assertEquals(first, edge.firstSeenScanId);
        assertEquals(second, edge.lastSeenScanId);
    }

    @Test
    public void baselineFlagIsRecordedOnTheScanRow() {
        long first = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);
        long second = repository.commitScan(ACCOUNT, users("1", "alice"), users(), 300L, 400L);

        assertTrue(repository.wasBaseline(first));
        assertFalse(repository.wasBaseline(second));
    }

    @Test
    public void hasAnyScanReflectsCommittedScans() {
        assertFalse(repository.hasAnyScan(ACCOUNT));

        repository.commitScan(ACCOUNT, users("1", "alice"), users(), 100L, 200L);

        assertTrue(repository.hasAnyScan(ACCOUNT));
    }

    @Test
    public void aFailedCommitLeavesTheDatabaseUntouched() {
        repository.commitScan(ACCOUNT, users("1", "alice"), users("1", "alice"), 100L, 200L);
        int edgesBefore = db.dao().edgeCount();
        int eventsBefore = db.dao().changeEventCount();
        int scansBefore = db.dao().scanCount(ACCOUNT);

        // A null username forces a failure partway through the transaction.
        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("2", "bob");
        broken.put(null, "corrupt");

        assertThrows(RuntimeException.class,
                () -> repository.commitScan(ACCOUNT, broken, users(), 300L, 400L));

        assertEquals(edgesBefore, db.dao().edgeCount());
        assertEquals(eventsBefore, db.dao().changeEventCount());
        assertEquals(scansBefore, db.dao().scanCount(ACCOUNT));
    }
}
```

- [ ] **Step 9: Pin the Robolectric SDK level**

Robolectric 4.13 does not ship an emulated SDK 35, so tests that default to
`targetSdk` fail with "Unsupported Android SDK". Pin it.

Create `android/app/src/test/resources/robolectric.properties`:

```properties
sdk=34
```

- [ ] **Step 10: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScanRepositoryTest'
```

Expected: compile failure, `cannot find symbol class ScanRepository`.

- [ ] **Step 11: Implement `ScanRepository`**

`ScanRepository.java`:

```java
package com.kira.followlens.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commits scans. The commit is all-or-nothing: a scan either lands completely or
 * writes nothing at all, because a partially written scan looks exactly like a
 * mass unfollow to every downstream reader.
 */
public class ScanRepository {

    private final FollowLensDatabase db;

    public ScanRepository(FollowLensDatabase db) {
        this.db = db;
    }

    public void ensureAccount(String accountId, String username) {
        AccountEntity account = new AccountEntity();
        account.id = accountId;
        account.username = username;
        account.addedAt = System.currentTimeMillis();
        db.dao().upsertAccount(account);
    }

    public boolean hasAnyScan(String accountId) {
        return db.dao().scanCount(accountId) > 0;
    }

    public boolean wasBaseline(long scanId) {
        return db.dao().isBaseline(scanId);
    }

    public List<ChangeEventEntity> changesForScan(long scanId) {
        return db.dao().changesForScan(scanId);
    }

    /**
     * Writes one scan and everything derived from it inside a single
     * transaction.
     *
     * @return the id of the new scan row
     */
    public long commitScan(String accountId,
                           Map<String, String> followers,
                           Map<String, String> following,
                           long startedAt,
                           long finishedAt) {
        FollowLensDao dao = db.dao();
        long[] scanIdHolder = new long[1];

        db.runInTransaction(() -> {
            boolean baseline = dao.scanCount(accountId) == 0;

            ScanEntity scan = new ScanEntity();
            scan.accountId = accountId;
            scan.startedAt = startedAt;
            scan.finishedAt = finishedAt;
            scan.followersCount = followers.size();
            scan.followingCount = following.size();
            scan.isBaseline = baseline;
            long scanId = dao.insertScan(scan);
            scanIdHolder[0] = scanId;

            applyList(dao, accountId, ListKind.FOLLOWER, followers, scanId, baseline, finishedAt);
            applyList(dao, accountId, ListKind.FOLLOWING, following, scanId, baseline, finishedAt);
        });

        return scanIdHolder[0];
    }

    private void applyList(FollowLensDao dao,
                           String accountId,
                           ListKind kind,
                           Map<String, String> current,
                           long scanId,
                           boolean baseline,
                           long occurredAt) {
        // Load the existing rows once, up front. Two reasons: it avoids a point
        // query per account, and the removed accounts' usernames must be read
        // before their rows are deleted or the change log would only have ids.
        Map<String, String> previousUsernames = new HashMap<>();
        Map<String, Long> firstSeen = new HashMap<>();
        for (EdgeEntity existing : dao.edgesNow(accountId, kind)) {
            previousUsernames.put(existing.userId, existing.username);
            firstSeen.put(existing.userId, existing.firstSeenScanId);
        }

        GraphDiff diff = GraphDiff.of(previousUsernames.keySet(), current.keySet());

        List<EdgeEntity> upserts = new ArrayList<>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String userId = entry.getKey();
            if (userId == null) {
                throw new IllegalArgumentException("user id must not be null");
            }
            EdgeEntity edge = new EdgeEntity();
            edge.accountId = accountId;
            edge.kind = kind;
            edge.userId = userId;
            edge.username = entry.getValue();
            Long seen = firstSeen.get(userId);
            edge.firstSeenScanId = seen == null ? scanId : seen;
            edge.lastSeenScanId = scanId;
            upserts.add(edge);
        }
        dao.upsertEdges(upserts);
        deleteInChunks(dao, accountId, kind, diff.removed());

        if (baseline) {
            return;
        }

        List<ChangeEventEntity> events = new ArrayList<>();
        for (String userId : diff.added()) {
            events.add(event(accountId, scanId, kind, ChangeDirection.ADDED, userId,
                    current.get(userId), occurredAt));
        }
        for (String userId : diff.removed()) {
            String username = previousUsernames.get(userId);
            events.add(event(accountId, scanId, kind, ChangeDirection.REMOVED, userId,
                    username == null ? userId : username, occurredAt));
        }
        if (!events.isEmpty()) {
            dao.insertChangeEvents(events);
        }
    }

    /**
     * SQLite caps the number of bound variables in one statement, so a large
     * batch of removals has to be deleted in slices.
     */
    private void deleteInChunks(FollowLensDao dao, String accountId, ListKind kind,
                                Set<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<String> all = new ArrayList<>(userIds);
        int chunkSize = 500;
        for (int start = 0; start < all.size(); start += chunkSize) {
            dao.deleteEdges(accountId, kind,
                    all.subList(start, Math.min(start + chunkSize, all.size())));
        }
    }

    private ChangeEventEntity event(String accountId, long scanId, ListKind kind,
                                    ChangeDirection direction, String userId, String username,
                                    long occurredAt) {
        ChangeEventEntity e = new ChangeEventEntity();
        e.accountId = accountId;
        e.scanId = scanId;
        e.kind = kind;
        e.direction = direction;
        e.userId = userId;
        e.username = username;
        e.occurredAt = occurredAt;
        return e;
    }
}
```

- [ ] **Step 12: Run all `data` tests and confirm they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*data*'
```

Expected: 3 `GraphDiffTest` and 7 `ScanRepositoryTest` pass. The
`aFailedCommitLeavesTheDatabaseUntouched` test is the one that matters most —
if it fails, the transaction boundary is wrong and no later task should proceed.

- [ ] **Step 13: Commit**

```bash
git add android/app/build.gradle android/app/src/main/java/com/kira/followlens/data android/app/src/test/java/com/kira/followlens/data android/app/src/test/resources/robolectric.properties
git commit -m "feat(android): add Room storage with an append-only change log

Current state lives in edge, history in change_event, and a scan commits
both inside one transaction so a partial scan writes nothing rather than
looking like a mass unfollow."
```

---

### Task 4: WebView login and encrypted session storage

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/auth/CookieParser.java`
- Create: `android/app/src/main/java/com/kira/followlens/auth/SessionStore.java`
- Create: `android/app/src/main/java/com/kira/followlens/auth/LoginActivity.java`
- Create: `android/app/src/main/res/layout/activity_login.xml`
- Test: `android/app/src/test/java/com/kira/followlens/auth/CookieParserTest.java`
- Modify: `android/app/build.gradle`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SessionId.userIdOf` from Task 2.
- Produces:
  - `CookieParser.sessionIdFrom(String cookieHeader)` returns `String` or `null`.
  - `SessionStore(Context)` with `void save(String sessionId)`, `String sessionId()` returning null when absent, `void clear()`, `boolean hasSession()`.
  - `LoginActivity` — sets `RESULT_OK` once a session is captured.

- [ ] **Step 1: Add the security and WebKit dependencies**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'
    implementation 'androidx.appcompat:appcompat:1.7.0'
```

- [ ] **Step 2: Write the failing cookie-parser test**

`CookieParserTest.java`:

```java
package com.kira.followlens.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CookieParserTest {

    @Test
    public void extractsSessionIdFromAMultiCookieHeader() {
        String header = "csrftoken=abc; sessionid=12345%3Asecret%3A99; ds_user_id=12345";

        assertEquals("12345%3Asecret%3A99", CookieParser.sessionIdFrom(header));
    }

    @Test
    public void handlesSessionIdAsTheOnlyCookie() {
        assertEquals("12345:secret:99", CookieParser.sessionIdFrom("sessionid=12345:secret:99"));
    }

    @Test
    public void doesNotMatchASuffixedCookieName() {
        assertNull(CookieParser.sessionIdFrom("x_sessionid=nope; other=1"));
    }

    @Test
    public void returnsNullWhenAbsent() {
        assertNull(CookieParser.sessionIdFrom("csrftoken=abc; mid=xyz"));
    }

    @Test
    public void returnsNullForNullOrEmptyHeader() {
        assertNull(CookieParser.sessionIdFrom(null));
        assertNull(CookieParser.sessionIdFrom(""));
    }

    @Test
    public void returnsNullForAnEmptySessionIdValue() {
        assertNull(CookieParser.sessionIdFrom("sessionid=; csrftoken=abc"));
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*CookieParserTest'
```

Expected: compile failure, `cannot find symbol class CookieParser`.

- [ ] **Step 4: Implement `CookieParser`**

`CookieParser.java`:

```java
package com.kira.followlens.auth;

/**
 * CookieManager returns every cookie for a domain as one header string, so the
 * session cookie has to be picked out of it.
 */
public final class CookieParser {

    private CookieParser() {
    }

    /** Returns the raw sessionid value, or null when it is absent or empty. */
    public static String sessionIdFrom(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (!cookie.startsWith("sessionid=")) {
                continue;
            }
            String value = cookie.substring("sessionid=".length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
```

- [ ] **Step 5: Run the parser test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*CookieParserTest'
```

Expected: 6 tests pass.

- [ ] **Step 6: Implement `SessionStore`**

`SessionStore.java`:

```java
package com.kira.followlens.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.kira.followlens.net.SessionId;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Holds the session cookie. This value is a full account session, so it lives in
 * EncryptedSharedPreferences and is never logged.
 */
public class SessionStore {

    private static final String FILE = "followlens_session";
    private static final String KEY_SESSION_ID = "sessionid";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            this.prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("cannot open encrypted preferences", e);
        }
    }

    public void save(String sessionId) {
        // Throws if the cookie is malformed, so a useless session is never stored.
        SessionId.userIdOf(sessionId);
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply();
    }

    public String sessionId() {
        return prefs.getString(KEY_SESSION_ID, null);
    }

    public String userId() {
        String sessionId = sessionId();
        return sessionId == null ? null : SessionId.userIdOf(sessionId);
    }

    public boolean hasSession() {
        return sessionId() != null;
    }

    public void clear() {
        prefs.edit().remove(KEY_SESSION_ID).apply();
    }
}
```

- [ ] **Step 7: Implement `LoginActivity` and its layout**

`android/app/src/main/res/layout/activity_login.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<WebView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/login_web_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`LoginActivity.java`:

```java
package com.kira.followlens.auth;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kira.followlens.R;

/**
 * Logs in by letting the user sign in to Instagram normally, then reading the
 * session cookie the WebView received. This replaces copying a cookie out of
 * desktop DevTools by hand.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String LOGIN_URL = "https://www.instagram.com/accounts/login/";

    private WebView webView;
    private SessionStore sessionStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        sessionStore = new SessionStore(this);

        CookieManager.getInstance().setAcceptCookie(true);

        webView = findViewById(R.id.login_web_view);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                captureSessionIfPresent();
            }
        });
        webView.loadUrl(LOGIN_URL);
    }

    /**
     * Instagram sets the session cookie as soon as login succeeds, so every
     * page load is a chance to find it.
     */
    private void captureSessionIfPresent() {
        String header = CookieManager.getInstance().getCookie("https://www.instagram.com");
        String sessionId = CookieParser.sessionIdFrom(header);
        if (sessionId == null) {
            return;
        }
        try {
            sessionStore.save(sessionId);
        } catch (IllegalArgumentException e) {
            return;
        }
        Toast.makeText(this, R.string.login_captured, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
```

- [ ] **Step 8: Register the activity and add the string**

Replace `android/app/src/main/AndroidManifest.xml` entirely. The `<application>`
tag stops being self-closing, and it gains an AppCompat theme because
`AppCompatActivity` refuses to start without one:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">

        <activity
            android:name=".auth.LoginActivity"
            android:exported="false"
            android:label="@string/login_title" />

    </application>

</manifest>
```

In `strings.xml`, add:

```xml
    <string name="login_title">Log in to Instagram</string>
    <string name="login_captured">Session captured</string>
```

- [ ] **Step 9: Verify the build and the whole suite**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add android/app/build.gradle android/app/src/main android/app/src/test/java/com/kira/followlens/auth
git commit -m "feat(android): capture the session by logging in through a WebView

The user signs in to Instagram normally and the app reads the resulting
sessionid cookie, storing it in EncryptedSharedPreferences. No cookie is
ever copied by hand and no password is entered into this app."
```

---

### Task 5: Scan orchestration and the WorkManager worker

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/scan/Clock.java`
- Create: `android/app/src/main/java/com/kira/followlens/scan/ScanPrefs.java`
- Create: `android/app/src/main/java/com/kira/followlens/scan/ScanOutcome.java`
- Create: `android/app/src/main/java/com/kira/followlens/scan/ScanService.java`
- Create: `android/app/src/main/java/com/kira/followlens/scan/ScanWorker.java`
- Create: `android/app/src/main/java/com/kira/followlens/scan/ScanScheduler.java`
- Test: `android/app/src/test/java/com/kira/followlens/scan/ScanServiceTest.java`
- Modify: `android/app/build.gradle`

**Interfaces:**
- Consumes: `IgWebClient`, `IgException`, `Sleeper` (Task 2); `ScanRepository`, `ChangeEventEntity` (Task 3); `SessionStore` (Task 4).
- Produces:
  - `interface Clock { long nowMillis(); }` with `Clock.SYSTEM`.
  - `ScanPrefs` with `long lastScanAt()`, `void setLastScanAt(long)`.
  - `ScanOutcome` — an immutable result with `boolean ok()`, `String error()`, `long scanId()`, `int addedFollowers()`, `int removedFollowers()`, `boolean baseline()`, plus factories `ScanOutcome.committed(...)`, `ScanOutcome.skipped(String)`, `ScanOutcome.failed(String)`.
  - `ScanService(ScanRepository, ScanPrefs, Clock, ScanService.ClientFactory)` with `ScanOutcome run(String sessionId, boolean force)`.
  - `interface ScanService.ClientFactory { IgWebClient create(String sessionId); }`
  - `ScanScheduler.schedulePeriodic(Context)` and `ScanScheduler.requestOneOff(Context)`.

- [ ] **Step 1: Add WorkManager**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.work:work-runtime:2.9.1'
```

- [ ] **Step 2: Write the failing service test**

`ScanServiceTest.java`:

```java
package com.kira.followlens.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.Sleeper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScanServiceTest {

    private static final String SESSION = "999:secret:1";

    private MockWebServer server;
    private FollowLensDatabase db;
    private ScanRepository repository;
    private FakePrefs prefs;
    private MutableClock clock;
    private ScanService service;

    /** In-memory stand-in so the test does not touch real preferences. */
    private static class FakePrefs implements ScanPrefs {
        long lastScanAt;

        @Override
        public long lastScanAt() {
            return lastScanAt;
        }

        @Override
        public void setLastScanAt(long millis) {
            lastScanAt = millis;
        }
    }

    private static class MutableClock implements Clock {
        long now = 1_000_000L;

        @Override
        public long nowMillis() {
            return now;
        }
    }

    private void enqueueList(String... idUsernamePairs) {
        StringBuilder users = new StringBuilder();
        for (int i = 0; i < idUsernamePairs.length; i += 2) {
            if (users.length() > 0) {
                users.append(',');
            }
            users.append("{\"pk\":\"").append(idUsernamePairs[i])
                    .append("\",\"username\":\"").append(idUsernamePairs[i + 1]).append("\"}");
        }
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"users\":[" + users + "],\"next_max_id\":null}"));
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        db = FollowLensDatabase.inMemory(context);
        repository = new ScanRepository(db);
        prefs = new FakePrefs();
        clock = new MutableClock();
        service = new ScanService(repository, prefs, clock,
                sessionId -> new IgWebClient(server.url("/"), sessionId, Sleeper.NONE, 0, 0));
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        server.shutdown();
    }

    @Test
    public void firstScanCommitsAsBaselineWithNoReportedChanges() {
        enqueueList("1", "alice");   // following
        enqueueList("2", "bob");     // followers, pass 1
        enqueueList("2", "bob");     // followers, pass 2 adds nothing

        ScanOutcome outcome = service.run(SESSION, false);

        assertTrue(outcome.ok());
        assertTrue(outcome.baseline());
        assertEquals(0, outcome.addedFollowers());
        assertEquals(0, outcome.removedFollowers());
    }

    @Test
    public void secondScanReportsFollowerChanges() {
        enqueueList("1", "alice");
        enqueueList("2", "bob");
        enqueueList("2", "bob");
        service.run(SESSION, false);

        clock.now += 10_000_000L;
        enqueueList("1", "alice");
        enqueueList("3", "carol");
        enqueueList("3", "carol");

        ScanOutcome outcome = service.run(SESSION, false);

        assertTrue(outcome.ok());
        assertFalse(outcome.baseline());
        assertEquals(1, outcome.addedFollowers());
        assertEquals(1, outcome.removedFollowers());
    }

    @Test
    public void cooldownBlocksAScanAndIssuesNoRequests() {
        prefs.lastScanAt = clock.now - 60_000L;   // 60s ago, cooldown is 600s

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("Cooldown"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void forceBypassesTheCooldown() {
        prefs.lastScanAt = clock.now - 60_000L;
        enqueueList("1", "alice");
        enqueueList("2", "bob");
        enqueueList("2", "bob");

        ScanOutcome outcome = service.run(SESSION, true);

        assertTrue(outcome.ok());
    }

    @Test
    public void rateLimitFailsWithoutWritingAnything() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertFalse(repository.hasAnyScan("999"));
    }

    @Test
    public void midScanFailureWritesNothing() {
        enqueueList("1", "alice");                                    // following ok
        server.enqueue(new MockResponse().setResponseCode(500));      // followers fails

        ScanOutcome outcome = service.run(SESSION, false);

        assertFalse(outcome.ok());
        assertFalse(repository.hasAnyScan("999"));
        assertEquals(0, db.dao().edgeCount());
    }

    @Test
    public void failedScanDoesNotAdvanceTheCooldownClock() {
        server.enqueue(new MockResponse().setResponseCode(500));

        service.run(SESSION, false);

        assertEquals(0L, prefs.lastScanAt);
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScanServiceTest'
```

Expected: compile failure, `cannot find symbol class ScanService`.

- [ ] **Step 4: Implement `Clock`, `ScanPrefs`, and `ScanOutcome`**

`Clock.java`:

```java
package com.kira.followlens.scan;

/** Indirection over the system clock so cooldown logic is testable. */
public interface Clock {

    Clock SYSTEM = System::currentTimeMillis;

    long nowMillis();
}
```

`ScanPrefs.java`:

```java
package com.kira.followlens.scan;

import android.content.Context;
import android.content.SharedPreferences;

/** When the last successful scan finished. Not secret, so plain preferences. */
public interface ScanPrefs {

    long lastScanAt();

    void setLastScanAt(long millis);

    static ScanPrefs of(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("followlens_scan", Context.MODE_PRIVATE);
        return new ScanPrefs() {
            @Override
            public long lastScanAt() {
                return prefs.getLong("last_scan_at", 0L);
            }

            @Override
            public void setLastScanAt(long millis) {
                prefs.edit().putLong("last_scan_at", millis).apply();
            }
        };
    }
}
```

`ScanOutcome.java`:

```java
package com.kira.followlens.scan;

/** The result of one scan attempt. */
public final class ScanOutcome {

    private final boolean ok;
    private final String error;
    private final boolean retryable;
    private final long scanId;
    private final boolean baseline;
    private final int addedFollowers;
    private final int removedFollowers;

    private ScanOutcome(boolean ok, String error, boolean retryable, long scanId,
                        boolean baseline, int addedFollowers, int removedFollowers) {
        this.ok = ok;
        this.error = error;
        this.retryable = retryable;
        this.scanId = scanId;
        this.baseline = baseline;
        this.addedFollowers = addedFollowers;
        this.removedFollowers = removedFollowers;
    }

    public static ScanOutcome committed(long scanId, boolean baseline, int addedFollowers,
                                        int removedFollowers) {
        return new ScanOutcome(true, null, false, scanId, baseline, addedFollowers,
                removedFollowers);
    }

    /** Nothing was attempted: cooldown, or no session. Retrying now will not help. */
    public static ScanOutcome skipped(String reason) {
        return new ScanOutcome(false, reason, false, -1, false, 0, 0);
    }

    /** The attempt failed partway. Worth retrying later. */
    public static ScanOutcome failed(String reason) {
        return new ScanOutcome(false, reason, true, -1, false, 0, 0);
    }

    public boolean ok() {
        return ok;
    }

    public String error() {
        return error;
    }

    public boolean retryable() {
        return retryable;
    }

    public long scanId() {
        return scanId;
    }

    public boolean baseline() {
        return baseline;
    }

    public int addedFollowers() {
        return addedFollowers;
    }

    public int removedFollowers() {
        return removedFollowers;
    }

    public boolean hasFollowerChanges() {
        return addedFollowers > 0 || removedFollowers > 0;
    }
}
```

- [ ] **Step 5: Implement `ScanService`**

`ScanService.java`:

```java
package com.kira.followlens.scan;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgException;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.SessionId;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runs one scan: cooldown check, fetch both lists in full, then commit. Port of
 * backend/scanner.py. Nothing is written unless both lists arrived complete.
 */
public class ScanService {

    public static final long COOLDOWN_MILLIS = 600_000L;
    public static final int FOLLOWER_PASSES = 3;
    public static final double DELAY_SECONDS = 3.0;
    public static final double JITTER_SECONDS = 2.5;

    /** Lets tests point the client at a MockWebServer. */
    public interface ClientFactory {
        IgWebClient create(String sessionId);
    }

    private final ScanRepository repository;
    private final ScanPrefs prefs;
    private final Clock clock;
    private final ClientFactory clientFactory;

    public ScanService(ScanRepository repository, ScanPrefs prefs, Clock clock,
                       ClientFactory clientFactory) {
        this.repository = repository;
        this.prefs = prefs;
        this.clock = clock;
        this.clientFactory = clientFactory;
    }

    public ScanOutcome run(String sessionId, boolean force) {
        if (sessionId == null) {
            return ScanOutcome.skipped("No session. Log in to Instagram first.");
        }

        long now = clock.nowMillis();
        long elapsed = now - prefs.lastScanAt();
        if (!force && elapsed < COOLDOWN_MILLIS) {
            long remaining = (COOLDOWN_MILLIS - elapsed) / 1000;
            return ScanOutcome.skipped("Cooldown active: try again in " + remaining + "s.");
        }

        String accountId;
        try {
            accountId = SessionId.userIdOf(sessionId);
        } catch (IllegalArgumentException e) {
            return ScanOutcome.skipped("Stored session is malformed. Log in again.");
        }

        IgWebClient client = clientFactory.create(sessionId);

        Map<String, String> following;
        Map<String, String> followers;
        try {
            // Both lists are fetched completely before anything is written.
            following = client.following(accountId);
            followers = client.followers(accountId, FOLLOWER_PASSES);
        } catch (IgException.SessionExpired e) {
            return ScanOutcome.skipped(e.getMessage());
        } catch (IgException | IOException e) {
            return ScanOutcome.failed(e.getMessage());
        }

        repository.ensureAccount(accountId, accountId);
        long scanId = repository.commitScan(accountId, followers, following, now,
                clock.nowMillis());
        prefs.setLastScanAt(clock.nowMillis());

        List<ChangeEventEntity> changes = repository.changesForScan(scanId);
        int added = 0;
        int removed = 0;
        for (ChangeEventEntity change : changes) {
            if (change.kind != ListKind.FOLLOWER) {
                continue;
            }
            if (change.direction == ChangeDirection.ADDED) {
                added++;
            } else {
                removed++;
            }
        }

        return ScanOutcome.committed(scanId, repository.wasBaseline(scanId), added, removed);
    }
}
```

- [ ] **Step 6: Run the service tests and confirm they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScanServiceTest'
```

Expected: 7 tests pass.

- [ ] **Step 7: Implement `ScanWorker` and `ScanScheduler`**

`ScanWorker.java`:

```java
package com.kira.followlens.scan;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.data.ScanRepository;
import com.kira.followlens.net.IgWebClient;
import com.kira.followlens.net.Sleeper;

import okhttp3.HttpUrl;

/**
 * Runs a scan off the main thread. Worker.doWork() is already called on a
 * background thread, so no coroutines or futures are involved.
 */
public class ScanWorker extends Worker {

    public static final String KEY_FORCE = "force";

    private static final HttpUrl INSTAGRAM =
            HttpUrl.get("https://www.instagram.com/");

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionStore sessionStore = new SessionStore(context);

        ScanService service = new ScanService(
                new ScanRepository(FollowLensDatabase.get(context)),
                ScanPrefs.of(context),
                Clock.SYSTEM,
                sessionId -> new IgWebClient(INSTAGRAM, sessionId, Sleeper.REAL,
                        ScanService.DELAY_SECONDS, ScanService.JITTER_SECONDS));

        ScanOutcome outcome = service.run(sessionStore.sessionId(),
                getInputData().getBoolean(KEY_FORCE, false));

        if (outcome.ok()) {
            return Result.success();
        }
        return outcome.retryable() ? Result.retry() : Result.success();
    }
}
```

`ScanScheduler.java`:

```java
package com.kira.followlens.scan;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Registers the recurring scan and the manual one. */
public final class ScanScheduler {

    private static final String PERIODIC_NAME = "followlens-periodic-scan";

    /** 15 minutes is PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS. */
    private static final long INTERVAL_MINUTES = 15;

    private ScanScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ScanWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** A user-initiated refresh, which skips the cooldown. */
    public static void requestOneOff(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInputData(new Data.Builder()
                        .putBoolean(ScanWorker.KEY_FORCE, true)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }
}
```

- [ ] **Step 8: Verify the whole suite and the build**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add android/app/build.gradle android/app/src/main/java/com/kira/followlens android/app/src/test/java/com/kira/followlens/scan
git commit -m "feat(android): add scan orchestration on a 15-minute schedule

ScanService holds the cooldown and commit logic as plain Java so it is
testable without Android; ScanWorker is a thin WorkManager wrapper. A
failed scan writes nothing and leaves the cooldown clock untouched."
```

---

### Task 6: Unfollow notification

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/notify/ScanSummary.java`
- Create: `android/app/src/main/java/com/kira/followlens/notify/ScanNotifier.java`
- Create: `android/app/src/main/java/com/kira/followlens/ui/ChangesActivity.java` (stub; Task 7 fills it in)
- Test: `android/app/src/test/java/com/kira/followlens/notify/ScanSummaryTest.java`
- Modify: `android/app/src/main/java/com/kira/followlens/scan/ScanWorker.java`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ScanOutcome` from Task 5.
- Produces:
  - `ScanSummary.textFor(int added, int removed)` returns `String`, or `null` when there is nothing worth notifying about.
  - `ScanNotifier(Context)` with `void ensureChannel()` and `void notifyScan(ScanOutcome outcome)`.

- [ ] **Step 1: Write the failing summary test**

`ScanSummaryTest.java`:

```java
package com.kira.followlens.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ScanSummaryTest {

    @Test
    public void reportsBothDirections() {
        assertEquals("3 new followers, 1 unfollowed", ScanSummary.textFor(3, 1));
    }

    @Test
    public void usesSingularForOne() {
        assertEquals("1 new follower", ScanSummary.textFor(1, 0));
    }

    @Test
    public void reportsOnlyUnfollows() {
        assertEquals("2 unfollowed", ScanSummary.textFor(0, 2));
    }

    @Test
    public void returnsNullWhenNothingChanged() {
        assertNull(ScanSummary.textFor(0, 0));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScanSummaryTest'
```

Expected: compile failure, `cannot find symbol class ScanSummary`.

- [ ] **Step 3: Implement `ScanSummary`**

`ScanSummary.java`:

```java
package com.kira.followlens.notify;

/** Builds the one-line notification text for a scan. */
public final class ScanSummary {

    private ScanSummary() {
    }

    /** Returns null when there is nothing worth interrupting the user for. */
    public static String textFor(int added, int removed) {
        if (added == 0 && removed == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (added > 0) {
            text.append(added).append(added == 1 ? " new follower" : " new followers");
        }
        if (removed > 0) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(removed).append(" unfollowed");
        }
        return text.toString();
    }
}
```

- [ ] **Step 4: Run the summary test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScanSummaryTest'
```

Expected: 4 tests pass.

- [ ] **Step 5: Implement `ScanNotifier`**

`ScanNotifier.java`:

```java
package com.kira.followlens.notify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.kira.followlens.R;
import com.kira.followlens.scan.ScanOutcome;
import com.kira.followlens.ui.ChangesActivity;

/** Posts one summary notification per scan. Never one per changed account. */
public class ScanNotifier {

    private static final String CHANNEL_ID = "scan-results";
    private static final int NOTIFICATION_ID = 1;

    private final Context context;

    public ScanNotifier(Context context) {
        this.context = context;
    }

    public void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_scan_results),
                NotificationManager.IMPORTANCE_DEFAULT);
        context.getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
    }

    public void notifyScan(ScanOutcome outcome) {
        if (!outcome.ok() || outcome.baseline()) {
            return;
        }
        String text = ScanSummary.textFor(outcome.addedFollowers(), outcome.removedFollowers());
        if (text == null) {
            return;
        }
        // POST_NOTIFICATIONS only exists from API 33. Checking it on 26 to 32
        // would be meaningless, so only gate where the permission is real.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ensureChannel();

        PendingIntent tap = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, ChangesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }
}
```

`ChangesActivity` is the notification's tap target and is fully built in Task 7.
Create it as a stub now so this task compiles and stays independently verifiable —
Task 7 replaces the body.

`android/app/src/main/java/com/kira/followlens/ui/ChangesActivity.java`:

```java
package com.kira.followlens.ui;

import androidx.appcompat.app.AppCompatActivity;

/** Stub. Task 7 adds the change feed. */
public class ChangesActivity extends AppCompatActivity {
}
```

Register it in `AndroidManifest.xml` inside `<application>`:

```xml
        <activity
            android:name=".ui.ChangesActivity"
            android:exported="false" />
```

- [ ] **Step 6: Add the permission, the channel string, and the dependency**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.core:core:1.13.1'
```

In `AndroidManifest.xml`, alongside the existing `INTERNET` permission:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

In `strings.xml`:

```xml
    <string name="channel_scan_results">Scan results</string>
```

- [ ] **Step 7: Wire the notifier into `ScanWorker`**

In `ScanWorker.doWork()`, replace the return block with:

```java
        new ScanNotifier(context).notifyScan(outcome);

        if (outcome.ok()) {
            return Result.success();
        }
        return outcome.retryable() ? Result.retry() : Result.success();
```

Add the import `com.kira.followlens.notify.ScanNotifier`.

- [ ] **Step 8: Verify the build and the suite**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add android/app
git commit -m "feat(android): notify once per scan when followers change

One summary notification per scan rather than one per account, so a large
swing cannot produce a wall of notifications. Baseline scans stay silent."
```

---

### Task 7: Dashboard and change history

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/ui/DashboardActivity.java`
- Create: `android/app/src/main/java/com/kira/followlens/ui/DashboardViewModel.java`
- Modify: `android/app/src/main/java/com/kira/followlens/ui/ChangesActivity.java` (stub created in Task 6)
- Create: `android/app/src/main/java/com/kira/followlens/ui/ChangeAdapter.java`
- Create: `android/app/src/main/java/com/kira/followlens/ui/EdgeAdapter.java`
- Create: `android/app/src/main/res/layout/activity_dashboard.xml`
- Create: `android/app/src/main/res/layout/activity_changes.xml`
- Create: `android/app/src/main/res/layout/item_change.xml`
- Create: `android/app/src/main/res/layout/item_edge.xml`
- Test: `android/app/src/test/java/com/kira/followlens/ui/ChangeAdapterTest.java`
- Modify: `android/app/build.gradle`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FollowLensDatabase`, `FollowLensDao`, `EdgeEntity`, `ChangeEventEntity`, `ListKind`, `ChangeDirection` (Task 3); `SessionStore` (Task 4); `ScanScheduler` (Task 5); `ScanNotifier` (Task 6).
- Produces: `DashboardActivity` as the launcher; `ChangesActivity` as the notification target.

- [ ] **Step 1: Add RecyclerView, Material, and lifecycle**

In `android/app/build.gradle`, add to `dependencies`:

```groovy
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel:2.8.7'
    implementation 'com.google.android.material:material:1.12.0'
```

- [ ] **Step 2: Write the failing adapter test**

`ChangeAdapterTest.java`:

```java
package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import org.junit.Test;

public class ChangeAdapterTest {

    private static ChangeEventEntity change(ChangeDirection direction, ListKind kind,
                                            String username) {
        ChangeEventEntity event = new ChangeEventEntity();
        event.direction = direction;
        event.kind = kind;
        event.username = username;
        return event;
    }

    @Test
    public void labelsANewFollower() {
        assertEquals("+ alice started following you",
                ChangeAdapter.labelFor(change(ChangeDirection.ADDED, ListKind.FOLLOWER, "alice")));
    }

    @Test
    public void labelsAnUnfollow() {
        assertEquals("- bob unfollowed you",
                ChangeAdapter.labelFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWER, "bob")));
    }

    @Test
    public void labelsAccountsYouStartedFollowing() {
        assertEquals("+ you started following carol",
                ChangeAdapter.labelFor(change(ChangeDirection.ADDED, ListKind.FOLLOWING, "carol")));
    }

    @Test
    public void labelsAccountsYouStoppedFollowing() {
        assertEquals("- you stopped following dave",
                ChangeAdapter.labelFor(change(ChangeDirection.REMOVED, ListKind.FOLLOWING, "dave")));
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ChangeAdapterTest'
```

Expected: compile failure, `cannot find symbol class ChangeAdapter`.

- [ ] **Step 4: Write the layouts**

`activity_dashboard.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/counts"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/last_scan"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="4dp"
        android:textSize="12sp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingTop="12dp">

        <Button
            android:id="@+id/refresh"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/refresh" />

        <Button
            android:id="@+id/view_changes"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/changes" />
    </LinearLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="16dp"
        android:text="@string/not_following_back"
        android:textStyle="bold" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

`activity_changes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.recyclerview.widget.RecyclerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/changes"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="8dp" />
```

`item_change.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/label"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="12dp"
    android:textSize="14sp" />
```

`item_edge.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/username"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="12dp"
    android:textSize="14sp" />
```

- [ ] **Step 5: Implement the adapters**

`ChangeAdapter.java`:

```java
package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.util.ArrayList;
import java.util.List;

public class ChangeAdapter extends RecyclerView.Adapter<ChangeAdapter.ViewHolder> {

    private final List<ChangeEventEntity> items = new ArrayList<>();

    /** Package-visible and static so it can be unit tested without a View. */
    static String labelFor(ChangeEventEntity event) {
        boolean added = event.direction == ChangeDirection.ADDED;
        String sign = added ? "+ " : "- ";
        if (event.kind == ListKind.FOLLOWER) {
            return sign + event.username + (added ? " started following you" : " unfollowed you");
        }
        return sign + "you " + (added ? "started following " : "stopped following ")
                + event.username;
    }

    public void submit(List<ChangeEventEntity> events) {
        items.clear();
        items.addAll(events);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_change, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.label.setText(labelFor(items.get(position)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        ViewHolder(View view) {
            super(view);
            label = view.findViewById(R.id.label);
        }
    }
}
```

`EdgeAdapter.java`:

```java
package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeEntity;

import java.util.ArrayList;
import java.util.List;

public class EdgeAdapter extends RecyclerView.Adapter<EdgeAdapter.ViewHolder> {

    private final List<EdgeEntity> items = new ArrayList<>();

    public void submit(List<EdgeEntity> edges) {
        items.clear();
        items.addAll(edges);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_edge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.username.setText(items.get(position).username);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView username;

        ViewHolder(View view) {
            super(view);
            username = view.findViewById(R.id.username);
        }
    }
}
```

- [ ] **Step 6: Run the adapter test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ChangeAdapterTest'
```

Expected: 4 tests pass.

- [ ] **Step 7: Implement the activities**

`DashboardActivity.java`:

```java
package com.kira.followlens.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.LoginActivity;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.FollowLensDatabase;
import com.kira.followlens.notify.ScanNotifier;
import com.kira.followlens.scan.ScanScheduler;

import java.text.DateFormat;
import java.util.Date;

public class DashboardActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private boolean permissionAsked;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sessionStore = new SessionStore(this);
        if (!sessionStore.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        new ScanNotifier(this).ensureChannel();
        ScanScheduler.schedulePeriodic(this);

        String accountId = sessionStore.userId();
        FollowLensDao dao = FollowLensDatabase.get(this).dao();

        TextView counts = findViewById(R.id.counts);
        TextView lastScan = findViewById(R.id.last_scan);

        dao.latestScan(accountId).observe(this, scan -> {
            if (scan == null) {
                counts.setText(R.string.no_scan_yet);
                lastScan.setText("");
                return;
            }
            counts.setText(getString(R.string.counts_format,
                    scan.followersCount, scan.followingCount));
            lastScan.setText(getString(R.string.last_scan_format,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(new Date(scan.finishedAt))));
            requestNotificationPermissionOnce();
        });

        EdgeAdapter adapter = new EdgeAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        dao.notFollowingBack(accountId).observe(this, adapter::submit);

        findViewById(R.id.view_changes).setOnClickListener(v ->
                startActivity(new Intent(this, ChangesActivity.class)));

        Button refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(v -> ScanScheduler.requestOneOff(this));
    }

    /**
     * Asked only after a scan exists, so the prompt arrives with visible context
     * rather than on first launch. The flag matters because the LiveData
     * observer fires on every scan and this must not re-prompt each time.
     */
    private void requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionAsked) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        permissionAsked = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }
}
```

`ChangesActivity.java`:

```java
package com.kira.followlens.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.auth.SessionStore;
import com.kira.followlens.data.FollowLensDatabase;

public class ChangesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changes);

        String accountId = new SessionStore(this).userId();
        if (accountId == null) {
            finish();
            return;
        }

        ChangeAdapter adapter = new ChangeAdapter();
        RecyclerView list = findViewById(R.id.changes);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FollowLensDatabase.get(this).dao().changeFeed(accountId)
                .observe(this, adapter::submit);
    }
}
```

- [ ] **Step 8: Register the activities and add strings**

In `AndroidManifest.xml`, inside `<application>`:

```xml
        <activity
            android:name=".ui.DashboardActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

```

In `strings.xml`:

```xml
    <string name="refresh">Refresh</string>
    <string name="changes">Changes</string>
    <string name="not_following_back">Not following you back</string>
    <string name="no_scan_yet">No scan yet. Tap Refresh.</string>
    <string name="counts_format">%1$d followers · %2$d following</string>
    <string name="last_scan_format">Last scan: %1$s</string>
```

Keep the existing strings; add these before the closing `</resources>` tag.

`ChangesActivity` was registered as a stub in Task 6, so its `<activity>` element
already exists — add only `android:label="@string/changes"` to it rather than
duplicating the element.

- [ ] **Step 9: Verify the build and the full suite**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Install on a device and confirm the loop works end to end**

```bash
cd android && ./gradlew :app:installDebug
```

Then on the device: log in, tap Refresh, wait for the scan, confirm counts appear
and the Changes screen is empty (the first scan is a baseline). Tap Refresh again
after 10 minutes and confirm any real changes appear.

This is the first point where the endpoint-parity risk is actually resolved. If
the followers list comes back empty or the request 400s, the headers in
`IgWebClient` are the place to look.

- [ ] **Step 11: Commit**

```bash
git add android/app
git commit -m "feat(android): add the dashboard and change history screens

Counts, accounts not following back, and the change feed, all driven by
LiveData from Room so the lists update themselves after a scan."
```

---

### Task 8: The remaining list views and scan-grouped history

Task 7 surfaces counts and one list. The spec calls for mutuals and one-way
follows in both directions, and for history grouped by scan rather than a flat
feed. This task closes both.

**Files:**
- Create: `android/app/src/main/java/com/kira/followlens/ui/ListView.java`
- Create: `android/app/src/main/java/com/kira/followlens/ui/ChangeFeedItems.java`
- Test: `android/app/src/test/java/com/kira/followlens/ui/ChangeFeedItemsTest.java`
- Modify: `android/app/src/main/java/com/kira/followlens/ui/DashboardActivity.java`
- Modify: `android/app/src/main/java/com/kira/followlens/ui/ChangeAdapter.java`
- Modify: `android/app/src/main/res/layout/activity_dashboard.xml`
- Create: `android/app/src/main/res/layout/item_scan_header.xml`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FollowLensDao` queries `edges`, `mutuals`, `notFollowingBack`, `fans` (Task 3); `ChangeAdapter`, `EdgeAdapter` (Task 7).
- Produces:
  - `enum ListView` with `FOLLOWERS`, `FOLLOWING`, `MUTUALS`, `NOT_FOLLOWING_BACK`, `FANS`, each carrying a `labelRes`.
  - `ChangeFeedItems.build(List<ChangeEventEntity>)` returns `List<ChangeFeedItems.Item>`, where `Item` is either a scan header or a change.

- [ ] **Step 1: Write the failing grouping test**

`ChangeFeedItemsTest.java`:

```java
package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ChangeFeedItemsTest {

    private static ChangeEventEntity change(long scanId, long occurredAt, String username) {
        ChangeEventEntity event = new ChangeEventEntity();
        event.scanId = scanId;
        event.occurredAt = occurredAt;
        event.username = username;
        event.kind = ListKind.FOLLOWER;
        event.direction = ChangeDirection.ADDED;
        return event;
    }

    @Test
    public void insertsOneHeaderPerScan() {
        List<ChangeFeedItems.Item> items = ChangeFeedItems.build(Arrays.asList(
                change(2, 2000L, "carol"),
                change(2, 2000L, "dave"),
                change(1, 1000L, "alice")));

        assertEquals(5, items.size());
        assertTrue(items.get(0).isHeader());
        assertFalse(items.get(1).isHeader());
        assertFalse(items.get(2).isHeader());
        assertTrue(items.get(3).isHeader());
        assertFalse(items.get(4).isHeader());
    }

    @Test
    public void headerCarriesTheScanTimestamp() {
        List<ChangeFeedItems.Item> items =
                ChangeFeedItems.build(Collections.singletonList(change(1, 1234L, "alice")));

        assertEquals(1234L, items.get(0).occurredAt());
    }

    @Test
    public void producesNothingForAnEmptyFeed() {
        assertTrue(ChangeFeedItems.build(Collections.emptyList()).isEmpty());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ChangeFeedItemsTest'
```

Expected: compile failure, `cannot find symbol class ChangeFeedItems`.

- [ ] **Step 3: Implement `ChangeFeedItems`**

`ChangeFeedItems.java`:

```java
package com.kira.followlens.ui;

import com.kira.followlens.data.ChangeEventEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens the change feed into headers and rows. The feed arrives newest first
 * and already grouped by scan, so this only has to insert a header whenever the
 * scan id changes.
 */
public final class ChangeFeedItems {

    /** Either a scan header or a single change. */
    public static final class Item {

        private final ChangeEventEntity change;
        private final long occurredAt;

        private Item(ChangeEventEntity change, long occurredAt) {
            this.change = change;
            this.occurredAt = occurredAt;
        }

        public boolean isHeader() {
            return change == null;
        }

        public ChangeEventEntity change() {
            return change;
        }

        public long occurredAt() {
            return occurredAt;
        }
    }

    private ChangeFeedItems() {
    }

    public static List<Item> build(List<ChangeEventEntity> events) {
        List<Item> items = new ArrayList<>();
        Long currentScanId = null;
        for (ChangeEventEntity event : events) {
            if (currentScanId == null || currentScanId != event.scanId) {
                items.add(new Item(null, event.occurredAt));
                currentScanId = event.scanId;
            }
            items.add(new Item(event, event.occurredAt));
        }
        return items;
    }
}
```

- [ ] **Step 4: Run the grouping test and confirm it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ChangeFeedItemsTest'
```

Expected: 3 tests pass.

- [ ] **Step 5: Add the header layout and strings**

`android/app/src/main/res/layout/item_scan_header.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/scan_time"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="12dp"
    android:paddingTop="16dp"
    android:paddingEnd="12dp"
    android:paddingBottom="4dp"
    android:textSize="12sp"
    android:textStyle="bold" />
```

In `strings.xml`, add before `</resources>`:

```xml
    <string name="followers">Followers</string>
    <string name="following">Following</string>
    <string name="mutuals">Mutuals</string>
    <string name="fans">Fans (you don\'t follow back)</string>
```

`not_following_back` already exists from Task 7 and is reused.

- [ ] **Step 6: Implement `ListView`**

`ListView.java`:

```java
package com.kira.followlens.ui;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeEntity;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.ListKind;

import java.util.List;

/** The list the dashboard is currently showing. */
public enum ListView {

    FOLLOWERS(R.string.followers),
    FOLLOWING(R.string.following),
    MUTUALS(R.string.mutuals),
    NOT_FOLLOWING_BACK(R.string.not_following_back),
    FANS(R.string.fans);

    private final int labelRes;

    ListView(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int labelRes() {
        return labelRes;
    }

    public LiveData<List<EdgeEntity>> query(FollowLensDao dao, String accountId) {
        switch (this) {
            case FOLLOWERS:
                return dao.edges(accountId, ListKind.FOLLOWER);
            case FOLLOWING:
                return dao.edges(accountId, ListKind.FOLLOWING);
            case MUTUALS:
                return dao.mutuals(accountId);
            case NOT_FOLLOWING_BACK:
                return dao.notFollowingBack(accountId);
            case FANS:
                return dao.fans(accountId);
            default:
                throw new IllegalStateException("unhandled list view: " + this);
        }
    }
}
```

- [ ] **Step 7: Add the selector to the dashboard layout**

In `activity_dashboard.xml`, replace the static `not_following_back` `TextView`
with a spinner:

```xml
    <Spinner
        android:id="@+id/list_selector"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp" />
```

- [ ] **Step 8: Wire the selector into `DashboardActivity`**

Replace the block in `onCreate` that reads:

```java
        EdgeAdapter adapter = new EdgeAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        dao.notFollowingBack(accountId).observe(this, adapter::submit);
```

with:

```java
        EdgeAdapter adapter = new EdgeAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        ListView[] views = ListView.values();
        String[] labels = new String[views.length];
        for (int i = 0; i < views.length; i++) {
            labels[i] = getString(views[i].labelRes());
        }

        Spinner selector = findViewById(R.id.list_selector);
        selector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        selector.setSelection(ListView.NOT_FOLLOWING_BACK.ordinal());

        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private LiveData<List<EdgeEntity>> current;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Detach the previous query or every switch leaves an observer
                // behind and the list starts flickering between datasets.
                if (current != null) {
                    current.removeObservers(DashboardActivity.this);
                }
                current = views[position].query(dao, accountId);
                current.observe(DashboardActivity.this, adapter::submit);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
```

Add these imports:

```java
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.lifecycle.LiveData;

import com.kira.followlens.data.EdgeEntity;

import java.util.List;
```

- [ ] **Step 9: Make `ChangeAdapter` render headers**

Replace `ChangeAdapter.java` in full:

```java
package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChangeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHANGE = 1;

    private final List<ChangeFeedItems.Item> items = new ArrayList<>();

    /** Package-visible and static so it can be unit tested without a View. */
    static String labelFor(ChangeEventEntity event) {
        boolean added = event.direction == ChangeDirection.ADDED;
        String sign = added ? "+ " : "- ";
        if (event.kind == ListKind.FOLLOWER) {
            return sign + event.username + (added ? " started following you" : " unfollowed you");
        }
        return sign + "you " + (added ? "started following " : "stopped following ")
                + event.username;
    }

    public void submit(List<ChangeEventEntity> events) {
        items.clear();
        items.addAll(ChangeFeedItems.build(events));
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader() ? TYPE_HEADER : TYPE_CHANGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_scan_header, parent, false));
        }
        return new ChangeHolder(inflater.inflate(R.layout.item_change, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChangeFeedItems.Item item = items.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).scanTime.setText(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(item.occurredAt())));
            return;
        }
        ((ChangeHolder) holder).label.setText(labelFor(item.change()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView scanTime;

        HeaderHolder(View view) {
            super(view);
            scanTime = view.findViewById(R.id.scan_time);
        }
    }

    static class ChangeHolder extends RecyclerView.ViewHolder {
        final TextView label;

        ChangeHolder(View view) {
            super(view);
            label = view.findViewById(R.id.label);
        }
    }
}
```

`ChangeAdapterTest` from Task 7 still passes unchanged, because `labelFor` kept
its signature.

- [ ] **Step 10: Verify the build and the full suite**

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass including the 4 from
`ChangeAdapterTest` and the 3 new `ChangeFeedItemsTest`.

- [ ] **Step 11: Commit**

```bash
git add android/app
git commit -m "feat(android): add all five list views and group history by scan

The dashboard spinner switches between followers, following, mutuals, and
both one-way directions, and the change feed now carries a timestamp
header per scan instead of running as one flat list."
```

---

### Task 9: CI and documentation

**Files:**
- Create: `.github/workflows/android.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: the whole build from Tasks 1 to 8.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Add the Android workflow**

`.github/workflows/android.yml`:

```yaml
name: android

on:
  push:
    paths:
      - 'android/**'
      - '.github/workflows/android.yml'
  pull_request:
    paths:
      - 'android/**'
      - '.github/workflows/android.yml'

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Assemble and test
        working-directory: android
        run: ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

CI has no `local.properties`, so the SDK comes from the runner's `ANDROID_HOME`
and the JDK from `setup-java`. Both are correct without that file, which is why
it stays git-ignored.

- [ ] **Step 2: Scope the Python workflow to Python changes**

In `.github/workflows/ci.yml`, add `paths` filters to both triggers so Python
tests stop running on Android-only commits:

```yaml
on:
  push:
    paths:
      - 'backend/**'
      - 'frontend/**'
      - 'tests/**'
      - 'run.py'
      - 'requirements.txt'
      - 'pyproject.toml'
      - '.github/workflows/ci.yml'
  pull_request:
    paths:
      - 'backend/**'
      - 'frontend/**'
      - 'tests/**'
      - 'run.py'
      - 'requirements.txt'
      - 'pyproject.toml'
      - '.github/workflows/ci.yml'
```

Read the existing file first and keep any other trigger keys it already has.

- [ ] **Step 3: Document the app in the README**

In `README.md`, add this section immediately before `## License`:

```markdown
## Android app

An Android client lives in `android/`. It is a standalone pure-Java app — it does
not talk to the Python server, and the desktop dashboard keeps working unchanged.

Instead of copying a cookie out of DevTools, you log in to Instagram in the app
and it reads the session cookie itself. It scans every 15 minutes in the
background and notifies you when someone unfollows.

```bash
cd android
./gradlew :app:installDebug
```

The build needs JDK 21; the wrapper is pinned and `android/local.properties`
points `org.gradle.java.home` at the JDK bundled with Android Studio.

Distribution is by sideloaded APK. An app whose purpose is automating Instagram
access with a session cookie is unlikely to be accepted on Google Play, and the
disclaimer above applies to the Android app exactly as it does to the desktop one.
```

Also update the `## Project structure` block to add:

```
├─ android/               # standalone pure-Java Android app
```

- [ ] **Step 4: Verify the workflow file parses**

```bash
python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android.yml')); yaml.safe_load(open('.github/workflows/ci.yml')); print('both workflows parse')"
```

Expected: `both workflows parse`.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows README.md
git commit -m "ci: run Android unit tests and scope Python CI by path

Adds a Gradle workflow on JDK 21 and stops the Python matrix running on
Android-only commits."
```

---

## Verification

After Task 9, from the repo root:

```bash
cd android && ./gradlew clean :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with 40 or more unit tests passing and zero
failures. The single most important test is
`ScanRepositoryTest.aFailedCommitLeavesTheDatabaseUntouched` — if it ever
regresses, the app can report a network failure as a mass unfollow.

Then confirm on a device: the periodic worker is registered (`adb shell dumpsys
jobscheduler | grep followlens`), and a second scan after a real follow or
unfollow produces both a change-feed row and a notification.
