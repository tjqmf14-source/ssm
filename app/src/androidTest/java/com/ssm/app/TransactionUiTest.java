package com.ssm.app;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class TransactionUiTest {
    @Test public void editedManualTransactionAppearsWithNewAmountAndType() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        try (TransactionDb db = new TransactionDb(context)) {
            db.getWritableDatabase().delete("delete_queue", null, null);
            db.getWritableDatabase().delete("sync_targets", null, null);
            db.getWritableDatabase().delete("transactions", null, null);
        }
        Transaction tx = new TransactionRepository(context).createManual(1200,
                Transaction.TYPE_EXPENSE, "수정 테스트 상점", "생활", "카드");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("수정")).perform(scrollTo(), click());
            onView(withHint("금액")).perform(replaceText("2500"));
            onView(withContentDescription("거래 유형")).perform(click());
            onData(allOf(is(instanceOf(String.class)), is("입금"))).perform(click());
            onView(withHint("거래 시각 (YYYY-MM-DD HH:mm)"))
                    .perform(replaceText("2026-09-20 09:30"));
            onView(withText("저장")).perform(click());
            try (TransactionDb db = new TransactionDb(context)) {
                Transaction updated = db.find(tx.transactionId);
                assertNotNull(updated);
                assertEquals(2500L, updated.amount);
                assertEquals(Transaction.TYPE_INCOME, updated.type);
                String formatted = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                        .format(new Date(updated.occurredAt));
                assertEquals("2026-09-20 09:30", formatted);
            }
        }
    }
}
