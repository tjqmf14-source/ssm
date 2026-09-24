package com.ssm.app;

public final class Transaction {
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";
    public final long id;
    public final String transactionId;
    public final String sourceKey;
    public final String sourcePackage;
    public final String merchant;
    public final long amount;
    public final String type;
    public final String category;
    public final String paymentMethod;
    public final long occurredAt;
    public final String rawText;
    public final boolean manual;
    public Transaction(long id,String transactionId,String sourceKey,String sourcePackage,String merchant,long amount,String type,String category,String paymentMethod,long occurredAt,String rawText,boolean manual){this.id=id;this.transactionId=transactionId;this.sourceKey=sourceKey;this.sourcePackage=sourcePackage;this.merchant=merchant;this.amount=amount;this.type=type;this.category=category;this.paymentMethod=paymentMethod;this.occurredAt=occurredAt;this.rawText=rawText;this.manual=manual;}
    public boolean isExpense(){return TYPE_EXPENSE.equals(type);}
}
