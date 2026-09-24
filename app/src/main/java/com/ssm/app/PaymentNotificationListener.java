package com.ssm.app;
import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
public final class PaymentNotificationListener extends NotificationListenerService {
    public static final String ACTION_TRANSACTION_CHANGED="com.ssm.app.TRANSACTION_CHANGED";
    @Override public void onNotificationPosted(StatusBarNotification sbn){if(sbn==null||getPackageName().equals(sbn.getPackageName()))return;Notification n=sbn.getNotification();if(n==null)return;CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE);CharSequence text=n.extras.getCharSequence(Notification.EXTRA_TEXT);CharSequence big=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);Transaction tx=NotificationParser.parse(sbn.getKey(),sbn.getPackageName(),title==null?"":title.toString(),text==null?"":text.toString(),big==null?"":big.toString(),sbn.getPostTime());if(tx==null)return;if(new TransactionRepository(getApplicationContext()).addParsed(tx)){Intent changed=new Intent(ACTION_TRANSACTION_CHANGED);changed.setPackage(getPackageName());sendBroadcast(changed);}}
}
