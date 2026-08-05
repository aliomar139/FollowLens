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
