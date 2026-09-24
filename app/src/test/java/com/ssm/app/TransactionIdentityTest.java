package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TransactionIdentityTest {
    @Test
    public void identityIsDeterministic() {
        String a = TransactionIdentity.create("key", "pkg", 5000, "expense", 1000L, "스타벅스");
        String b = TransactionIdentity.create("key", "pkg", 5000, "expense", 1000L, "스타벅스");
        assertEquals(a, b);
        assertTrue(a.startsWith("ssm2_"));
    }

    @Test
    public void identityChangesWhenSourceEventChanges() {
        String a = TransactionIdentity.create("key-a", "pkg", 5000, "expense", 1000L, "스타벅스");
        String b = TransactionIdentity.create("key-b", "pkg", 5000, "expense", 1000L, "스타벅스");
        assertNotEquals(a, b);
    }
}
