package com.ssm.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class NotificationParserTest {
 @Test public void samsung(){Transaction t=NotificationParser.parse("k1","com.samsung.android.spay","삼성월렛","₩990 결제 완료 스타벅스","",1700000000000L);assertNotNull(t);assertEquals(990,t.amount);assertEquals("삼성페이",t.paymentMethod);}
 @Test public void card(){Transaction t=NotificationParser.parse("k2","card","현대카드","12,300원 일시불 스타벅스 승인","",1700000000000L);assertNotNull(t);assertEquals(12300,t.amount);assertEquals("카페",t.category);}
 @Test public void income(){Transaction t=NotificationParser.parse("k3","bank","카카오뱅크","홍길동 입금 250,000원","",1700000000000L);assertNotNull(t);assertEquals(Transaction.TYPE_INCOME,t.type);}
 @Test public void cancellation(){Transaction t=NotificationParser.parse("k4","card","승인취소","5,000원 결제 취소 완료","",1700000000000L);assertNotNull(t);assertEquals(Transaction.TYPE_INCOME,t.type);}
 @Test public void ignoresDate(){Transaction t=NotificationParser.parse("k6","card","현대카드","2026년 9월 24일 승인 12,300원 스타벅스","",1700000000000L);assertNotNull(t);assertEquals(12300,t.amount);}
 @Test public void couponIgnored(){assertNull(NotificationParser.parse("k5","shop","쿠폰 도착","10,000원 쿠폰을 확인하세요","",1700000000000L));}
}