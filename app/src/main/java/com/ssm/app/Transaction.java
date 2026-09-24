package com.ssm.app;

public final class Transaction {
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";

    public static final String STATUS_NORMAL = "normal";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_DELETED = "deleted";

    public final long id;
    public final String transactionId;
    public final String sourceKey;
    public final String sourcePackage;
    public final String merchant;
    public final String normalizedMerchant;
    public final long amount;
    public final String type;
    public final long occurredAt;
    public final String rawText;
    public final String paymentMethod;
    public final String category;
    public final String status;
    public final double confidence;
    public final Long calendarEventId;
    public final String notionPageId;

    public Transaction(
            long id,
            String transactionId,
            String sourceKey,
            String sourcePackage,
            String merchant,
            String normalizedMerchant,
            long amount,
            String type,
            long occurredAt,
            String rawText,
            String paymentMethod,
            String category,
            String status,
            double confidence,
            Long calendarEventId,
            String notionPageId
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.sourceKey = sourceKey;
        this.sourcePackage = sourcePackage;
        this.merchant = merchant;
        this.normalizedMerchant = normalizedMerchant;
        this.amount = amount;
        this.type = type;
        this.occurredAt = occurredAt;
        this.rawText = rawText;
        this.paymentMethod = paymentMethod;
        this.category = category;
        this.status = status;
        this.confidence = confidence;
        this.calendarEventId = calendarEventId;
        this.notionPageId = notionPageId;
    }

    // Compatibility constructor for legacy/manual call sites while 2.0 UI is being replaced.
    public Transaction(
            long id,
            String sourceKey,
            String sourcePackage,
            String merchant,
            long amount,
            String type,
            long occurredAt,
            String rawText,
            Long calendarEventId
    ) {
        this(
                id,
                TransactionIdentity.create(sourceKey, sourcePackage, amount, type, occurredAt, merchant),
                sourceKey,
                sourcePackage,
                merchant,
                MerchantNormalizer.normalize(merchant),
                amount,
                type,
                occurredAt,
                rawText,
                PaymentMethodClassifier.classify(sourcePackage, rawText),
                CategoryClassifier.classify(merchant, rawText),
                STATUS_NORMAL,
                1.0,
                calendarEventId,
                null
        );
    }

    public boolean isExpense() {
        return TYPE_EXPENSE.equals(type);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isDeleted() {
        return STATUS_DELETED.equals(status);
    }
}
