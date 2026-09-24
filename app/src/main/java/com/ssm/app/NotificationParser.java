package com.ssm.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotificationParser {
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?<![\\d,])(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");

    private static final String[] EXPENSE_WORDS = {
            "승인", "결제", "이용", "사용", "출금", "구매", "체크카드", "신용카드"
    };

    private static final String[] INCOME_WORDS = {
            "입금", "급여", "송금 받", "송금받", "이체 받", "이체받"
    };

    private static final String[] CANCEL_WORDS = {
            "승인취소", "결제취소", "취소완료", "승인 취소", "결제 취소", "환불"
    };

    private static final String[] PROMO_WORDS = {
            "쿠폰", "혜택", "이벤트", "광고", "수신거부", "가입하면", "할인쿠폰"
    };

    private NotificationParser() {}

    public static Transaction parse(
            String sourceKey,
            String packageName,
            String title,
            String text,
            String bigText,
            long postTime
    ) {
        String combined = join(title, text, bigText);
        if (combined.trim().isEmpty()) return null;

        String normalized = combined.replace("\n", " ").replaceAll("\\s+", " ").trim();
        boolean financialSource = isFinancialSource(packageName);

        if (!financialSource && isCommerceOrPromo(packageName, normalized)) return null;

        Matcher matcher = AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.find()) return null;

        boolean cancel = containsAny(normalized, CANCEL_WORDS);
        boolean income = containsAny(normalized, INCOME_WORDS);
        boolean expense = containsAny(normalized, EXPENSE_WORDS) || cancel;

        if (!income && !expense) return null;
        if (!financialSource && containsAny(normalized, PROMO_WORDS)) return null;

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException error) {
            return null;
        }
        if (amount <= 0L) return null;

        String type = income && !expense ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
        String merchant = extractMerchant(title, text, normalized, matcher.group());
        String paymentMethod = PaymentMethodClassifier.classify(packageName, normalized);
        String category = CategoryClassifier.classify(merchant, normalized);
        String status = cancel ? Transaction.STATUS_CANCELLED : Transaction.STATUS_NORMAL;
        double confidence = financialSource ? 0.96 : 0.80;

        String fallbackKey = safe(packageName) + ":" + postTime + ":" + amount + ":" + merchant;
        String key = safe(sourceKey).trim().isEmpty() ? fallbackKey : sourceKey;
        String transactionId = TransactionIdentity.create(key, packageName, amount, type, postTime, merchant);

        return new Transaction(
                0L,
                transactionId,
                key,
                safe(packageName),
                merchant,
                MerchantNormalizer.normalize(merchant),
                amount,
                type,
                postTime,
                normalized,
                paymentMethod,
                category,
                status,
                confidence,
                null,
                null
        );
    }

    private static boolean isFinancialSource(String packageName) {
        String pkg = safe(packageName).toLowerCase(Locale.ROOT);
        return pkg.contains("card")
                || pkg.contains("bank")
                || pkg.contains("samsung.android.spay")
                || pkg.contains("kakaopay")
                || pkg.contains("naverfin")
                || pkg.contains("payco")
                || pkg.contains("woori")
                || pkg.contains("shinhan")
                || pkg.contains("kb")
                || pkg.contains("hana")
                || pkg.contains("nh");
    }

    private static boolean isCommerceOrPromo(String packageName, String text) {
        String pkg = safe(packageName).toLowerCase(Locale.ROOT);
        if (pkg.contains("coupang") || pkg.contains("shopping") || pkg.contains("market")) return true;
        return containsAny(text, PROMO_WORDS) && !containsAny(text, INCOME_WORDS);
    }

    private static String extractMerchant(String title, String text, String combined, String amountToken) {
        String safeTitle = safe(title).trim();
        String safeText = safe(text).trim();

        boolean providerTitle = looksLikeProviderTitle(safeTitle);
        String candidate = providerTitle && !safeText.isEmpty()
                ? safeText
                : (!safeTitle.isEmpty() ? safeTitle : safeText);
        if (candidate.trim().isEmpty()) candidate = combined;

        candidate = candidate
                .replace(amountToken, " ")
                .replaceAll("(?i)\\b(현대|우리|신한|국민|kb|하나|nh|롯데|삼성)\\s*카드\\b", " ")
                .replaceAll("(?i)카카오뱅크|삼성페이", " ")
                .replaceAll("(?i)잔액\\s*[0-9,]+원", " ");

        String cleaned = MerchantNormalizer.cleanDisplayName(candidate);
        if ("거래".equals(cleaned) && !safeText.isEmpty() && !safeText.equals(candidate)) {
            cleaned = MerchantNormalizer.cleanDisplayName(safeText.replace(amountToken, " "));
        }
        return cleaned;
    }

    private static boolean looksLikeProviderTitle(String title) {
        String normalized = safe(title).toLowerCase(Locale.KOREA)
                .replaceAll("[\\[\\](){}]", " ")
                .trim();
        return normalized.isEmpty()
                || normalized.contains("카드")
                || normalized.contains("은행")
                || normalized.contains("뱅크")
                || normalized.contains("삼성페이")
                || normalized.contains("pay")
                || normalized.contains("페이");
    }

    private static boolean containsAny(String text, String[] words) {
        String lower = text.toLowerCase(Locale.KOREA);
        for (String word : words) {
            if (lower.contains(word.toLowerCase(Locale.KOREA))) return true;
        }
        return false;
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (builder.length() > 0) builder.append(' ');
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
