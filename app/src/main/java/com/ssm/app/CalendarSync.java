package com.ssm.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CalendarSync {
    public static final String TARGET_GOOGLE = "calendar_google";
    public static final String TARGET_SAMSUNG = "calendar_samsung";
    private CalendarSync() {}

    public static boolean hasPermission(Context c){return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED&&c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;}

    public static List<CalendarItem> listWritableCalendars(Context context){
        List<CalendarItem> out=new ArrayList<>(); if(!hasPermission(context))return out;
        String[] p={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,CalendarContract.Calendars.ACCOUNT_NAME,CalendarContract.Calendars.ACCOUNT_TYPE};
        String sel=CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?";
        try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,p,sel,new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},CalendarContract.Calendars.CALENDAR_DISPLAY_NAME+" COLLATE NOCASE ASC")){
            if(c!=null)while(c.moveToNext())out.add(new CalendarItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
        } return out;
    }

    public static CalendarItem findBest(Context c,String target){
        List<CalendarItem> list=listWritableCalendars(c);
        for(CalendarItem item:list){String hay=(item.accountType+" "+item.accountName+" "+item.displayName).toLowerCase(Locale.ROOT); if(TARGET_GOOGLE.equals(target)&&hay.contains("google"))return item; if(TARGET_SAMSUNG.equals(target)&&(hay.contains("samsung")||hay.contains("com.samsung")||hay.contains("com.osp")||hay.contains("scloud")))return item;}
        return null;
    }

    public static String upsertTransactionEvent(Context context,Transaction tx,String target,String remoteId) throws Exception {
        if(!hasPermission(context))throw new IllegalStateException("calendar permission missing");
        CalendarItem cal=findBest(context,target); if(cal==null)throw new IllegalStateException(target+" calendar not found");
        ContentValues v=new ContentValues();v.put(CalendarContract.Events.CALENDAR_ID,cal.id);
        v.put(CalendarContract.Events.TITLE,(tx.isExpense()?"지출 ":"입금 ")+String.format(Locale.KOREA,"%,d원",tx.amount)+" · "+tx.merchant);
        v.put(CalendarContract.Events.DESCRIPTION,"씀 가계부 2.0\ntransaction_id="+tx.transactionId+"\n카테고리="+tx.category+"\n"+tx.rawText);
        v.put(CalendarContract.Events.DTSTART,tx.occurredAt);v.put(CalendarContract.Events.DTEND,tx.occurredAt+5*60*1000L);v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        ContentResolver resolver=context.getContentResolver();
        if(remoteId!=null&&!remoteId.isBlank()){
            Uri existing=ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,Long.parseLong(remoteId));
            int updated=resolver.update(existing,v,null,null); if(updated>0)return remoteId;
        }
        Uri uri=resolver.insert(CalendarContract.Events.CONTENT_URI,v); if(uri==null)throw new IllegalStateException("calendar insert returned null");
        return String.valueOf(ContentUris.parseId(uri));
    }

    public static final class CalendarItem {public final long id;public final String displayName;public final String accountName;public final String accountType;CalendarItem(long id,String d,String a,String t){this.id=id;this.displayName=d==null?"":d;this.accountName=a==null?"":a;this.accountType=t==null?"":t;} @Override public String toString(){return displayName+(accountName.isBlank()?"":" · "+accountName);} }
}
