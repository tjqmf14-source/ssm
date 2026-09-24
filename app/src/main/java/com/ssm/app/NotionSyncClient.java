package com.ssm.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotionSyncClient {
    public static final String DATA_SOURCE_ID = "6dbe7411-93e9-4b01-a2e9-761fe4c0e98f";
    private static final String API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2026-03-11";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    public static final class Result {
        public final boolean success;
        public final boolean retryable;
        public final boolean authRequired;
        public final String pageId;
        public final int httpCode;

        private Result(boolean success, boolean retryable, boolean authRequired, String pageId, int httpCode) {
            this.success = success;
            this.retryable = retryable;
            this.authRequired = authRequired;
            this.pageId = pageId;
            this.httpCode = httpCode;
        }

        public static Result success(String pageId, int httpCode) {
            return new Result(true, false, false, pageId, httpCode);
        }

        public static Result retry(int httpCode) {
            return new Result(false, true, false, null, httpCode);
        }

        public static Result auth(int httpCode) {
            return new Result(false, false, true, null, httpCode);
        }

        public static Result failure(int httpCode) {
            return new Result(false, false, false, null, httpCode);
        }
    }

    private NotionSyncClient() {}

    public static Result upsert(String token, Transaction transaction) {
        if (token == null || token.trim().isEmpty()) return Result.auth(401);
        try {
            String existing = findByTransactionId(token, transaction.transactionId);
            if (transaction.isDeleted() || transaction.isCancelled()) {
                if (existing == null) return Result.success(null, 200);
                return archivePage(token, existing);
            }
            if (existing != null) return updatePage(token, existing, transaction);
            return createPage(token, transaction);
        } catch (AuthException error) {
            return Result.auth(401);
        } catch (RetryException error) {
            return Result.retry(0);
        } catch (Exception error) {
            return Result.retry(0);
        }
    }

    private static String findByTransactionId(String token, String transactionId) throws Exception {
        String body = "{"
                + "\"filter\":{\"property\":\"transaction_id\",\"rich_text\":{\"equals\":\"" + json(transactionId) + "\"}},"
                + "\"page_size\":1"
                + "}";
        Response response = request(
                "POST",
                API_BASE + "/data_sources/" + DATA_SOURCE_ID + "/query",
                token,
                body
        );
        if (response.code == 401 || response.code == 403) throw new AuthException();
        if (response.code == 429 || response.code >= 500) throw new RetryException();
        if (response.code < 200 || response.code >= 300) return null;

        int resultsIndex = response.body.indexOf("\"results\"");
        if (resultsIndex < 0) return null;
        int arrayStart = response.body.indexOf('[', resultsIndex);
        if (arrayStart < 0) return null;
        int firstObject = response.body.indexOf('{', arrayStart);
        if (firstObject < 0) return null;

        Matcher matcher = ID_PATTERN.matcher(response.body.substring(firstObject));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Result createPage(String token, Transaction transaction) throws Exception {
        Response response = request(
                "POST",
                API_BASE + "/pages",
                token,
                buildCreatePayload(transaction)
        );
        return toResult(response);
    }

    private static Result archivePage(String token, String pageId) throws Exception {
        Response response = request(
                "PATCH",
                API_BASE + "/pages/" + pageId,
                token,
                "{\"archived\":true}"
        );
        Result result = toResult(response);
        if (result.success && result.pageId == null) return Result.success(pageId, response.code);
        return result;
    }

    private static Result updatePage(String token, String pageId, Transaction transaction) throws Exception {
        Response response = request(
                "PATCH",
                API_BASE + "/pages/" + pageId,
                token,
                "{\"properties\":" + buildProperties(transaction) + "}"
        );
        Result result = toResult(response);
        if (result.success && result.pageId == null) return Result.success(pageId, response.code);
        return result;
    }

    private static Result toResult(Response response) {
        if (response.code == 401 || response.code == 403) return Result.auth(response.code);
        if (response.code == 429 || response.code >= 500 || response.code == 0) return Result.retry(response.code);
        if (response.code < 200 || response.code >= 300) return Result.failure(response.code);

        Matcher matcher = ID_PATTERN.matcher(response.body);
        return Result.success(matcher.find() ? matcher.group(1) : null, response.code);
    }

    static String buildCreatePayload(Transaction transaction) {
        return "{"
                + "\"parent\":{\"type\":\"data_source_id\",\"data_source_id\":\"" + DATA_SOURCE_ID + "\"},"
                + "\"properties\":" + buildProperties(transaction)
                + "}";
    }

    static String buildProperties(Transaction transaction) {
        String type = transaction.isExpense() ? "지출" : "입금";
        String start = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).toOffsetDateTime()
        );

        return "{"
                + "\"내역\":{\"title\":[{\"text\":{\"content\":\"" + json(transaction.merchant) + "\"}}]},"
                + "\"금액\":{\"number\":" + transaction.amount + "},"
                + "\"결제일\":{\"date\":{\"start\":\"" + json(start) + "\"}},"
                + "\"구분\":{\"select\":{\"name\":\"" + type + "\"}},"
                + "\"가맹점\":{\"rich_text\":[{\"text\":{\"content\":\"" + json(transaction.merchant) + "\"}}]},"
                + "\"카테고리\":{\"select\":{\"name\":\"" + json(transaction.category) + "\"}},"
                + "\"결제수단\":{\"select\":{\"name\":\"" + json(transaction.paymentMethod) + "\"}},"
                + "\"등록경로\":{\"select\":{\"name\":\"씀\"}},"
                + "\"transaction_id\":{\"rich_text\":[{\"text\":{\"content\":\"" + json(transaction.transactionId) + "\"}}]},"
                + "\"원본 이벤트 ID\":{\"rich_text\":[{\"text\":{\"content\":\"" + json(transaction.sourceKey) + "\"}}]},"
                + "\"메모\":{\"rich_text\":[{\"text\":{\"content\":\"" + (transaction.isCancelled() ? "씀 2.0 자동 기록 · 취소 거래" : "씀 2.0 자동 기록") + "\"}}]}"
                + "}";
    }

    private static Response request(String method, String endpoint, String token, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        connection.setRequestProperty("Notion-Version", NOTION_VERSION);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);

        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readAll(stream);
        connection.disconnect();
        return new Response(code, responseBody);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String json(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    private static final class AuthException extends Exception {}
    private static final class RetryException extends Exception {}
}
