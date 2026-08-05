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
