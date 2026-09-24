package com.ssm.app;

public final class RetryPolicy {
    private RetryPolicy(){}
    public static long delayMillis(int attempts){int n=Math.max(0,Math.min(attempts,8));long delay=30_000L*(1L<<n);return Math.min(delay,6L*60L*60L*1000L);}
}
