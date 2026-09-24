package com.ssm.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 1001;

    private static final int COLOR_BG = Color.rgb(247, 249, 249);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(31, 36, 35);
    private static final int COLOR_MUTED = Color.rgb(111, 120, 118);
    private static final int COLOR_MINT = Color.rgb(76, 175, 145);
    private static final int COLOR_EXPENSE = Color.rgb(213, 74, 74);
    private static final int COLOR_INCOME = Color.rgb(45, 148, 93);

    private LinearLayout root;
    private final NumberFormat wonFormat = NumberFormat.getNumberInstance(Locale.KOREA);

    private final BroadcastReceiver transactionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scroll);
        render();
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(PaymentNotificationListener.ACTION_TRANSACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(transactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            // Android 12L 이하에서는 RECEIVER_NOT_EXPORTED 플래그가 아직 정의되지 않았습니다.
            registerReceiver(transactionReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(transactionReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (root == null) return;
        root.removeAllViews();

        LocalDate today = LocalDate.now();
        ZonedDateTime monthStartZdt = today.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault());
        long monthStart = monthStartZdt.toInstant().toEpochMilli();
        long nextMonth = monthStartZdt.plusMonths(1).toInstant().toEpochMilli();
        long todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long tomorrowStart = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        TransactionDb db = new TransactionDb(this);
        long monthExpense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, monthStart, nextMonth);
        long monthIncome = db.sumByTypeBetween(Transaction.TYPE_INCOME, monthStart, nextMonth);
        long todayExpense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, todayStart, tomorrowStart);
        int monthCount = db.countBetween(monthStart, nextMonth);
        List<Transaction> recent = db.getRecent(20);
        db.close();

        TextView brand = text("씀", 28, COLOR_TEXT, Typeface.BOLD);
        root.addView(brand);

        TextView month = text(
                new SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(new Date()),
                14, COLOR_MUTED, Typeface.NORMAL
        );
        addTopMargin(month, 2);
        root.addView(month);

        LinearLayout totalCard = card();
        TextView totalLabel = text("이번 달 총지출", 14, COLOR_MUTED, Typeface.NORMAL);
        totalCard.addView(totalLabel);
        TextView totalValue = text(won(monthExpense), 34, COLOR_EXPENSE, Typeface.BOLD);
        addTopMargin(totalValue, 6);
        totalCard.addView(totalValue);
        TextView incomeValue = text("입금  +" + won(monthIncome), 13, COLOR_INCOME, Typeface.BOLD);
        addTopMargin(incomeValue, 10);
        totalCard.addView(incomeValue);
        addSection(root, totalCard, 22);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setWeightSum(2f);

        LinearLayout todayCard = smallCard("오늘 지출", won(todayExpense), COLOR_EXPENSE);
        LinearLayout countCard = smallCard("이번 달 거래", monthCount + "건", COLOR_TEXT);
        metrics.addView(todayCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        countParams.setMarginStart(dp(10));
        metrics.addView(countCard, countParams);
        addSection(root, metrics, 10);

        LinearLayout setupCard = card();
        setupCard.addView(text("자동 기록 설정", 16, COLOR_TEXT, Typeface.BOLD));

        TextView calendarStatus = text(
                "캘린더 · " + CalendarSync.getSelectedCalendarName(this),
                13, COLOR_MUTED, Typeface.NORMAL
        );
        addTopMargin(calendarStatus, 8);
        setupCard.addView(calendarStatus);

        Button notificationButton = button("알림 접근 설정");
        notificationButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addTopMargin(notificationButton, 12);
        setupCard.addView(notificationButton);

        Button calendarButton = button("캘린더 선택");
        calendarButton.setOnClickListener(v -> ensureCalendarAndChoose());
        addTopMargin(calendarButton, 8);
        setupCard.addView(calendarButton);

        Button manualButton = button("거래 직접 추가");
        manualButton.setOnClickListener(v -> showManualTransactionDialog());
        addTopMargin(manualButton, 8);
        setupCard.addView(manualButton);

        addSection(root, setupCard, 18);

        TextView recentTitle = text("최근 내역", 18, COLOR_TEXT, Typeface.BOLD);
        addTopMargin(recentTitle, 24);
        root.addView(recentTitle);

        if (recent.isEmpty()) {
            TextView empty = text(
                    "아직 저장된 거래가 없습니다.\n알림 접근을 허용하면 결제·입금 알림을 자동으로 기록합니다.",
                    14, COLOR_MUTED, Typeface.NORMAL
            );
            empty.setLineSpacing(0f, 1.25f);
            addTopMargin(empty, 12);
            root.addView(empty);
        } else {
            for (Transaction transaction : recent) {
                LinearLayout row = transactionRow(transaction);
                addSection(root, row, 10);
            }
        }

        TextView note = text(
                "거래 데이터는 이 기기에만 저장됩니다. 캘린더 기록은 선택한 Android 캘린더에 추가됩니다.",
                12, COLOR_MUTED, Typeface.NORMAL
        );
        note.setLineSpacing(0f, 1.2f);
        addTopMargin(note, 28);
        root.addView(note);
    }

    private LinearLayout transactionRow(Transaction transaction) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView merchant = text(transaction.merchant, 15, COLOR_TEXT, Typeface.BOLD);
        info.addView(merchant);

        String date = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
                .format(new Date(transaction.occurredAt));
        TextView meta = text(date, 12, COLOR_MUTED, Typeface.NORMAL);
        addTopMargin(meta, 4);
        info.addView(meta);

        line.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean expense = transaction.isExpense();
        String prefix = expense ? "-" : "+";
        TextView amount = text(
                prefix + won(transaction.amount),
                15,
                expense ? COLOR_EXPENSE : COLOR_INCOME,
                Typeface.BOLD
        );
        line.addView(amount);

        row.addView(line);
        return row;
    }

    private void ensureCalendarAndChoose() {
        if (!CalendarSync.hasPermission(this)) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                    REQ_CALENDAR
            );
            return;
        }
        showCalendarChooser();
    }

    private void showCalendarChooser() {
        List<CalendarSync.CalendarItem> calendars = CalendarSync.listWritableCalendars(this);
        if (calendars.isEmpty()) {
            Toast.makeText(this, "기록 가능한 캘린더가 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels = new String[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            labels[i] = calendars.get(i).toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("기록할 캘린더 선택")
                .setItems(labels, (dialog, which) -> {
                    CalendarSync.saveCalendar(this, calendars.get(which));
                    Toast.makeText(this, "캘린더가 설정되었습니다.", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showManualTransactionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);

        Spinner typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"지출", "입금"}
        ));
        form.addView(typeSpinner);

        EditText amount = new EditText(this);
        amount.setHint("금액");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(amount);

        EditText merchant = new EditText(this);
        merchant.setHint("사용처 / 보낸 사람");
        form.addView(merchant);

        new AlertDialog.Builder(this)
                .setTitle("거래 직접 추가")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    String amountText = amount.getText().toString().replace(",", "").trim();
                    long parsedAmount;
                    try {
                        parsedAmount = Long.parseLong(amountText);
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "금액을 확인해주세요.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (parsedAmount <= 0L) {
                        Toast.makeText(this, "금액은 1원 이상이어야 합니다.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String merchantText = merchant.getText().toString().trim();
                    if (merchantText.isEmpty()) merchantText = "직접 입력";

                    String type = typeSpinner.getSelectedItemPosition() == 0
                            ? Transaction.TYPE_EXPENSE
                            : Transaction.TYPE_INCOME;
                    long now = System.currentTimeMillis();

                    Transaction transaction = new Transaction(
                            0L,
                            "manual:" + UUID.randomUUID(),
                            "manual",
                            merchantText,
                            parsedAmount,
                            type,
                            now,
                            "직접 입력",
                            null
                    );

                    TransactionDb db = new TransactionDb(this);
                    long rowId = db.insertOrIgnore(transaction);
                    String transactionId = rowId > 0L ? db.getTransactionId(rowId) : null;
                    db.close();

                    if (rowId > 0L) {
                        SyncScheduler.enqueue(this, transactionId);
                    }
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            if (CalendarSync.hasPermission(this)) {
                showCalendarChooser();
            } else {
                Toast.makeText(this, "캘린더 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_CARD);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(232, 237, 236));
        card.setBackground(background);
        return card;
    }

    private LinearLayout smallCard(String label, String value, int valueColor) {
        LinearLayout card = card();
        card.addView(text(label, 12, COLOR_MUTED, Typeface.NORMAL));
        TextView valueView = text(value, 18, valueColor, Typeface.BOLD);
        addTopMargin(valueView, 6);
        card.addView(valueView);
        return card;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(14), dp(11), dp(14), dp(11));

        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_MINT);
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        return button;
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private void addSection(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private void addTopMargin(View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topDp);
        view.setLayoutParams(params);
    }

    private String won(long amount) {
        return wonFormat.format(amount) + "원";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
