package com.ssm.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 1001;
    private static final String[] CATEGORIES = {"식비","카페","쇼핑","교통","생활","구독","의료","기타"};
    private static final String[] METHODS = {"삼성페이","네이버페이","카드","현금","기타"};

    private static final int BG = Color.rgb(246, 248, 247);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(28, 34, 32);
    private static final int MUTED = Color.rgb(103, 113, 110);
    private static final int BORDER = Color.rgb(227, 233, 231);
    private static final int MINT = Color.rgb(43, 145, 111);
    private static final int MINT_SOFT = Color.rgb(229, 244, 238);
    private static final int RED = Color.rgb(195, 67, 67);
    private static final int GREEN = Color.rgb(37, 133, 82);

    private LinearLayout root;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(48));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        render();
    }

    @Override @SuppressLint("UnspecifiedRegisterReceiverFlag") protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(PaymentNotificationListener.ACTION_TRANSACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    @Override protected void onResume() {
        super.onResume();
        SyncCoordinator.schedule(this);
        render();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            SyncCoordinator.schedule(this);
            render();
        }
    }

    private void render() {
        if (root == null) return;
        root.removeAllViews();

        LocalDate today = LocalDate.now();
        long start = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long tomorrow = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        long expense;
        long income;
        long todayExpense;
        int monthCount;
        int pending;
        List<Transaction> recent;
        try (TransactionDb db = new TransactionDb(this)) {
            expense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, start, end);
            income = db.sumByTypeBetween(Transaction.TYPE_INCOME, start, end);
            todayExpense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, todayStart, tomorrow);
            monthCount = db.countBetween(start, end);
            pending = db.pendingSyncCount();
            recent = db.getRecent(30);
        }

        TextView brand = text("씀", 30, TEXT, Typeface.BOLD);
        root.addView(brand);
        TextView month = text(new SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(new Date()), 14, MUTED, Typeface.NORMAL);
        margin(month, 2);
        root.addView(month);

        LinearLayout hero = card();
        hero.setBackground(round(MINT_SOFT, 22, 0, 0));
        hero.addView(text("이번 달 지출", 14, MUTED, Typeface.BOLD));
        TextView total = text(money(expense), 34, TEXT, Typeface.BOLD);
        margin(total, 6);
        hero.addView(total);
        TextView flow = text("입금 +" + money(income) + "  ·  " + monthCount + "건", 13, GREEN, Typeface.BOLD);
        margin(flow, 10);
        hero.addView(flow);
        addSection(hero, 22);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metricCard("오늘 지출", money(todayExpense), RED), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.setMarginStart(dp(10));
        metrics.addView(metricCard("동기화 대기", pending + "건", pending == 0 ? GREEN : RED), mp);
        addSection(metrics, 10);

        addSectionTitle("빠른 실행", 28);
        LinearLayout actions = card();
        actions.addView(primaryButton("+ 거래 직접 추가", v -> showManualDialog()));
        Button retry = secondaryButton("외부 연동 다시 시도", v -> {
            SyncCoordinator.schedule(this);
            Toast.makeText(this, "동기화 작업을 예약했습니다.", Toast.LENGTH_SHORT).show();
            render();
        });
        margin(retry, 8);
        actions.addView(retry);
        addSection(actions, 10);

        addSectionTitle("최근 거래", 28);
        if (recent.isEmpty()) {
            LinearLayout empty = card();
            TextView e = text("아직 저장된 거래가 없습니다.\n알림 접근을 허용하면 결제·입금 알림을 자동으로 기록합니다.", 14, MUTED, Typeface.NORMAL);
            e.setLineSpacing(0f, 1.25f);
            empty.addView(e);
            addSection(empty, 10);
        } else {
            for (Transaction tx : recent) addSection(transactionCard(tx), 10);
        }

        addSectionTitle("연동 상태", 30);
        LinearLayout integrations = card();
        boolean notificationReady = isNotificationAccessEnabled();
        boolean calendarPermission = CalendarSync.hasPermission(this);
        boolean googleReady = calendarPermission && CalendarSync.findBest(this, CalendarSync.TARGET_GOOGLE) != null;
        boolean samsungReady = calendarPermission && (CalendarSync.findBest(this, CalendarSync.TARGET_SAMSUNG) != null || googleReady);
        boolean notionReady = SecretStore.hasNotionToken(this);
        integrations.addView(statusRow("결제·입금 알림", notificationReady ? "연결됨" : "설정 필요", notificationReady));
        integrations.addView(statusRow("Google Calendar", googleReady ? "연결 가능" : (calendarPermission ? "계정 미감지" : "권한 필요"), googleReady));
        integrations.addView(statusRow("Samsung Calendar", samsungReady ? "연결 가능" : (calendarPermission ? "계정 미감지" : "권한 필요"), samsungReady));
        integrations.addView(statusRow("Notion 입출금 캘린더", notionReady ? "토큰 저장됨" : "토큰 필요", notionReady));

        Button notificationButton = secondaryButton("알림 접근 설정", v -> showNotificationAccessDisclosure());
        margin(notificationButton, 14);
        integrations.addView(notificationButton);
        Button calendarButton = secondaryButton("캘린더 권한 허용", v -> ensureCalendarPermission());
        margin(calendarButton, 8);
        integrations.addView(calendarButton);
        Button notionButton = secondaryButton("Notion 토큰 설정", v -> showNotionTokenDialog());
        margin(notionButton, 8);
        integrations.addView(notionButton);
        addSection(integrations, 10);

        TextView privacy = text("거래 원본은 기기 로컬 DB에 우선 저장됩니다. 외부 연동 실패 항목은 로컬 큐에 남아 재시도되며, Notion 토큰은 Android Keystore로 암호화해 보관합니다.", 12, MUTED, Typeface.NORMAL);
        privacy.setLineSpacing(0f, 1.25f);
        margin(privacy, 24);
        root.addView(privacy);
    }

    private LinearLayout transactionCard(Transaction tx) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(tx.merchant, 16, TEXT, Typeface.BOLD));
        String when = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(new Date(tx.occurredAt));
        TextView meta = text(when + " · " + tx.category + " · " + tx.paymentMethod, 12, MUTED, Typeface.NORMAL);
        margin(meta, 4);
        info.addView(meta);
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(text((tx.isExpense() ? "-" : "+") + money(tx.amount), 16, tx.isExpense() ? RED : GREEN, Typeface.BOLD));
        card.addView(top);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = miniButton("수정", v -> showEditDialog(tx));
        Button del = miniButton("삭제", v -> confirmDelete(tx));
        buttons.addView(edit, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        dp2.setMarginStart(dp(8));
        buttons.addView(del, dp2);
        margin(buttons, 10);
        card.addView(buttons);
        return card;
    }

    private void showManualDialog() {
        LinearLayout form = form();
        Spinner type = spinner(new String[]{"지출","입금"}, 0);
        EditText amount = input("금액", InputType.TYPE_CLASS_NUMBER);
        EditText merchant = input("사용처 / 보낸 사람", InputType.TYPE_CLASS_TEXT);
        Spinner category = spinner(CATEGORIES, 7);
        Spinner method = spinner(METHODS, 4);
        form.addView(type); form.addView(amount); form.addView(merchant); form.addView(category); form.addView(method);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("거래 추가")
                .setView(form)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long value = parseAmount(amount.getText().toString());
            String name = merchant.getText().toString().trim();
            if (value <= 0 || name.isEmpty()) {
                Toast.makeText(this, "금액과 사용처를 확인해주세요.", Toast.LENGTH_LONG).show();
                return;
            }
            new TransactionRepository(this).createManual(
                    value,
                    type.getSelectedItemPosition() == 0 ? Transaction.TYPE_EXPENSE : Transaction.TYPE_INCOME,
                    name,
                    (String) category.getSelectedItem(),
                    (String) method.getSelectedItem());
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void showEditDialog(Transaction tx) {
        LinearLayout form = form();
        EditText merchant = input("사용처", InputType.TYPE_CLASS_TEXT);
        merchant.setText(tx.merchant);
        Spinner category = spinner(CATEGORIES, indexOf(CATEGORIES, tx.category));
        Spinner method = spinner(METHODS, indexOf(METHODS, tx.paymentMethod));
        form.addView(merchant); form.addView(category); form.addView(method);
        new AlertDialog.Builder(this)
                .setTitle("거래 보정")
                .setMessage("같은 가맹점의 다음 거래부터 선택한 카테고리를 자동 적용합니다.")
                .setView(form)
                .setPositiveButton("저장", (d,w) -> {
                    String name = merchant.getText().toString().trim();
                    if (name.isEmpty()) return;
                    new TransactionRepository(this).correct(tx.transactionId, name,
                            (String) category.getSelectedItem(), (String) method.getSelectedItem());
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmDelete(Transaction tx) {
        new AlertDialog.Builder(this)
                .setTitle("거래 삭제")
                .setMessage("거래를 삭제합니다. 외부 Calendar·Notion 항목도 연결 가능한 시점에 자동 정리됩니다.")
                .setPositiveButton("삭제", (d,w) -> {
                    new TransactionRepository(this).delete(tx.transactionId);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showNotificationAccessDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("알림 접근이 필요한 이유")
                .setMessage("씀은 카드·은행의 결제/입금 알림에서 금액과 가맹점만 추출해 로컬 가계부에 저장합니다. 일반 알림을 외부 서버로 전송하지 않으며, Notion 연동을 직접 설정한 경우에만 해당 거래 정보가 사용자의 Notion으로 전송됩니다.")
                .setPositiveButton("설정으로 이동", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("취소", null)
                .show();
    }

    private void showNotionTokenDialog() {
        EditText token = input("Integration token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Notion 토큰 설정")
                .setMessage("기존 입출금 캘린더 데이터소스를 Integration에 연결한 뒤 토큰을 입력하세요.")
                .setView(token)
                .setPositiveButton("저장", (d,w) -> {
                    try {
                        SecretStore.saveNotionToken(this, token.getText().toString());
                        SyncCoordinator.schedule(this);
                        render();
                    } catch (Exception e) {
                        Toast.makeText(this, "토큰 저장 실패", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void ensureCalendarPermission() {
        if (CalendarSync.hasPermission(this)) {
            SyncCoordinator.schedule(this);
            Toast.makeText(this, "캘린더 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR);
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setBackground(round(CARD, 20, 1, BORDER));
        return c;
    }

    private LinearLayout metricCard(String label, String value, int valueColor) {
        LinearLayout c = card();
        c.addView(text(label, 12, MUTED, Typeface.BOLD));
        TextView v = text(value, 18, valueColor, Typeface.BOLD);
        margin(v, 6);
        c.addView(v);
        return c;
    }

    private LinearLayout statusRow(String label, String value, boolean ok) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(label, 14, TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(value, 13, ok ? GREEN : RED, Typeface.BOLD));
        return row;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button b = buttonBase(label);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(MINT, 14, 0, 0));
        b.setOnClickListener(listener);
        return b;
    }

    private Button secondaryButton(String label, View.OnClickListener listener) {
        Button b = buttonBase(label);
        b.setTextColor(TEXT);
        b.setBackground(round(Color.rgb(250, 251, 251), 14, 1, BORDER));
        b.setOnClickListener(listener);
        return b;
    }

    private Button miniButton(String label, View.OnClickListener listener) {
        Button b = buttonBase(label);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setBackground(round(Color.rgb(249, 250, 250), 12, 1, BORDER));
        b.setOnClickListener(listener);
        return b;
    }

    private Button buttonBase(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(50));
        b.setPadding(dp(14), 0, dp(14), 0);
        return b;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        return v;
    }

    private void addSectionTitle(String title, int topDp) {
        TextView v = text(title, 18, TEXT, Typeface.BOLD);
        margin(v, topDp);
        root.addView(v);
    }

    private void addSection(View child, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        root.addView(child, p);
    }

    private void margin(View view, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        view.setLayoutParams(p);
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(2), dp(20), 0);
        return f;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setInputType(type);
        return e;
    }

    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        s.setSelection(Math.max(0, Math.min(selected, values.length - 1)));
        return s;
    }

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(target)) return i;
        return values.length - 1;
    }

    private long parseAmount(String value) {
        try { return Long.parseLong(value.replace(",", "").trim()); }
        catch (Exception e) { return -1L; }
    }

    private String money(long amount) { return won.format(amount) + "원"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
