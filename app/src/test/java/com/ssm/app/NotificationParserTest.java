package com.ssm.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationParserTest {
    @Test public void parsesSamsungWalletWonPrefix() {
        Transaction t = NotificationParser.parse("k1","com.samsung.android.spay","삼성월렛","₩990 결제 완료 스타벅스","",1_700_000_000_000L);
        assertNotNull(t); assertEquals(990L,t.amount); assertTrue(t.isExpense()); assertEquals("삼성페이",t.paymentMethod); assertEquals("스타벅스",t.merchant);
    }
    @Test public void parsesCardExpenseAndMerchant() {
        Transaction t = NotificationParser.parse("k2","card","현대카드","12,300원 일시불 스타벅스 승인","",1_700_000_000_000L);
        assertNotNull(t); assertEquals(12_300L,t.amount); assertEquals("카페",t.category); assertEquals("스타벅스",t.merchant);
    }
    @Test public void parsesIncome() {
        Transaction t = NotificationParser.parse("k3","bank","카카오뱅크","홍길동 입금 250,000원","",1_700_000_000_000L);
        assertNotNull(t); assertEquals(Transaction.TYPE_INCOME,t.type); assertEquals(250_000L,t.amount); assertEquals("홍길동",t.merchant);
    }
    @Test public void cancellationBecomesIncomeCorrection() {
        Transaction t = NotificationParser.parse("k4","card","승인취소","5,000원 결제 취소 완료","",1_700_000_000_000L);
        assertNotNull(t); assertEquals(Transaction.TYPE_INCOME,t.type);
    }
    @Test public void ignoresCouponFalsePositive() {
        assertNull(NotificationParser.parse("k5","shop","쿠폰","10,000원 쿠폰 사용 가능","",1_700_000_000_000L));
        assertNull(NotificationParser.parse("k5b","shop","쿠폰","10,000원 쿠폰 결제 시 사용 가능","",1_700_000_000_000L));
    }
    @Test public void ignoresDateNumbersWhenFindingAmount() {
        Transaction t = NotificationParser.parse("k6","card","현대카드","2026년 9월 24일 승인 12,300원 스타벅스","",1_700_000_000_000L);
        assertNotNull(t); assertEquals(12_300L,t.amount);
    }
}
