package com.ssm.app;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RealDeviceProbeInstrumentedTest {
    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test public void notificationProbeStoredExpectedTransaction() {
        try (TransactionDb db = new TransactionDb(context())) {
            List<Transaction> rows = db.getRecent(20);
            assertEquals("exactly one notification transaction expected", 1, rows.size());
            Transaction tx = rows.get(0);
            assertEquals(12_300L, tx.amount);
            assertEquals(Transaction.TYPE_EXPENSE, tx.type);
            assertTrue("merchant=" + tx.merchant, tx.merchant.contains("스타벅스"));
            assertEquals("카페", tx.category);
            assertEquals("카드", tx.paymentMethod);
            assertFalse(tx.manual);
        }
    }

    @Test public void duplicateGuardStillSingle() {
        try (TransactionDb db = new TransactionDb(context())) {
            List<Transaction> rows = db.getRecent(20);
            assertEquals("duplicate notification must not create a second transaction", 1, rows.size());
        }
    }

    @Test public void externalSyncTargetsAreSyncedWithRemoteIds() {
        Map<String, String[]> byTarget = new HashMap<>();
        try (TransactionDb db = new TransactionDb(context());
             Cursor c = db.getReadableDatabase().rawQuery(
                     "SELECT target,status,COALESCE(remote_id,''),attempts,COALESCE(last_error,'') " +
                             "FROM sync_targets ORDER BY target", null)) {
            while (c.moveToNext()) {
                byTarget.put(c.getString(0), new String[]{
                        c.getString(1), c.getString(2),
                        String.valueOf(c.getInt(3)), c.getString(4)
                });
            }
        }

        String[] required = {
                CalendarSync.TARGET_GOOGLE,
                CalendarSync.TARGET_SAMSUNG,
                NotionSync.TARGET
        };
        for (String target : required) {
            assertTrue("missing sync target: " + target, byTarget.containsKey(target));
            String[] row = byTarget.get(target);
            assertEquals(target + " status / error=" + row[3], "synced", row[0]);
            assertFalse(target + " remote_id missing", row[1] == null || row[1].isBlank());
        }
    }
}
