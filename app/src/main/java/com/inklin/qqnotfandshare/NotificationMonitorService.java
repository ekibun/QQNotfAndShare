package com.inklin.qqnotfandshare;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationMonitorService extends NotificationListenerService {
    static final int maxCount = 10;

    public static void toggleNotificationListenerService(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, NotificationMonitorService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(new ComponentName(context, NotificationMonitorService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    // 在收到消息时触发
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        int tag = getTagfromPackageName(sbn.getPackageName());
        if(tag != 0)
            notif(sbn, tag);
    }

    public static int getTagfromPackageName(String packageName){
        switch (packageName){
            case "com.tencent.mobileqq":
                return R.string.qq;
            case "com.tencent.tim":
                return R.string.tim;
        }
        return 0;
    }

    private int getIcon(int tag){
        switch (tag){
            case R.string.qq:
                return R.drawable.ic_qq;
            case R.string.tim:
                return R.drawable.ic_tim;
        }
        return R.drawable.ic_qq;
    }

    final ArrayList<String> msgQQ = new  ArrayList<String>();
    final ArrayList<String> msgTim = new  ArrayList<String>();
    private ArrayList<String> getMsgList(int tag){
        switch (tag){
            case R.string.qq:
                return msgQQ;
            case R.string.tim:
                return msgTim;
        }
        return msgQQ;
    }

    private void notif(StatusBarNotification sbn, int tag){
        Notification notification = sbn.getNotification();
        if (notification == null)
            return;
        ArrayList<String> msgs = getMsgList(tag);
        //标题/内容
        String title = notification.extras.getString(Notification.EXTRA_TITLE);
        title = getString(tag).equals(title)? notification.extras.getString(Notification.EXTRA_TEXT):title;
        if(title == null)
            return;//QQ电话
        msgs.add(0, notification.tickerText.toString());
        int count = getNotifCount(title);
        //删除多余消息
        for(int i = Math.max(0, Math.min(count, maxCount)); i< msgs.size(); ){
            msgs.remove(i);
        }
        android.support.v4.app.NotificationCompat.InboxStyle style = new android.support.v4.app.NotificationCompat.InboxStyle();
        style.setBigContentTitle(title);
        for(String s: msgs)
            style.addLine(s);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSubText(getString(tag))
                .setContentTitle(title)
                .setContentText(msgs.get(0))
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))
                .setSmallIcon(getIcon(tag))
                .setLargeIcon((Bitmap)notification.extras.get(Notification.EXTRA_LARGE_ICON))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(notification.contentIntent)
                .setDeleteIntent(notification.deleteIntent)
                .setPriority(notification.priority)
                .setSound(notification.sound);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(tag, builder.build());
        if(Build.VERSION.SDK_INT >= 23)
            setNotificationsShown(new String[]{sbn.getKey()});
        cancelNotification(sbn.getKey());
    }

    private int getNotifCount(String title){
        int count = 1;
        Matcher matcher = Pattern.compile("(\\d+)\\S{1,3}新消息\\)?$").matcher(title);
        if (matcher.find()) {
            String s = matcher.group(1);
            count = Integer.parseInt(s);
        }
        return count;
    }

    // 在删除消息时触发
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
}
