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
