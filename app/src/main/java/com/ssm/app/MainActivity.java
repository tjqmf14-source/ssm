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
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 1001;
    private static final String[] CATEGORIES = {"식비","카페","쇼핑","교통","생활","구독","의료","기타"};
    private static final String[] METHODS = {"삼성페이","네이버페이","카드","현금","기타"};
    private LinearLayout root;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(48));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
    }

    @Override @SuppressLint("UnspecifiedRegisterReceiverFlag") protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(PaymentNotificationListener.ACTION_TRANSACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onStop() { super.onStop(); unregisterReceiver(receiver); }
    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        if (root == null) return;
        root.removeAllViews();
        LocalDate today = LocalDate.now();
        long start = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expense, income; int pending; List<Transaction> recent;
        try (TransactionDb db = new TransactionDb(this)) {
            expense = db.sumByTypeBetween(Transaction.TYPE_EXPENSE, start, end);
            income = db.sumByTypeBetween(Transaction.TYPE_INCOME, start, end);
            pending = db.pendingSyncCount();
            recent = db.getRecent(20);
        }

        addTitle("씀", 30);
        addText("이번 달 지출  " + money(expense), 22);
        addText("입금 +" + money(income) + " · 동기화 대기 " + pending + "건", 14);
        addButton("+ 거래 직접 추가", v -> showManualDialog());
        addButton("외부 연동 다시 시도", v -> { SyncCoordinator.runDue(this, 30); SyncCoordinator.schedule(this); render(); });
        addButton("알림 접근 설정", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton("캘린더 권한", v -> ensureCalendarPermission());
        addButton("Notion 토큰 설정", v -> showNotionTokenDialog());

        addTitle("최근 거래", 20);
        if (recent.isEmpty()) addText("아직 저장된 거래가 없습니다.", 14);
        for (Transaction tx : recent) addTransaction(tx);

        addTitle("연동 상태", 20);
        addText("Google Calendar · " + (CalendarSync.findBest(this, CalendarSync.TARGET_GOOGLE) == null ? "미감지" : "감지됨"), 14);
        addText("삼성 캘린더 · " + (CalendarSync.findBest(this, CalendarSync.TARGET_SAMSUNG) == null ? "미감지" : "감지됨"), 14);
        addText("Notion · " + (SecretStore.hasNotionToken(this) ? "토큰 저장됨" : "토큰 필요"), 14);
    }

    private void addTransaction(Transaction tx) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, dp(10), 0, dp(10));
        TextView line = new TextView(this); line.setText(tx.merchant + "  " + (tx.isExpense() ? "-" : "+") + money(tx.amount) + "\n" + tx.category + " · " + tx.paymentMethod); line.setTextSize(15); box.addView(line);
        LinearLayout actions = new LinearLayout(this);
        Button edit = new Button(this); edit.setText("수정"); edit.setOnClickListener(v -> showEditDialog(tx)); actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        Button del = new Button(this); del.setText("삭제"); del.setOnClickListener(v -> confirmDelete(tx)); actions.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(actions); root.addView(box);
    }

    private void showManualDialog() {
        LinearLayout form = form(); Spinner type = spinner(new String[]{"지출","입금"},0); EditText amount = input("금액", InputType.TYPE_CLASS_NUMBER); EditText merchant = input("사용처", InputType.TYPE_CLASS_TEXT); Spinner category = spinner(CATEGORIES,7); Spinner method = spinner(METHODS,4);
        form.addView(type); form.addView(amount); form.addView(merchant); form.addView(category); form.addView(method);
        new AlertDialog.Builder(this).setTitle("거래 추가").setView(form).setPositiveButton("저장", (d,w) -> {
            long value = parseAmount(amount.getText().toString()); String name = merchant.getText().toString().trim();
            if (value <= 0 || name.isEmpty()) { Toast.makeText(this,"금액과 사용처를 확인해주세요.",Toast.LENGTH_LONG).show(); return; }
            new TransactionRepository(this).createManual(value, type.getSelectedItemPosition()==0 ? Transaction.TYPE_EXPENSE : Transaction.TYPE_INCOME, name, (String)category.getSelectedItem(), (String)method.getSelectedItem()); render();
        }).setNegativeButton("취소", null).show();
    }

    private void showEditDialog(Transaction tx) {
        LinearLayout form = form(); EditText merchant = input("사용처", InputType.TYPE_CLASS_TEXT); merchant.setText(tx.merchant); Spinner category = spinner(CATEGORIES,indexOf(CATEGORIES,tx.category)); Spinner method = spinner(METHODS,indexOf(METHODS,tx.paymentMethod));
        form.addView(merchant); form.addView(category); form.addView(method);
        new AlertDialog.Builder(this).setTitle("거래 보정").setView(form).setPositiveButton("저장", (d,w) -> {
            String name = merchant.getText().toString().trim(); if (name.isEmpty()) return;
            new TransactionRepository(this).correct(tx.transactionId,name,(String)category.getSelectedItem(),(String)method.getSelectedItem()); render();
        }).setNegativeButton("취소", null).show();
    }

    private void confirmDelete(Transaction tx) {
        new AlertDialog.Builder(this).setTitle("거래 삭제").setMessage("로컬 거래를 삭제합니다. 이미 생성된 외부 항목은 자동 삭제하지 않습니다.").setPositiveButton("삭제",(d,w)->{ new TransactionRepository(this).delete(tx.transactionId); render(); }).setNegativeButton("취소",null).show();
    }

    private void showNotionTokenDialog() {
        EditText token = input("Integration token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Notion 토큰 설정").setView(token).setPositiveButton("저장",(d,w)->{
            try { SecretStore.saveNotionToken(this, token.getText().toString()); SyncCoordinator.schedule(this); render(); }
            catch (Exception e) { Toast.makeText(this,"토큰 저장 실패",Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("취소",null).show();
    }

    private void ensureCalendarPermission() {
        if (CalendarSync.hasPermission(this)) return;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR);
    }

    private void addTitle(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(25,30,29)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(18); root.addView(v,p); }
    private void addText(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(8); root.addView(v,p); }
    private void addButton(String text, View.OnClickListener listener) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(listener); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(52)); p.topMargin=dp(8); root.addView(b,p); }
    private LinearLayout form() { LinearLayout f = new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(dp(20),0,dp(20),0); return f; }
    private EditText input(String hint,int type) { EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); return e; }
    private Spinner spinner(String[] values,int selected) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values)); s.setSelection(Math.max(0,Math.min(selected,values.length-1))); return s; }
    private int indexOf(String[] values,String target) { for(int i=0;i<values.length;i++) if(values[i].equals(target)) return i; return values.length-1; }
    private long parseAmount(String value) { try { return Long.parseLong(value.replace(",","").trim()); } catch(Exception e) { return -1; } }
    private String money(long amount) { return won.format(amount) + "원"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
