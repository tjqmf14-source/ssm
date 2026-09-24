package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NotificationParserTest {
    @Test
    public void parsesCardExpense() {
        Transaction result = NotificationParser.parse(
                "key-1",
                "com.wooricard.smartapp",
                "[우리카드]",
                "12,300원 일시불 스타벅스 승인",
                "",
                1_700_000_000_000L
        );

        assertNotNull(result);
        assertEquals(12_300L, result.amount);
        assertEquals(Transaction.TYPE_EXPENSE, result.type);
        assertEquals("카드", result.paymentMethod);
        assertEquals("카페", result.category);
        assertTrue(result.transactionId.startsWith("ssm2_"));
    }

    @Test
    public void parsesBankIncome() {
        Transaction result = NotificationParser.parse(
                "key-2",
                "com.kakaobank.channel",
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
    public void ignoresCoupangCouponPromoEvenWithWonAmount() {
        Transaction result = NotificationParser.parse(
                "key-promo",
                "com.coupang.mobile",
                "쿠팡",
                "4,000원 쿠폰, 와우 가입하면 바로 가능 [수신거부:마이쿠팡]",
                "",
                1_700_000_000_000L
        );

        assertNull(result);
    }

    @Test
    public void parsesSamsungPayExpense() {
        Transaction result = NotificationParser.parse(
                "key-spay",
                "com.samsung.android.spay",
                "삼성페이",
                "5,000원 결제 완료 주식회사 아성다이소",
                "",
                1_700_000_000_000L
        );

        assertNotNull(result);
        assertEquals("삼성페이", result.paymentMethod);
        assertEquals("쇼핑", result.category);
    }

    @Test
    public void cancellationIsRetainedAsCancellationEvent() {
        Transaction result = NotificationParser.parse(
                "key-cancel",
                "com.wooricard.smartapp",
                "우리카드",
                "5,000원 결제 취소 완료 아성다이소",
                "",
                1_700_000_000_000L
        );

        assertNotNull(result);
        assertTrue(result.isCancelled());
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
}
