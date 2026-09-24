package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class NotionPayloadTest {
    @Test
    public void payloadTargetsExistingDataSourceAndCarriesTransactionId() {
        Transaction transaction = new Transaction(
                0L,
                "ssm2_test123",
                "source-key",
                "com.wooricard.smartapp",
                "스타벅스 부산대점",
                MerchantNormalizer.normalize("스타벅스 부산대점"),
                6200L,
                Transaction.TYPE_EXPENSE,
                1_700_000_000_000L,
                "6,200원 승인 스타벅스 부산대점",
                "카드",
                "카페",
                Transaction.STATUS_NORMAL,
                0.96,
                null,
                null
        );

        String payload = NotionSyncClient.buildCreatePayload(transaction);
        assertTrue(payload.contains(NotionSyncClient.DATA_SOURCE_ID));
        assertTrue(payload.contains("ssm2_test123"));
        assertTrue(payload.contains("스타벅스 부산대점"));
        assertTrue(payload.contains("\"금액\":{\"number\":6200}"));
        assertTrue(payload.contains("\"구분\":{\"select\":{\"name\":\"지출\"}}"));
    }
}
