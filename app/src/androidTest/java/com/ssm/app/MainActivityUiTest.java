package com.ssm.app;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.startsWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityUiTest {
    private Context context;

    @Before public void prepareTransaction() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("ssm.db");
        new TransactionRepository(context).createManual(
                12_000L, Transaction.TYPE_EXPENSE, "UI 테스트 상점", "식비", "카드");
    }

    @Test public void editDialogExposesAmountTypeMerchantAndTimeControls() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Espresso.onView(withText("UI 테스트 상점")).check(matches(isDisplayed()));
            Espresso.onView(withText("수정")).perform(click());

            Espresso.onView(withText("거래 수정")).check(matches(isDisplayed()));
            Espresso.onView(withHint("금액")).check(matches(withText("12000")));
            Espresso.onView(withHint("사용처 / 보낸 사람")).check(matches(withText("UI 테스트 상점")));
            Espresso.onView(withText(startsWith("거래 시간 · "))).check(matches(isDisplayed()));
        }
    }
}
