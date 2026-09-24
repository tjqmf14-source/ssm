package com.ssm.app;

import java.util.Locale;

public final class CategoryClassifier {
    private CategoryClassifier() {}

    public static String classify(String merchant, String rawText) {
        String t = ((merchant == null ? "" : merchant) + " " + (rawText == null ? "" : rawText)).toLowerCase(Locale.KOREA);
        if (contains(t, "스타벅스", "투썸", "커피", "카페", "메가커피", "컴포즈")) return "카페";
        if (contains(t, "버스", "지하철", "택시", "코레일", "srt", "주차", "티머니")) return "교통";
        if (contains(t, "쿠팡", "11번가", "g마켓", "옥션", "무신사", "쇼핑")) return "쇼핑";
        if (contains(t, "넷플릭스", "유튜브", "spotify", "구독", "google one", "icloud")) return "구독";
        if (contains(t, "병원", "의원", "약국", "치과", "한의원")) return "의료";
        if (contains(t, "마트", "편의점", "다이소", "이마트", "홈플러스", "세븐일레븐", "gs25")
                || containsAsciiToken(t, "cu")) return "생활";
        if (contains(t, "식당", "김밥", "치킨", "피자", "버거", "배달", "요기요", "배민", "맥도날드", "롯데리아")) return "식비";
        return "기타";
    }

    public static String paymentMethod(String sourcePackage, String rawText) {
        String t = ((sourcePackage == null ? "" : sourcePackage) + " " + (rawText == null ? "" : rawText)).toLowerCase(Locale.ROOT);
        if (contains(t, "samsung", "삼성월렛", "삼성페이")) return "삼성페이";
        if (contains(t, "naver", "네이버페이")) return "네이버페이";
        if (contains(t, "현금")) return "현금";
        if (contains(t, "카드", "승인", "체크", "신용")) return "카드";
        return "기타";
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean containsAsciiToken(String text, String word) {
        String needle = word.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= text.length() - needle.length()) {
            int at = text.indexOf(needle, from);
            if (at < 0) return false;
            int end = at + needle.length();
            boolean leftOk = at == 0 || !isAsciiLetterOrDigit(text.charAt(at - 1));
            boolean rightOk = end == text.length() || !isAsciiLetterOrDigit(text.charAt(end));
            if (leftOk && rightOk) return true;
            from = at + 1;
        }
        return false;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
