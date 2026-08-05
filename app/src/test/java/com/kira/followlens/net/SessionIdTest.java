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
