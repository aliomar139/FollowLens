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

    /**
     * Told after every page that lands, with the running unique-account total.
     *
     * The client reports pages because it is the only layer that knows when one
     * arrives; it deliberately does not know what the caller does with that.
     */
    public interface PageListener {
        void onPage(String kind, int collected);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /**
     * A runaway guard, not a product limit.
     *
     * It used to be 40, which at 100 accounts a page meant a hard ceiling of
     * 4,000 followers — and walkOnce hit that ceiling silently, handing back a
     * truncated list that the caller then committed as the complete one. On a
     * larger account that reads as a mass unfollow that never happened. The
     * cap is now high enough that no real account reaches it, and running out
     * of pages is treated as the failure it is rather than as an ending.
     */
    private static final int MAX_PAGES_PER_PASS = 200;

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
    private PageListener pageListener = (kind, collected) -> {
    };

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

    /** Reporting is optional and off by default; tests never set one. */
    public void setPageListener(PageListener listener) {
        this.pageListener = listener == null ? (kind, collected) -> {
        } : listener;
    }

    /** Returns the full following list as user id to username. */
    public Map<String, String> following(String uid) throws IgException, IOException {
        Map<String, String> out = new LinkedHashMap<>();
        boolean complete = walkOnce(uid, "following", FOLLOWING_PAGE_SIZE, out);
        if (!complete) {
            throw new IgException.Fetch("following for " + uid + " still had more pages after "
                    + MAX_PAGES_PER_PASS + "; aborting so a partial list is not stored as a"
                    + " full one.");
        }
        return out;
    }

    /** Kept for callers with no expected count; behaves exactly as before. */
    public Map<String, String> followers(String uid, int maxPasses)
            throws IgException, IOException {
        return followers(uid, maxPasses, null);
    }

    /**
     * Returns the full followers list as user id to username.
     *
     * The followers endpoint paginates inconsistently, so this runs repeated
     * full passes and unions the results. Two things end it early.
     *
     * The first is {@code expected}: once the union has reached the number of
     * followers the account reports having, there is nothing left to find and
     * further passes are pure cost. This is where the time goes — without it
     * the loop cannot know pass one was complete, so it always pays for a
     * second pass to prove it.
     *
     * The second is the original test, kept as the fallback for when no
     * expected count is available: stop once a pass adds nobody new.
     *
     * @param expected how many followers the account reports, or null when that
     *                 could not be established. Null means every decision below
     *                 falls back to the behaviour this method has always had.
     */
    public Map<String, String> followers(String uid, int maxPasses, Integer expected)
            throws IgException, IOException {
        Map<String, String> out = new LinkedHashMap<>();
        int previousSize = -1;
        boolean truncated = false;

        for (int pass = 0; pass < maxPasses; pass++) {
            truncated |= !walkOnce(uid, "followers", FOLLOWERS_PAGE_SIZE, out);

            // Reaching the reported total is proof of completeness that no
            // amount of re-walking can improve on.
            if (expected != null && out.size() >= expected) {
                return out;
            }
            if (out.size() == previousSize) {
                break;
            }
            previousSize = out.size();
            pause();
        }

        // Falling out of the page loop with a live cursor is not an ending. If
        // the union never reached the expected total either, the list in hand is
        // short and committing it would invent unfollows.
        if (truncated) {
            throw new IgException.Fetch("followers for " + uid + " still had more pages after "
                    + MAX_PAGES_PER_PASS + "; aborting so a partial list is not stored as a"
                    + " full one.");
        }
        return out;
    }

    /**
     * How many followers the account reports having, or null if that cannot be
     * established right now.
     *
     * Deliberately best-effort and deliberately not routed through {@link #get},
     * which retries a 429 twice with fifteen and thirty second backoffs. This
     * call exists to make the scan faster; paying forty-five seconds for it when
     * the endpoint is unhappy would defeat the entire point. One attempt, and
     * any failure at all means null and the caller carries on as before.
     */
    public Integer followerCount(String uid) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/v1/users/" + uid + "/info/")
                .build();
        try (Response response = http.newCall(profileRequest(url)).execute()) {
            if (response.code() != 200) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(response.body().string());
            JsonObject user = parsed.getAsJsonObject().getAsJsonObject("user");
            JsonElement count = user == null ? null : user.get("follower_count");
            if (count == null || count.isJsonNull()) {
                return null;
            }
            int value = count.getAsInt();
            return value <= 0 ? null : value;
        } catch (IOException | RuntimeException e) {
            // Any shape of failure — offline, throttled, a body that changed
            // shape — is the same answer: we do not know, so do it the long way.
            return null;
        }
    }

    private Request profileRequest(HttpUrl url) {
        bootstrapCsrfTokenOnce();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("x-ig-app-id", APP_ID)
                .header("user-agent", USER_AGENT)
                .header("accept", "*/*")
                .header("accept-language", "en-US,en;q=0.9")
                .header("referer", "https://www.instagram.com/")
                .header("cookie", cookieHeader());
        if (csrfToken != null) {
            builder.header("x-csrftoken", csrfToken);
        }
        return builder.build();
    }

    /**
     * Walks every page of one list once, merging into {@code out}.
     *
     * @return true if the walk ended because the endpoint said there was no next
     *         page. False means it ran out of page budget with a live cursor
     *         still in hand, which is not the same thing and must not be
     *         mistaken for one — that mistake is what let a truncated list be
     *         committed as a complete one.
     */
    private boolean walkOnce(String uid, String kind, int pageSize, Map<String, String> out)
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

            // Reported after the merge, so the number is unique accounts held
            // rather than rows seen: the followers endpoint repeats itself
            // across passes and a raw row count would climb past the real total.
            pageListener.onPage(kind, out.size());

            JsonElement next = body.get("next_max_id");
            if (next == null || next.isJsonNull()) {
                return true;
            }
            maxId = next.getAsString();
            pause();
        }
        return false;
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
