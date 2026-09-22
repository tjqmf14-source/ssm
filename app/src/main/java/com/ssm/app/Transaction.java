package com.ssm.app;

public final class Transaction {
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";

    public final long id;
    public final String sourceKey;
    public final String sourcePackage;
    public final String merchant;
    public final long amount;
    public final String type;
    public final long occurredAt;
    public final String rawText;
    public final Long calendarEventId;

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
        this.id = id;
        this.sourceKey = sourceKey;
        this.sourcePackage = sourcePackage;
        this.merchant = merchant;
        this.amount = amount;
        this.type = type;
        this.occurredAt = occurredAt;
        this.rawText = rawText;
        this.calendarEventId = calendarEventId;
    }

    public boolean isExpense() {
        return TYPE_EXPENSE.equals(type);
    }
}
