package com.ssm.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PrivacyPolicyTest {
    @Test public void localFinancialDataIsExcludedFromAndroidBackup() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(0, context.getApplicationInfo().flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
    }

    @Test public void notionTokenIsEncryptedAtRestAndCanBeRemoved() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String token = "test-secret-not-a-real-integration-token";
        try {
            SecretStore.saveNotionToken(context, token);
            assertEquals(token, SecretStore.getNotionToken(context));
            String stored = context.getSharedPreferences("ssm_secure", Context.MODE_PRIVATE)
                    .getString("notion", "");
            assertNotNull(stored);
            assertFalse(stored.contains(token));
        } finally {
            SecretStore.saveNotionToken(context, "");
        }
        assertNull(SecretStore.getNotionToken(context));
    }
}
