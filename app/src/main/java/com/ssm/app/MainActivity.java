package com.ssm.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 1001;

    private static final int TAB_HOME = 0;
    private static final int TAB_TRANSACTIONS = 1;
    private static final int TAB_ANALYSIS = 2;
    private static final int TAB_SETTINGS = 3;

    private static final String[] CATEGORIES = {
            "식비", "카페", "쇼핑", "교통", "생활", "구독", "의료", "기타"
    };
    private static final String[] PAYMENT_METHODS = {
            "삼성페이", "네이버페이", "카드", "현금", "기타"
    };

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);
    private final DateTimeFormatter rowDate = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA);

    private FrameLayout content;
    private LinearLayout nav;
    private int selectedTab = TAB_HOME;

    private final BroadcastReceiver transactionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderCurrentTab();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(DesignTokens.BG);
        getWindow().setNavigationBarColor(DesignTokens.SURFACE);

        buildShell();
        registerTransactionReceiver();
        renderCurrentTab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCurrentTab();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(transactionReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    private void registerTransactionReceiver() {
        IntentFilter filter = new IntentFilter(PaymentNotificationListener.ACTION_TRANSACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(transactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(transactionReceiver, filter);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DesignTokens.BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(12), dp(8), dp(12), dp(10));
        nav.setBackground(background(DesignTokens.SURFACE, 0, DesignTokens.LINE, 1));

        String[] labels = {"홈", "내역", "분석", "설정"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView item = text(labels[i], 15, DesignTokens.MUTED, Typeface.BOLD);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(12), dp(8), dp(12));
            item.setOnClickListener(v -> {
                selectedTab = tab;
                renderCurrentTab();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(54), 1f));
        }

        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void renderCurrentTab() {
        if (content == null) return;
        content.removeAllViews();
        refreshNav();

        View page;
        switch (selectedTab) {
            case TAB_TRANSACTIONS:
                page = transactionsPage();
                break;
            case TAB_ANALYSIS:
                page = analysisPage();
                break;
            case TAB_SETTINGS:
                page = settingsPage();
                break;
            case TAB_HOME:
            default:
                page = homePage();
                break;
        }
        content.addView(page);
    }

    private void refreshNav() {
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == selectedTab;
            item.setTextColor(active ? DesignTokens.ACCENT : DesignTokens.MUTED);
            item.setBackground(active
                    ? background(DesignTokens.ACCENT_SOFT, 14, DesignTokens.ACCENT_SOFT, 0)
                    : null);
        }
    }

    private View homePage() {
        ScrollView scroll = pageScroll();
        LinearLayout body = pageBody();

        YearMonth month = YearMonth.now();
        long[] monthRange = monthRange(month);
        long[] todayRange = todayRange();

        TransactionDb db = new TransactionDb(this);
        long monthExpense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, monthRange[0], monthRange[1]);
        long monthIncome = db.sumByTypeBetween(Transaction.TYPE_INCOME, monthRange[0], monthRange[1]);
        long todayExpense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, todayRange[0], todayRange[1]);
        int monthCount = db.countBetween(monthRange[0], monthRange[1]);
        int reviewCount = db.getNeedsReviewCount();
        int notionPending = db.getPendingNotionCount();
        List<Transaction> recent = db.getRecent(6);
        db.close();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.addView(text("씀", 28, DesignTokens.TEXT, Typeface.BOLD));
        titleWrap.addView(text(month.getYear() + "년 " + month.getMonthValue() + "월", 15, DesignTokens.MUTED, Typeface.NORMAL));
        header.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = compactButton("+ 거래 추가", true);
        add.setOnClickListener(v -> showTransactionEditor(null));
        header.addView(add);
        body.addView(header);

        LinearLayout hero = card();
        addTop(hero, text("이번 달 총지출", 15, DesignTokens.MUTED, Typeface.BOLD), 0);
        addTop(hero, text(won(monthExpense), 34, DesignTokens.TEXT, Typeface.BOLD), 8);
        addTop(hero, text("입금  +" + won(monthIncome), 15, DesignTokens.INCOME, Typeface.BOLD), 10);
        addSection(body, hero, 24);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setWeightSum(2f);
        LinearLayout today = metricCard("오늘 지출", won(todayExpense), DesignTokens.EXPENSE);
        LinearLayout count = metricCard("이번 달 거래", monthCount + "건", DesignTokens.TEXT);
        metrics.addView(today, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        countParams.leftMargin = dp(10);
        metrics.addView(count, countParams);
        addSection(body, metrics, 10);

        LinearLayout sync = card();
        String automationText;
        int automationColor;
        if (!isNotificationAccessEnabled()) {
            automationText = "알림 접근 권한이 필요합니다";
            automationColor = DesignTokens.WARNING;
        } else if (!SecureTokenStore.hasNotionToken(this)) {
            automationText = "자동 기록 준비됨 · Notion 연결 필요";
            automationColor = DesignTokens.WARNING;
        } else if (notionPending > 0) {
            automationText = "자동 기록 정상 · Notion 동기화 대기 " + notionPending + "건";
            automationColor = DesignTokens.WARNING;
        } else {
            automationText = "자동 기록 정상 · 동기화 완료";
            automationColor = DesignTokens.ACCENT;
        }
        sync.addView(text(automationText, 15, automationColor, Typeface.BOLD));
        if (reviewCount > 0) {
            TextView review = text("확인 필요한 거래 " + reviewCount + "건", 15, DesignTokens.WARNING, Typeface.BOLD);
            review.setOnClickListener(v -> {
                selectedTab = TAB_TRANSACTIONS;
                renderCurrentTab();
            });
            addTop(sync, review, 8);
        }
        addSection(body, sync, 12);

        TextView recentTitle = text("최근 거래", 20, DesignTokens.TEXT, Typeface.BOLD);
        addSection(body, recentTitle, 28);

        if (recent.isEmpty()) {
            addSection(body, emptyCard("아직 기록된 거래가 없습니다.\n결제 알림이 들어오면 자동으로 여기에 표시됩니다."), 12);
        } else {
            for (Transaction transaction : recent) {
                addSection(body, transactionRow(transaction), 10);
            }
        }

        body.addView(space(28));
        scroll.addView(body);
        return scroll;
    }

    private View transactionsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout body = pageBody();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("내역", 28, DesignTokens.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = compactButton("+ 추가", true);
        add.setOnClickListener(v -> showTransactionEditor(null));
        header.addView(add);
        body.addView(header);

        EditText search = new EditText(this);
        search.setHint("가맹점 또는 카테고리 검색");
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        search.setBackground(background(DesignTokens.SURFACE, 14, DesignTokens.LINE, 1));
        addSection(body, search, 20);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        addSection(body, list, 12);

        TransactionDb db = new TransactionDb(this);
        List<Transaction> all = db.getRecent(100);
        db.close();

        Runnable renderList = () -> {
            list.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.KOREA);
            int shown = 0;
            for (Transaction transaction : all) {
                String haystack = (transaction.merchant + " " + transaction.category + " " + transaction.paymentMethod)
                        .toLowerCase(Locale.KOREA);
                if (!query.isEmpty() && !haystack.contains(query)) continue;
                addSection(list, transactionRow(transaction), shown == 0 ? 0 : 10);
                shown++;
            }
            if (shown == 0) list.addView(emptyCard("조건에 맞는 거래가 없습니다."));
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        renderList.run();

        body.addView(space(28));
        scroll.addView(body);
        return scroll;
    }

    private View analysisPage() {
        ScrollView scroll = pageScroll();
        LinearLayout body = pageBody();

        body.addView(text("분석", 28, DesignTokens.TEXT, Typeface.BOLD));

        YearMonth month = YearMonth.now();
        long[] range = monthRange(month);
        TransactionDb db = new TransactionDb(this);
        long expense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, range[0], range[1]);
        long income = db.sumByTypeBetween(Transaction.TYPE_INCOME, range[0], range[1]);
        List<TransactionDb.CategoryTotal> categories = db.getCategoryTotalsBetween(range[0], range[1]);
        db.close();

        LinearLayout summary = card();
        summary.addView(text(month.getMonthValue() + "월 지출", 15, DesignTokens.MUTED, Typeface.BOLD));
        addTop(summary, text(won(expense), 30, DesignTokens.TEXT, Typeface.BOLD), 8);
        addTop(summary, text("입금 +" + won(income), 15, DesignTokens.INCOME, Typeface.BOLD), 8);
        addSection(body, summary, 22);

        TextView categoryTitle = text("카테고리", 20, DesignTokens.TEXT, Typeface.BOLD);
        addSection(body, categoryTitle, 28);

        if (categories.isEmpty()) {
            addSection(body, emptyCard("이번 달 지출 데이터가 없습니다."), 12);
        } else {
            long max = Math.max(1L, categories.get(0).amount);
            for (TransactionDb.CategoryTotal category : categories) {
                LinearLayout item = card();
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView name = text(category.category, 16, DesignTokens.TEXT, Typeface.BOLD);
                TextView amount = text(won(category.amount), 16, DesignTokens.TEXT, Typeface.BOLD);
                amount.setGravity(Gravity.END);
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                line.addView(amount);
                item.addView(line);
                addTop(item, ratioBar(category.amount, max), 12);
                addSection(body, item, 10);
            }
        }

        body.addView(space(28));
        scroll.addView(body);
        return scroll;
    }

    private View settingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout body = pageBody();

        body.addView(text("설정", 28, DesignTokens.TEXT, Typeface.BOLD));

        TextView automationTitle = text("자동 기록", 20, DesignTokens.TEXT, Typeface.BOLD);
        addSection(body, automationTitle, 26);

        LinearLayout notification = card();
        notification.addView(text("결제 알림", 17, DesignTokens.TEXT, Typeface.BOLD));
        addTop(notification, text(
                isNotificationAccessEnabled() ? "알림 접근 허용됨" : "알림 접근 권한이 필요합니다",
                15,
                isNotificationAccessEnabled() ? DesignTokens.ACCENT : DesignTokens.WARNING,
                Typeface.BOLD
        ), 8);
        Button notificationButton = actionButton(isNotificationAccessEnabled() ? "알림 접근 설정" : "권한 설정");
        notificationButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addTop(notification, notificationButton, 14);
        addSection(body, notification, 12);

        LinearLayout calendar = card();
        calendar.addView(text("캘린더", 17, DesignTokens.TEXT, Typeface.BOLD));
        addTop(calendar, text(CalendarSync.getSelectedCalendarName(this), 15, DesignTokens.MUTED, Typeface.NORMAL), 8);
        Button calendarButton = actionButton("기록할 캘린더 선택");
        calendarButton.setOnClickListener(v -> requestOrChooseCalendars());
        addTop(calendar, calendarButton, 14);
        addSection(body, calendar, 10);

        LinearLayout notion = card();
        notion.addView(text("Notion", 17, DesignTokens.TEXT, Typeface.BOLD));
        boolean notionConnected = SecureTokenStore.hasNotionToken(this);
        addTop(notion, text(
                notionConnected ? "입출금 캘린더 연결됨" : "연결되지 않음",
                15,
                notionConnected ? DesignTokens.ACCENT : DesignTokens.WARNING,
                Typeface.BOLD
        ), 8);
        addTop(notion, text("기존 DB · " + NotionSyncClient.DATA_SOURCE_ID.substring(0, 8) + "…", 15, DesignTokens.MUTED, Typeface.NORMAL), 4);
        Button notionButton = actionButton(notionConnected ? "Notion 연결 변경" : "Notion 연결");
        notionButton.setOnClickListener(v -> showNotionTokenDialog());
        addTop(notion, notionButton, 14);
        if (notionConnected) {
            Button clear = secondaryButton("연결 해제");
            clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Notion 연결 해제")
                    .setMessage("기기에 저장된 Notion 토큰만 삭제합니다. 기존 Notion 데이터는 삭제하지 않습니다.")
                    .setPositiveButton("해제", (d, w) -> {
                        SecureTokenStore.clearNotionToken(this);
                        renderCurrentTab();
                    })
                    .setNegativeButton("취소", null)
                    .show());
            addTop(notion, clear, 8);
        }
        addSection(body, notion, 10);

        Button retry = actionButton("동기화 다시 시도");
        retry.setOnClickListener(v -> {
            resyncAll();
            Toast.makeText(this, "동기화를 다시 예약했습니다.", Toast.LENGTH_SHORT).show();
        });
        addSection(body, retry, 16);

        TextView privacyTitle = text("개인정보", 20, DesignTokens.TEXT, Typeface.BOLD);
        addSection(body, privacyTitle, 28);

        LinearLayout privacy = card();
        privacy.addView(text("로컬 우선 저장", 17, DesignTokens.TEXT, Typeface.BOLD));
        addTop(privacy, text(
                "거래 원본은 기기의 씀 DB에 먼저 저장됩니다. 씀 자체 중계 서버는 사용하지 않으며, 사용자가 선택한 캘린더와 Notion에만 동기화합니다.",
                15,
                DesignTokens.MUTED,
                Typeface.NORMAL
        ), 8);
        addSection(body, privacy, 12);

        body.addView(space(28));
        scroll.addView(body);
        return scroll;
    }

    private View transactionRow(Transaction transaction) {
        LinearLayout row = card();
        row.setOnClickListener(v -> showTransactionEditor(transaction));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        String merchant = transaction.merchant == null || transaction.merchant.trim().isEmpty()
                ? "거래"
                : transaction.merchant;
        info.addView(text(merchant, 16, DesignTokens.TEXT, Typeface.BOLD));

        String meta = transaction.category + " · " + transaction.paymentMethod + " · " + formatTime(transaction.occurredAt);
        if (transaction.isCancelled()) meta = "취소 · " + meta;
        addTop(info, text(meta, 15, DesignTokens.MUTED, Typeface.NORMAL), 5);

        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String prefix = transaction.isExpense() ? "-" : "+";
        int color = transaction.isExpense() ? DesignTokens.EXPENSE : DesignTokens.INCOME;
        if (transaction.isCancelled()) color = DesignTokens.MUTED;
        TextView amount = text(prefix + won(transaction.amount), 17, color, Typeface.BOLD);
        amount.setGravity(Gravity.END);
        top.addView(amount);

        row.addView(top);

        if (transaction.confidence < 0.9) {
            TextView review = text("자동 분류 확인 필요", 15, DesignTokens.WARNING, Typeface.BOLD);
            addTop(row, review, 10);
        }
        return row;
    }

    private void showTransactionEditor(Transaction existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(4), dp(22), 0);

        Spinner type = spinner(new String[]{"지출", "입금"});
        form.addView(fieldLabel("구분"));
        form.addView(type);

        EditText amount = editField("금액", InputType.TYPE_CLASS_NUMBER);
        addTop(form, fieldLabel("금액"), 14);
        form.addView(amount);

        EditText merchant = editField("가맹점 / 보낸 사람", InputType.TYPE_CLASS_TEXT);
        addTop(form, fieldLabel("내역"), 14);
        form.addView(merchant);

        Spinner category = spinner(CATEGORIES);
        addTop(form, fieldLabel("카테고리"), 14);
        form.addView(category);

        Spinner payment = spinner(PAYMENT_METHODS);
        addTop(form, fieldLabel("결제수단"), 14);
        form.addView(payment);

        if (existing != null) {
            type.setSelection(existing.isExpense() ? 0 : 1);
            amount.setText(String.valueOf(existing.amount));
            merchant.setText(existing.merchant);
            selectSpinner(category, CATEGORIES, existing.category);
            selectSpinner(payment, PAYMENT_METHODS, existing.paymentMethod);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "거래 추가" : "거래 수정")
                .setView(form)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .setNeutralButton(existing == null ? null : "삭제", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long parsed;
                try {
                    parsed = Long.parseLong(amount.getText().toString().replace(",", "").trim());
                } catch (NumberFormatException error) {
                    amount.setError("금액을 확인해주세요.");
                    return;
                }
                if (parsed <= 0L) {
                    amount.setError("1원 이상 입력해주세요.");
                    return;
                }

                String merchantText = merchant.getText().toString().trim();
                if (merchantText.isEmpty()) {
                    merchant.setError("내역을 입력해주세요.");
                    return;
                }

                String txType = type.getSelectedItemPosition() == 0
                        ? Transaction.TYPE_EXPENSE
                        : Transaction.TYPE_INCOME;
                String txCategory = CATEGORIES[category.getSelectedItemPosition()];
                String txPayment = PAYMENT_METHODS[payment.getSelectedItemPosition()];

                TransactionDb db = new TransactionDb(this);
                String transactionId;
                if (existing == null) {
                    long now = System.currentTimeMillis();
                    String sourceKey = "manual:" + UUID.randomUUID();
                    transactionId = TransactionIdentity.create(sourceKey, "manual", parsed, txType, now, merchantText);
                    Transaction transaction = new Transaction(
                            0L,
                            transactionId,
                            sourceKey,
                            "manual",
                            merchantText,
                            MerchantNormalizer.normalize(merchantText),
                            parsed,
                            txType,
                            now,
                            "직접 입력",
                            txPayment,
                            txCategory,
                            Transaction.STATUS_NORMAL,
                            1.0,
                            null,
                            null
                    );
                    long rowId = db.insertOrIgnore(transaction);
                    transactionId = rowId > 0L ? db.getTransactionId(rowId) : null;
                    if (rowId > 0L) db.saveMerchantRule(merchantText, txCategory);
                } else {
                    db.updateTransaction(existing.id, merchantText, parsed, txType, txCategory, txPayment);
                    transactionId = existing.transactionId;
                }
                db.close();

                SyncScheduler.enqueue(this, transactionId);
                dialog.dismiss();
                renderCurrentTab();
            });

            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DesignTokens.EXPENSE);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                        new AlertDialog.Builder(this)
                                .setTitle("거래 삭제")
                                .setMessage("씀에서 삭제하고 연결된 캘린더·Notion 기록도 동기화하여 정리합니다.")
                                .setPositiveButton("삭제", (d, w) -> {
                                    TransactionDb db = new TransactionDb(this);
                                    boolean changed = db.deleteTransaction(existing.id);
                                    db.close();
                                    if (changed) SyncScheduler.enqueue(this, existing.transactionId);
                                    dialog.dismiss();
                                    renderCurrentTab();
                                })
                                .setNegativeButton("취소", null)
                                .show()
                );
            }
        });
        dialog.show();
    }

    private void requestOrChooseCalendars() {
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

        Set<Long> selected = new HashSet<>(CalendarSync.getSelectedCalendarIds(this));
        String[] labels = new String[calendars.size()];
        boolean[] checked = new boolean[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            labels[i] = calendars.get(i).toString();
            checked[i] = selected.contains(calendars.get(i).id);
        }

        new AlertDialog.Builder(this)
                .setTitle("기록할 캘린더")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("저장", (dialog, which) -> {
                    List<CalendarSync.CalendarItem> chosen = new ArrayList<>();
                    for (int i = 0; i < calendars.size(); i++) {
                        if (checked[i]) chosen.add(calendars.get(i));
                    }
                    CalendarSync.saveCalendars(this, chosen);
                    resyncAll();
                    renderCurrentTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showNotionTokenDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(22), dp(4), dp(22), 0);

        TextView guide = text(
                "Notion Integration 토큰은 이 기기의 Android Keystore로 암호화해 저장하며 GitHub나 씀 서버에 저장하지 않습니다.",
                15,
                DesignTokens.MUTED,
                Typeface.NORMAL
        );
        wrap.addView(guide);

        EditText token = editField("ntn_…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(wrap, token, 14);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Notion 연결")
                .setView(wrap)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = token.getText().toString().trim();
                    if (value.length() < 10) {
                        token.setError("Notion Integration 토큰을 입력해주세요.");
                        return;
                    }
                    try {
                        SecureTokenStore.saveNotionToken(this, value);
                        resyncAll();
                        dialog.dismiss();
                        renderCurrentTab();
                    } catch (RuntimeException error) {
                        Toast.makeText(this, "토큰을 안전하게 저장하지 못했습니다.", Toast.LENGTH_LONG).show();
                    }
                })
        );
        dialog.show();
    }

    private void resyncAll() {
        TransactionDb db = new TransactionDb(this);
        List<Transaction> recent = db.getRecent(100);
        db.close();
        for (Transaction transaction : recent) {
            SyncScheduler.enqueue(this, transaction.transactionId);
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        return enabled != null && enabled.contains(getPackageName());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            if (CalendarSync.hasPermission(this)) showCalendarChooser();
            else Toast.makeText(this, "캘린더 기록을 위해 권한이 필요합니다.", Toast.LENGTH_LONG).show();
        }
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        return scroll;
    }

    private LinearLayout pageBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(22), dp(20), dp(18));
        return body;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(background(DesignTokens.SURFACE, 18, DesignTokens.LINE, 1));
        return card;
    }

    private LinearLayout metricCard(String label, String value, int valueColor) {
        LinearLayout card = card();
        card.addView(text(label, 15, DesignTokens.MUTED, Typeface.BOLD));
        addTop(card, text(value, 20, valueColor, Typeface.BOLD), 8);
        return card;
    }

    private View emptyCard(String message) {
        LinearLayout card = card();
        TextView text = text(message, 15, DesignTokens.MUTED, Typeface.NORMAL);
        text.setLineSpacing(0, 1.15f);
        card.addView(text);
        return card;
    }

    private View ratioBar(long value, long max) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(background(DesignTokens.LINE, 99, DesignTokens.LINE, 0));

        View fill = new View(this);
        fill.setBackground(background(DesignTokens.ACCENT, 99, DesignTokens.ACCENT, 0));
        float ratio = Math.max(0.04f, Math.min(1f, (float) value / (float) max));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(8), ratio));
        View rest = new View(this);
        track.addView(rest, new LinearLayout.LayoutParams(0, dp(8), 1f - ratio));
        track.setWeightSum(1f);
        return track;
    }

    private Spinner spinner(String[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                items
        );
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(8), dp(6), dp(8), dp(6));
        spinner.setBackground(background(DesignTokens.SURFACE, 12, DesignTokens.LINE, 1));
        return spinner;
    }

    private EditText editField(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setPadding(dp(14), dp(11), dp(14), dp(11));
        field.setBackground(background(DesignTokens.SURFACE, 12, DesignTokens.LINE, 1));
        return field;
    }

    private TextView fieldLabel(String label) {
        return text(label, 15, DesignTokens.MUTED, Typeface.BOLD);
    }

    private Button compactButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? android.graphics.Color.WHITE : DesignTokens.TEXT);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(background(
                primary ? DesignTokens.ACCENT : DesignTokens.SURFACE,
                14,
                primary ? DesignTokens.ACCENT : DesignTokens.LINE,
                1
        ));
        return button;
    }

    private Button actionButton(String label) {
        Button button = compactButton(label, true);
        button.setMinimumHeight(dp(48));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = compactButton(label, false);
        button.setMinimumHeight(dp(46));
        return button;
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private GradientDrawable background(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
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

    private void addTop(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private String won(long amount) {
        return numberFormat.format(amount) + "원";
    }

    private String formatTime(long millis) {
        return rowDate.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private long[] monthRange(YearMonth month) {
        ZoneId zone = ZoneId.systemDefault();
        long start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new long[]{start, end};
    }

    private long[] todayRange() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long start = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new long[]{start, end};
    }

    private void selectSpinner(Spinner spinner, String[] items, String target) {
        if (target == null) return;
        for (int i = 0; i < items.length; i++) {
            if (target.equals(items[i])) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
