package com.kira.followlens.scan;

/** Indirection over the system clock so cooldown logic is testable. */
public interface Clock {

    Clock SYSTEM = System::currentTimeMillis;

    long nowMillis();
}
