package com.ssm.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

public final class TransactionId {
    private TransactionId() {}

    public static String create(String sourcePackage, String sourceKey, long amount, String type,
                                long occurredAt, String merchant) {
        String normalizedKey = norm(sourceKey);
        String sourceIdentity = normalizedKey.isBlank() ? "minute:" + (occurredAt / 60_000L) : normalizedKey;
        String normalized = norm(sourcePackage) + "|" + sourceIdentity + "|" + amount + "|" +
                norm(type) + "|" + norm(merchant);
        return "tx_" + sha256(normalized).substring(0, 24);
    }

    private static String norm(String value) {
        String v = value == null ? "" : value;
        v = Normalizer.normalize(v, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return v.replaceAll("\\s+", " ").trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
