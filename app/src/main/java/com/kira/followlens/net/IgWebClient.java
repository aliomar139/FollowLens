package com.kira.followlens.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private final String sessionId;
    private final String userId;
    private String csrfToken;
    private boolean csrfBootstrapAttempted;
    private final Sleeper sleeper;
    private final double delaySeconds;
    private final double jitterSeconds;
    private final OkHttpClient http;
    private final Random random = new Random();

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
        this(baseUrl, sessionId, null, sleeper, delaySeconds, jitterSeconds);
    }

    /**
     * @param csrfToken the {@code csrftoken} cookie, when the pasted session
     *                  included one. A browser always sends it next to sessionid;
     *                  null means bootstrap one on first use.
     */
    public IgWebClient(HttpUrl baseUrl, String sessionId, String csrfToken, Sleeper sleeper,
                       double delaySeconds, double jitterSeconds) {
        this.baseUrl = baseUrl;
        this.sleeper = sleeper;
        this.delaySeconds = delaySeconds;
        this.jitterSeconds = jitterSeconds;
        this.sessionId = sessionId;
        this.userId = SessionId.userIdOf(sessionId);
        this.csrfToken = csrfToken == null || csrfToken.trim().isEmpty() ? null : csrfToken.trim();
        this.http = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                // Redirects are never legitimate for these JSON endpoints. Following
                // one turns Instagram's "no" into a 200 full of HTML, which then
                // looks like a parse bug instead of a throttled request.
                .followRedirects(false)
                .followSslRedirects(false)
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
                body = asJsonObject(response.body().string(), kind, uid);
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

    /**
     * Parses a friendship page body, or fails as a fetch error.
     *
     * A 200 is not a promise of JSON. A throttled or challenged session gets an
     * HTML page or a bare string with a 200 status, and Gson signals that with an
     * unchecked JsonSyntaxException — which would sail past every caller's
     * catch clause and kill the worker instead of being retried.
     */
    private JsonObject asJsonObject(String raw, String kind, String uid) throws IgException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (RuntimeException e) {
            throw new IgException.Fetch(kind + " page for " + uid
                    + " returned a 200 that is not valid JSON (" + e.getClass().getSimpleName()
                    + "); body starts: " + snippet(raw));
        }
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IgException.Fetch(kind + " page for " + uid
                    + " returned a 200 that is not a JSON object; the session is most likely"
                    + " being throttled or challenged. Body starts: " + snippet(raw));
        }
        return parsed.getAsJsonObject();
    }

    /**
     * A short, single-line prefix of an unexpected body.
     *
     * Enough to tell an HTML challenge page apart from a JSON error envelope,
     * which is the difference between "wait it out" and "the request is wrong".
     * Deliberately short so a surprise body cannot dump account data into a log.
     */
    private static String snippet(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "<empty>";
        }
        String flat = raw.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

    /** The cookie header a browser would send for these endpoints. */
    private String cookieHeader() {
        StringBuilder cookies = new StringBuilder("sessionid=").append(sessionId)
                .append("; ds_user_id=").append(userId);
        if (csrfToken != null) {
            cookies.append("; csrftoken=").append(csrfToken);
        }
        return cookies.toString();
    }

    /**
     * Fetches a csrftoken from the site root when the pasted session did not
     * carry one. Done once, best-effort: the endpoints have been observed to
     * answer without it, so a failure here must not fail the scan.
     */
    private void bootstrapCsrfTokenOnce() {
        if (csrfToken != null || csrfBootstrapAttempted) {
            return;
        }
        csrfBootstrapAttempted = true;
        Request request = new Request.Builder()
                .url(baseUrl)
                .header("user-agent", USER_AGENT)
                .header("accept-language", "en-US,en;q=0.9")
                .header("cookie", cookieHeader())
                .build();
        try (Response response = http.newCall(request).execute()) {
            for (String setCookie : response.headers("set-cookie")) {
                String token = firstCookieValue(setCookie, "csrftoken");
                if (token != null) {
                    csrfToken = token;
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            // Best effort only; carry on without the token.
        }
    }

    private static String firstCookieValue(String setCookieHeader, String name) {
        String prefix = name + "=";
        for (String part : setCookieHeader.split(";")) {
            String candidate = part.trim();
            if (candidate.startsWith(prefix)) {
                String value = candidate.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** GET with up to two backoff retries on HTTP 429. */
    private Response get(HttpUrl url) throws IgException, IOException {
        bootstrapCsrfTokenOnce();

        for (int attempt = 0; attempt < 3; attempt++) {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("x-ig-app-id", APP_ID)
                    .header("x-asbd-id", "129477")
                    .header("x-ig-www-claim", "0")
                    .header("user-agent", USER_AGENT)
                    .header("x-requested-with", "XMLHttpRequest")
                    .header("accept", "*/*")
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("referer", "https://www.instagram.com/")
                    // The web app sends these on every XHR; without them the
                    // request is distinguishable from a real browser call.
                    .header("sec-fetch-site", "same-origin")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-dest", "empty")
                    .header("cookie", cookieHeader());
            if (csrfToken != null) {
                builder.header("x-csrftoken", csrfToken);
            }

            Response response = http.newCall(builder.build()).execute();

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
            if (response.code() >= 300 && response.code() < 400) {
                String location = response.header("Location");
                response.close();
                // Verified against the same session from Python: the followers
                // endpoint 302s to the homepage while following still returns
                // JSON. That is a per-endpoint soft block, not a broken request.
                throw new IgException.RateLimited(
                        "Instagram redirected this request to "
                                + (location == null ? "another page" : location)
                                + " instead of returning data. The session is being"
                                + " throttled on this endpoint — wait and try again"
                                + " later, or scan less often.");
            }
            return response;
        }
        throw new IllegalStateException("unreachable");
    }

    private void pause() {
        sleeper.sleep((long) ((delaySeconds + random.nextDouble() * jitterSeconds) * 1000));
    }
}
