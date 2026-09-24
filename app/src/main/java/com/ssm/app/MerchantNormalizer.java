package com.ssm.app;

import java.text.Normalizer;
import java.util.Locale;

public final class MerchantNormalizer {
    private MerchantNormalizer() {}

    public static String cleanDisplayName(String value) {
        String text = value == null ? "" : value;
        text = text
                .replaceAll("[\\[\\]{}]", " ")
                .replaceAll("(?i)승인취소|결제취소|취소완료|승인|결제|이용|사용|출금|입금|급여|체크카드|신용카드|일시불|완료", " ")
                .replaceAll("(?i)할부\\s*\\d+\\s*개월", " ")
                .replaceAll("(?i)승인번호\\s*[:：]?\\s*[0-9A-Za-z-]+", " ")
                .replaceAll("(?i)잔액\\s*[0-9,]+원", " ")
                .replaceAll("(?i)주식회사|㈜", " ")
                .replaceAll("^\\s*\\(?\\d{2,4}\\)?\\s*[|·:-]?\\s*", " ")
                .replaceAll("\\b\\d{2,4}[-*xX]?\\d{2,4}\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > 48) text = text.substring(0, 48).trim();
        return text.isEmpty() ? "거래" : text;
    }

    public static String normalize(String value) {
        return Normalizer.normalize(cleanDisplayName(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.KOREA)
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    public static boolean similar(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a))) return true;

        int common = 0;
        int min = Math.min(a.length(), b.length());
        for (int i = 0; i < min; i++) {
            if (a.charAt(i) == b.charAt(i)) common++;
            else break;
        }
        return common >= 5;
    }
}
