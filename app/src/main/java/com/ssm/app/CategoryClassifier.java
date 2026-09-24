package com.ssm.app;

import java.util.Locale;

public final class CategoryClassifier {
    private CategoryClassifier() {}

    public static String classify(String merchant, String rawText) {
        String text = ((merchant == null ? "" : merchant) + " " + (rawText == null ? "" : rawText))
                .toLowerCase(Locale.KOREA);

        if (contains(text, "스타벅스", "커피", "카페", "메가커피", "컴포즈", "빽다방", "페이타랩")) return "카페";
        if (contains(text, "cu", "씨유", "gs25", "세븐일레븐", "이마트24", "식당", "김밥", "만두", "치킨", "피자", "버거", "맘스터치", "배달", "보쌈", "마트")) return "식비";
        if (contains(text, "버스", "지하철", "택시", "카카오t", "주차", "톨게이트", "코레일", "srt")) return "교통";
        if (contains(text, "넷플릭스", "유튜브", "spotify", "스포티파이", "구독", "멤버십")) return "구독";
        if (contains(text, "병원", "약국", "의원", "치과", "의료")) return "의료";
        if (contains(text, "다이소", "쿠팡", "11번가", "g마켓", "옥션", "쇼핑", "무신사")) return "쇼핑";
        if (contains(text, "관리비", "전기", "가스", "수도", "통신", "보험", "세탁", "생활")) return "생활";
        return "기타";
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
