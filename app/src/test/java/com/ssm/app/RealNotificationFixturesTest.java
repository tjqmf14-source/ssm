package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RealNotificationFixturesTest {
    @Test
    public void wooriCardExtractsStoreInsteadOfCardBrand() {
        Transaction tx = NotificationParser.parse(
                "woori-1",
                "com.wooricard.smartapp",
                "[우리카드]",
                "2,900원 일시불 씨유(CU) 범천점 승인",
                "",
                1_795_000_000_000L
        );
        assertNotNull(tx);
        assertEquals("씨유(CU) 범천점", tx.merchant);
        assertEquals("식비", tx.category);
    }

    @Test
    public void samsungPayRemovesCompletionNoise() {
        Transaction tx = NotificationParser.parse(
                "spay-1",
                "com.samsung.android.spay",
                "삼성페이",
                "5,000원 결제 완료 주식회사 아성다이소",
                "",
                1_795_000_000_000L
        );
        assertNotNull(tx);
        assertEquals("아성다이소", tx.merchant);
        assertEquals("삼성페이", tx.paymentMethod);
        assertEquals("쇼핑", tx.category);
    }

    @Test
    public void kakaobankRemovesLeadingAccountMarker() {
        Transaction tx = NotificationParser.parse(
                "kakao-1",
                "com.kakaobank.channel",
                "카카오뱅크",
                "29,000원 출금 (0315) | 구글페이먼트코리아",
                "",
                1_795_000_000_000L
        );
        assertNotNull(tx);
        assertEquals("구글페이먼트코리아", tx.merchant);
    }

    @Test
    public void coupangMarketingFixtureIsRejected() {
        Transaction tx = NotificationParser.parse(
                "coupang-1",
                "com.coupang.mobile",
                "쿠팡",
                "4,000원 쿠폰, 와우 가입하면 바로 가능 [수신거부:마이쿠팡]",
                "",
                1_795_000_000_000L
        );
        assertNull(tx);
    }

    @Test
    public void apartmentManagementFeeIsLiving() {
        Transaction tx = NotificationParser.parse(
                "woori-2",
                "com.wooricard.smartapp",
                "[우리카드]",
                "193,120원 일시불 아파트관리비-26년08월-0807호 승인",
                "",
                1_795_000_000_000L
        );
        assertNotNull(tx);
        assertEquals("아파트관리비-26년08월-0807호", tx.merchant);
        assertEquals("생활", tx.category);
    }
}
