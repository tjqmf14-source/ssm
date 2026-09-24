package com.ssm.app;

import java.util.Locale;

public final class PaymentMethodClassifier {
    private PaymentMethodClassifier() {}

    public static String classify(String packageName, String rawText) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.KOREA);

        if (pkg.contains("samsung.android.spay") || text.contains("삼성페이")) return "삼성페이";
        if (text.contains("네이버페이") || pkg.contains("naver") && text.contains("페이")) return "네이버페이";
        if (text.contains("현금")) return "현금";
        if (text.contains("카드") || text.contains("승인") || pkg.contains("card")) return "카드";
        return "기타";
    }
}
