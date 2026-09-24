package com.ssm.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotificationParser {
    private static final Pattern AMOUNT = Pattern.compile("(?:(?:₩|￦|KRW\\s*)(\\d{1,3}(?:,\\d{3})+|\\d+)|(?<![\\d,])(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원)", Pattern.CASE_INSENSITIVE);
    private static final String[] EXPENSE = {"승인", "결제", "이용", "사용", "출금", "구매", "체크카드", "신용카드", "결제 완료"};
    private static final String[] INCOME = {"입금", "급여", "송금 받", "송금받", "이체 받", "이체받"};
    private static final String[] CANCEL = {"승인취소", "결제취소", "취소완료", "승인 취소", "결제 취소", "취소 승인"};
    private static final String[] NON_TRANSACTION = {"쿠폰", "포인트 적립 예정", "혜택", "이벤트", "광고"};
    private NotificationParser() {}
    public static Transaction parse(String sourceKey,String packageName,String title,String text,String bigText,long postTime){
        String normalized=join(title,text,bigText).replace('\n',' ').replaceAll("\\s+"," ").trim();
        if(normalized.isBlank())return null;
        boolean cancelled=containsAny(normalized,CANCEL),income=containsAny(normalized,INCOME),expense=containsAny(normalized,EXPENSE);
        if(!income&&!expense&&!cancelled)return null;
        if(!income&&!expense&&containsAny(normalized,NON_TRANSACTION))return null;
        Matcher m=AMOUNT.matcher(normalized);Long amount=null;String token=null;
        while(m.find()){String digits=m.group(1)!=null?m.group(1):m.group(2);try{long n=Long.parseLong(digits.replace(",",""));if(n>0){amount=n;token=m.group();break;}}catch(NumberFormatException ignored){}}
        if(amount==null)return null;
        String type=cancelled?Transaction.TYPE_INCOME:(income?Transaction.TYPE_INCOME:Transaction.TYPE_EXPENSE);
        String merchant=extractMerchant(title,text,normalized,token),category=CategoryClassifier.classify(merchant,normalized),paymentMethod=CategoryClassifier.paymentMethod(packageName,normalized);
        String stableSourceKey=sourceKey==null||sourceKey.isBlank()?safe(packageName)+":"+postTime+":"+amount:sourceKey;
        String transactionId=TransactionId.create(packageName,stableSourceKey,amount,type,postTime,merchant);
        return new Transaction(0L,transactionId,stableSourceKey,safe(packageName),merchant,amount,type,category,paymentMethod,postTime,normalized,false);
    }
    private static String extractMerchant(String title,String text,String all,String amountToken){String candidate=!safe(title).isBlank()?safe(title):safe(text);if(candidate.isBlank())candidate=all;if(amountToken!=null)candidate=candidate.replace(amountToken," ");candidate=candidate.replaceAll("[\\[\\](){}]"," ").replaceAll("(?i)승인취소|결제취소|취소완료|승인|결제|이용|사용|출금|구매|입금|급여|체크카드|신용카드|완료"," ").replaceAll("\\s+"," ").trim();if(candidate.length()>48)candidate=candidate.substring(0,48).trim();return candidate.isBlank()?"거래":candidate;}
    private static boolean containsAny(String text,String[] words){String lower=text.toLowerCase(Locale.KOREA);for(String word:words)if(lower.contains(word.toLowerCase(Locale.KOREA)))return true;return false;}
    private static String join(String... values){StringBuilder b=new StringBuilder();for(String v:values)if(v!=null&&!v.isBlank()){if(b.length()>0)b.append(' ');b.append(v);}return b.toString();}
    private static String safe(String value){return value==null?"":value;}
}
