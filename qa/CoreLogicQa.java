import com.ssm.app.*;
public class CoreLogicQa {
 private static int passed=0;
 public static void main(String[] args){
  check("Samsung Wallet",()->{Transaction t=NotificationParser.parse("k1","com.samsung.android.spay","삼성월렛","₩990 결제 완료 스타벅스","",1700000000000L);return t!=null&&t.amount==990&&t.isExpense();});
  check("Card expense",()->{Transaction t=NotificationParser.parse("k2","card","현대카드","12,300원 스타벅스 승인","",1700000000000L);return t!=null&&t.amount==12300&&"카페".equals(t.category);});
  check("Bank income",()->{Transaction t=NotificationParser.parse("k3","bank","카카오뱅크","홍길동 입금 250,000원","",1700000000000L);return t!=null&&t.amount==250000&&Transaction.TYPE_INCOME.equals(t.type);});
  check("Coupon blocked",()->NotificationParser.parse("k4","shop","쿠폰","10,000원 쿠폰을 확인하세요","",1700000000000L)==null);
  check("Cancellation",()->{Transaction t=NotificationParser.parse("k5","card","승인취소","5,000원 결제 취소 완료","",1700000000000L);return t!=null&&Transaction.TYPE_INCOME.equals(t.type);});
  check("Deterministic id",()->TransactionId.create("p","k",1000,"expense",120001,"A").equals(TransactionId.create("p","k",1000,"expense",120059,"A")));
  check("Amount distinction",()->!TransactionId.create("p","k",1000,"expense",120001,"A").equals(TransactionId.create("p","k",2000,"expense",120001,"A")));
  check("Transport",()->"교통".equals(CategoryClassifier.classify("부산 지하철","승인")));
  check("Medical",()->"의료".equals(CategoryClassifier.classify("약국","결제")));
  check("Date ignored",()->{Transaction t=NotificationParser.parse("k6","card","현대카드","2026년 9월 24일 승인 12,300원 스타벅스","",1700000000000L);return t!=null&&t.amount==12300;});
  check("Retry cap",()->RetryPolicy.delayMillis(20)<=21600000L);
  System.out.println("CORE_QA_PASS="+passed+"/11");
 }
 interface Test {boolean run() throws Exception;}
 static void check(String n,Test t){try{if(!t.run())throw new AssertionError(n);passed++;System.out.println("PASS: "+n);}catch(Exception|AssertionError e){System.err.println("FAIL: "+n);System.exit(1);}}
}
