package com.ssm.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoreLogicTest {
    @Test public void transactionIdIsDeterministicWithinMinute() {
        String a=TransactionId.create("p","k",1200,"expense",120001,"상점");
        String b=TransactionId.create("p","k",1200,"expense",120059,"상점");
        assertEquals(a,b); assertTrue(a.startsWith("tx_"));
    }
    @Test public void transactionIdChangesOnAmount() {
        assertNotEquals(TransactionId.create("p","k",1200,"expense",120001,"상점"), TransactionId.create("p","k",1300,"expense",120001,"상점"));
    }
    @Test public void distinctNotificationKeysDoNotCollapse() {
        assertNotEquals(TransactionId.create("p","k1",1200,"expense",120001,"상점"), TransactionId.create("p","k2",1200,"expense",120020,"상점"));
    }
    @Test public void missingNotificationKeyFallsBackToMinuteBucket() {
        assertEquals(TransactionId.create("p","",1200,"expense",120001,"상점"), TransactionId.create("p",null,1200,"expense",120059,"상점"));
    }
    @Test public void classifiesKnownCategories() {
        assertEquals("교통",CategoryClassifier.classify("부산 지하철","승인"));
        assertEquals("의료",CategoryClassifier.classify("행복약국","결제"));
        assertEquals("기타",CategoryClassifier.classify("알수없음","결제"));
    }
    @Test public void retryBackoffIsBounded() {
        assertEquals(30_000L,RetryPolicy.delayMillis(0));
        assertTrue(RetryPolicy.delayMillis(20)<=6L*60L*60L*1000L);
    }
}
