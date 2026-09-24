package com.ssm.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class NotionSync {
    public static final String TARGET = "notion";
    public static final String DATA_SOURCE_ID = "6dbe7411-93e9-4b01-a2e9-761fe4c0e98f";
    public static final String DATA_SOURCE_NAME = "입출금 캘린더";
    private static final String API_VERSION = "2026-03-11";
    private NotionSync() {}

    public static String upsert(Context context, Transaction tx, String remoteId) throws Exception {
        String token = SecretStore.getNotionToken(context);
        if (token == null || token.isBlank()) throw new IllegalStateException("Notion token not configured");
        String pageId = remoteId;
        if (pageId == null || pageId.isBlank()) pageId = findByTransactionId(token, tx.transactionId);

        JSONObject body = new JSONObject().put("properties", properties(tx));
        if (pageId == null) {
            body.put("parent", new JSONObject().put("type", "data_source_id").put("data_source_id", DATA_SOURCE_ID));
            return request("POST", "https://api.notion.com/v1/pages", token, body).getString("id");
        }
        request("PATCH", "https://api.notion.com/v1/pages/" + pageId, token, body);
        return pageId;
    }

    public static void trash(Context context, String pageId) throws Exception {
        if (pageId == null || pageId.isBlank()) return;
        String token = SecretStore.getNotionToken(context);
        if (token == null || token.isBlank()) throw new IllegalStateException("Notion token not configured");
        request("PATCH", "https://api.notion.com/v1/pages/" + pageId, token,
                new JSONObject().put("in_trash", true));
    }

    private static String findByTransactionId(String token, String transactionId) throws Exception {
        JSONObject filter = new JSONObject()
                .put("property", "transaction_id")
                .put("rich_text", new JSONObject().put("equals", transactionId));
        JSONObject result = request("POST",
                "https://api.notion.com/v1/data_sources/" + DATA_SOURCE_ID + "/query",
                token,
                new JSONObject().put("filter", filter).put("page_size", 1));
        JSONArray rows = result.optJSONArray("results");
        return rows != null && rows.length() > 0 ? rows.getJSONObject(0).optString("id", null) : null;
    }

    private static JSONObject properties(Transaction tx) throws Exception {
        JSONObject p = new JSONObject();
        p.put("내역", title(tx.merchant));
        p.put("금액", new JSONObject().put("number", tx.amount));
        p.put("결제일", new JSONObject().put("date", new JSONObject().put("start", Instant.ofEpochMilli(tx.occurredAt).toString())));
        p.put("구분", select(tx.isExpense() ? "지출" : "입금"));
        p.put("가맹점", richText(tx.merchant));
        p.put("카테고리", select(tx.category));
        p.put("결제수단", select(tx.paymentMethod));
        p.put("등록경로", select(tx.manual ? "수동" : "씀"));
        p.put("transaction_id", richText(tx.transactionId));
        p.put("원본 이벤트 ID", richText(tx.sourceKey));
        p.put("메모", richText(tx.rawText));
        return p;
    }

    private static JSONObject title(String s) throws Exception {
        return new JSONObject().put("title", new JSONArray().put(
                new JSONObject().put("type", "text").put("text", new JSONObject().put("content", limit(s, 180)))));
    }

    private static JSONObject richText(String s) throws Exception {
        return new JSONObject().put("rich_text", new JSONArray().put(
                new JSONObject().put("type", "text").put("text", new JSONObject().put("content", limit(s, 1800)))));
    }

    private static JSONObject select(String s) throws Exception {
        return new JSONObject().put("select", new JSONObject().put("name", s));
    }

    private static String limit(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static JSONObject request(String method, String url, String token, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Notion-Version", API_VERSION);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (stream != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        if (code < 200 || code >= 300) throw new IllegalStateException("Notion HTTP " + code + ": " + limit(sb.toString(), 240));
        return sb.length() == 0 ? new JSONObject() : new JSONObject(sb.toString());
    }
}
