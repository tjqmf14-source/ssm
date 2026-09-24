package com.ssm.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TransactionIdentity {
    private TransactionIdentity() {}

    public static String create(
            String sourceKey,
            String sourcePackage,
            long amount,
            String type,
            long occurredAt,
            String merchant
    ) {
        String raw = safe(sourceKey) + "|" + safe(sourcePackage) + "|" + amount + "|"
                + safe(type) + "|" + occurredAt + "|" + MerchantNormalizer.normalize(merchant);
        return "ssm2_" + sha256(raw).substring(0, 32);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
