package com.inklin.qqnotfandshare;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
                return PreferenceManager.getDefaultSharedPreferences(this).
                        getBoolean("use_full_icon", false) ? R.drawable.ic_qq_full : R.drawable.ic_qq;
            case R.string.tim:
                return R.drawable.ic_tim;
        }
        return R.drawable.ic_qq;
    }

    final ArrayList<String> msgQQ = new  ArrayList<String>();
    final ArrayList<String> msgTim = new  ArrayList<String>();
    final ArrayList<String> msgQzone = new  ArrayList<String>();
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
        //标题/内容
        String notf_title = notification.extras.getString(Notification.EXTRA_TITLE);
        String notf_text = notification.extras.getString(Notification.EXTRA_TEXT);
        if(notf_text!= null && !notf_text.isEmpty())
            notf_text = notf_text.replaceAll("\n", " ");
        String notf_ticker = "";
        if(notification.tickerText!= null){
            notf_ticker = notification.tickerText.toString();
            notf_ticker = notf_ticker.replaceAll("\n", " ");
        }
        boolean mul = getString(tag).equals(notf_title);
        String title = mul? notf_text: notf_title; ///notification.extras.getString(Notification.EXTRA_TITLE);
        //title = getString(tag).equals(title)? notification.extras.getString(Notification.EXTRA_TEXT):title;
        //非消息
        if(title == null || notf_ticker.isEmpty() || title.equals(notf_ticker))
            return;
        String msg = notf_ticker + "\n" + (mul ? notf_ticker :notf_text);

        //单独处理QQ空间
        boolean isQzone = false;
        int count = 1;
        Matcher matcher = Pattern.compile("QQ空间动态\\(共(\\d+)条未读\\)$").matcher(title);
        if (matcher.find() && notf_ticker.equals(notf_text)){
            isQzone = true;
            count = Integer.parseInt(matcher.group(1));
        }

        ArrayList<String> msgs = isQzone ? msgQzone : getMsgList(tag);
        msgs.add(0, msg);
        //消息数量
        if(!isQzone){
            matcher = Pattern.compile("(\\d+)\\S{1,3}新消息\\)?$").matcher(title);
            if (matcher.find())
                count = Integer.parseInt(matcher.group(1));
        }
        //删除多余消息
        for(int i = Math.max(0, Math.min(count, maxCount)); i< msgs.size(); ){
            msgs.remove(i);
        }
        Notification.InboxStyle style = new Notification.InboxStyle();
        style.setBigContentTitle(title);
        String first = "";
        for(String s: msgs) {
            String[] ss = s.split("\n");
            String m = ss.length > 1 ? ss[mul ? 0: 1] : s;
            style.addLine(m);
            if(first.isEmpty())
                first = m;
        }
        Notification.Builder builder = new Notification.Builder(this)
                .setSubText(getString(tag))
                .setContentTitle(title)
                .setContentText(first)
                .setColor(ContextCompat.getColor(getApplicationContext(), isQzone? R.color.colorQzone : R.color.colorPrimary))
                .setSmallIcon(isQzone? R.drawable.ic_qzone : getIcon(tag))
                .setLargeIcon((Bitmap)notification.extras.get(Notification.EXTRA_LARGE_ICON))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(notification.contentIntent)
                .setDeleteIntent(notification.deleteIntent)
                .setPriority(notification.priority)
                .setSound(notification.sound)
                .setLights(notification.ledARGB, notification.ledOnMS, notification.ledOffMS)
                .setVibrate(notification.vibrate);
        if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_default_sound", false))
            builder.setDefaults(Notification.DEFAULT_SOUND);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(tag + (isQzone? 1 : 0), builder.build());
        if(Build.VERSION.SDK_INT >= 23)
            setNotificationsShown(new String[]{sbn.getKey()});
        cancelNotification(sbn.getKey());
    }
/*
    private int getNotifCount(String title){
        int count = 1;
        Matcher matcher = Pattern.compile("(\\d+)\\S{1,3}新消息\\)?$").matcher(title);
        if (matcher.find()) {
            String s = matcher.group(1);
            count = Integer.parseInt(s);
        }
        return count;
    }
*/
    // 在删除消息时触发
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
}
