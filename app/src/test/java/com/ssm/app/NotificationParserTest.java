package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NotificationParserTest {
    @Test
    public void parsesCardExpense() {
        Transaction result = NotificationParser.parse(
                "key-1",
                "com.example.card",
                "[현대카드]",
                "12,300원 일시불 스타벅스 승인",
                "",
                1_700_000_000_000L
        );

        assertNotNull(result);
        assertEquals(12_300L, result.amount);
        assertEquals(Transaction.TYPE_EXPENSE, result.type);
    }

    @Test
    public void parsesBankIncome() {
        Transaction result = NotificationParser.parse(
                "key-2",
                "com.example.bank",
                "카카오뱅크",
                "홍길동 입금 250,000원",
                "",
                1_700_000_000_000L
        );

        assertNotNull(result);
        assertEquals(250_000L, result.amount);
        assertEquals(Transaction.TYPE_INCOME, result.type);
    }

    @Test
    public void ignoresNonTransactionWonText() {
        Transaction result = NotificationParser.parse(
                "key-3",
                "com.example.shopping",
                "쿠폰 도착",
                "10,000원 쿠폰을 확인하세요",
                "",
                1_700_000_000_000L
        );

        assertNull(result);
    }

    @Test
    public void ignoresCancellationToAvoidDoubleCounting() {
        Transaction result = NotificationParser.parse(
                "key-4",
                "com.example.card",
                "승인취소",
                "5,000원 결제 취소 완료",
                "",
                1_700_000_000_000L
        );

        assertNull(result);
    }

    @Test
    public void dedupKeyChangesWithTypeAndAmount() {
        Transaction expense = NotificationParser.parse(
                "same-key",
                "com.example.card",
                "카드 승인",
                "8,500원 사용",
                "",
                1L
        );
        Transaction income = NotificationParser.parse(
                "same-key",
                "com.example.bank",
                "입금",
                "9,000원 입금",
                "",
                2L
        );

        assertNotNull(expense);
        assertNotNull(income);
        assertEquals("same-key:8500:expense", expense.sourceKey);
        assertEquals("same-key:9000:income", income.sourceKey);
    }
}
