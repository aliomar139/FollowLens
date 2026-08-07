package com.kira.followlens.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonogramTest {

    @Test
    public void usesTheFirstLetterUppercased() {
        assertEquals("A", Monogram.initialOf("alice"));
        assertEquals("B", Monogram.initialOf("Bob"));
    }

    @Test
    public void skipsLeadingPunctuationAndUnderscores() {
        assertEquals("X", Monogram.initialOf("._xavier"));
        assertEquals("Q", Monogram.initialOf("__queen"));
    }

    @Test
    public void fallsBackToAPlaceholderForDigitsOnlyOrEmpty() {
        assertEquals("#", Monogram.initialOf("123"));
        assertEquals("?", Monogram.initialOf(""));
        assertEquals("?", Monogram.initialOf(null));
    }

    @Test
    public void tintIsStableForTheSameAccount() {
        // The colour must not change between renders, or scrolling looks broken.
        assertEquals(Monogram.tintIndexOf("6633301528"), Monogram.tintIndexOf("6633301528"));
    }

    @Test
    public void tintIsWithinTheAvailableRange() {
        for (String id : new String[]{"1", "9999999999", "abc", "", null}) {
            int index = Monogram.tintIndexOf(id);
            assertTrue("index out of range for " + id,
                    index >= 0 && index < Monogram.TINT_COUNT);
        }
    }

    @Test
    public void differentAccountsUsuallyDifferInTint() {
        // Not a guarantee for any given pair, but the spread must not be constant.
        boolean anyDifferent = false;
        int first = Monogram.tintIndexOf("1000");
        for (String id : new String[]{"1001", "1002", "1003", "1004", "1005"}) {
            if (Monogram.tintIndexOf(id) != first) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent);
    }
}
