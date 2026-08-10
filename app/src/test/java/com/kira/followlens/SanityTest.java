package com.kira.followlens;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanityTest {

    /**
     * Guards the source level, not one specific JDK: the code compiles against
     * Java 17 and any later JDK can run it. Pinning this to an exact major
     * version fails the build on a machine that happens to have a different
     * JDK installed, which says nothing about whether the app is correct.
     */
    @Test
    public void javaVersionIsAtLeast17() {
        int major = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]);
        assertTrue("expected JDK 17 or newer, got " + major, major >= 17);
    }
}
