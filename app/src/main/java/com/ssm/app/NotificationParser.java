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

    private static final String[] IGNORE_WORDS = {
            "승인취소", "결제취소", "취소완료", "승인 취소", "결제 취소"
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
        if (combined.isBlank()) {
            return null;
        }

        String normalized = combined.replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (containsAny(normalized, IGNORE_WORDS)) {
            return null;
        }

        Matcher matcher = AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        boolean income = containsAny(normalized, INCOME_WORDS);
        boolean expense = containsAny(normalized, EXPENSE_WORDS);
        if (!income && !expense) {
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException error) {
            return null;
        }

        if (amount <= 0L) {
            return null;
        }

        String type = income ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
        String merchant = extractMerchant(title, text, normalized, matcher.group());
        String key = (sourceKey == null || sourceKey.isBlank())
                ? packageName + ":" + postTime + ":" + amount + ":" + merchant
                : sourceKey + ":" + amount + ":" + type;

        return new Transaction(
                0L,
                key,
                safe(packageName),
                merchant,
                amount,
                type,
                postTime,
                normalized,
                null
        );
    }

    private static String extractMerchant(String title, String text, String combined, String amountToken) {
        String candidate = !safe(title).isBlank() ? safe(title) : safe(text);
        if (candidate.isBlank()) {
            candidate = combined;
        }

        candidate = candidate
                .replace(amountToken, "")
                .replaceAll("[\\[\\](){}]", " ")
                .replaceAll("(?i)승인|결제|이용|사용|출금|구매|입금|급여|체크카드|신용카드", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (candidate.length() > 40) {
            candidate = candidate.substring(0, 40).trim();
        }
        return candidate.isBlank() ? "거래" : candidate;
    }

    private static boolean containsAny(String text, String[] words) {
        String lower = text.toLowerCase(Locale.KOREA);
        for (String word : words) {
            if (lower.contains(word.toLowerCase(Locale.KOREA))) {
                return true;
            }
        }
        return false;
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
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
