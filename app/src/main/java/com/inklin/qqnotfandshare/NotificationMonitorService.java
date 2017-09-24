package com.inklin.qqnotfandshare;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.inklin.qqnotfandshare.utils.FileUtils;
import com.inklin.qqnotfandshare.utils.PreferencesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationMonitorService extends NotificationListenerService {
    public static final int id_qq = 1;
    public static final int id_qqlite = 2;
    public static final int id_tim = 3;
    public static final int id_qzone = 4;
    public static final int id_group0 = 5;

    private static final int maxCount = 20;

    // 在收到消息时触发
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        int tag = getTagfromPackageName(sbn.getPackageName());
        if(tag != 0)
            notif(sbn, tag);
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        if(intent != null){
            if(intent.hasExtra("tag")){
                int tag = intent.getIntExtra("tag", 0);
                if(tag > 0){
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancel(tag);
                    if(tag != id_qzone){
                        for(StatusBarNotification sbn : getActiveNotifications()){
                            if(!getPackageName().equals(sbn.getPackageName()) || sbn.getId() < id_group0)
                                continue;
                            nm.cancel(sbn.getId());
                        }
                        notifs.clear();
                    }
                }
            }else if(intent.hasExtra("notfClick")){
                if(lastIntent != null){
                    notifs.clear();
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    for(StatusBarNotification sbn : getActiveNotifications()){
                        if(!getPackageName().equals(sbn.getPackageName()) || sbn.getId() < id_group0)
                            continue;
                        nm.cancel(sbn.getId());
                    }
                    try {
                        lastIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
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
        else
            notf_text = "";
        String notf_ticker = "";
        if(notification.tickerText!= null){
            notf_ticker = notification.tickerText.toString();
            notf_ticker = notf_ticker.replaceAll("\n", " ");
        }
        //多人消息
        boolean mul = !notf_text.contains(":") && !notf_ticker.endsWith(notf_text);
        String title = mul? notf_text: notf_title;
        if(title == null || notf_ticker.isEmpty() || title.equals(notf_ticker))
            return;

        //单独处理QQ空间
        boolean isQzone = false;
        int count = 1;
        Matcher matcher = Pattern.compile("QQ空间动态\\(共(\\d+)条未读\\)$").matcher(title);
        if (notf_ticker.equals(notf_text)){
            if(matcher.find()){
                isQzone = true;
                count = Integer.parseInt(matcher.group(1));
            }else if("QQ空间动态".equals(title)){
                isQzone = true;
                count = maxCount;
            }
        }
        //消息数量
        if(!isQzone){
            matcher = Pattern.compile("(\\d+)\\S{1,3}新消息\\)?$").matcher(title);
            if (matcher.find())
                count = Integer.parseInt(matcher.group(1));
        }
        int maxMsgLength = getMaxMsgLength();
        if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_group", false) ?
                notif_group(count, tag, title, notf_ticker, notf_text, mul, isQzone, notification, maxMsgLength) :
                notif_list(count, tag, title, notf_ticker, notf_text, mul, isQzone, notification, maxMsgLength)){
            if(Build.VERSION.SDK_INT >= 23)
                setNotificationsShown(new String[]{sbn.getKey()});
            cancelNotification(sbn.getKey());
        }
    }

    private class NotifInfo{
        String name;
        List<String> msgs = new ArrayList<>();
    }

    final ArrayList<NotifInfo> notifs = new ArrayList<>();
    private boolean notif_group(int count, int tag, String title, String notf_ticker, String notf_text, boolean mul, boolean isQzone, Notification notification, int maxMsgLength){
        if(isQzone)
            return notif_list(count, tag, title, notf_ticker, notf_text, mul, isQzone, notification, maxMsgLength);
        String name = null, text = null;
        boolean isGroupMsg = false;
        Matcher matcher = Pattern.compile("(.*)\\((.+)\\):(.+)").matcher(notf_ticker);
        if(matcher.find()){
            name = matcher.group(2);
            text = matcher.group(1) + ":" + matcher.group(3);
            isGroupMsg = true;
        }else{
            matcher = Pattern.compile("(.+):(.+)").matcher(notf_ticker);
            if(matcher.find()){
                name = matcher.group(1);
                text = matcher.group(2);
            }
        }
        if(name == null || text == null)
            return notif_list(count, tag, title, notf_ticker, notf_text, mul, isQzone, notification, maxMsgLength);

        NotifInfo newInfo = new NotifInfo();
        newInfo.name = name;
        newInfo.msgs.add(text);
        int id = -1;
        for(int i = 0; i< notifs.size(); i++){
            NotifInfo notif = notifs.get(i);
            if(name.equals(notif.name)){
                id = i;
                newInfo.msgs.addAll(notif.msgs);
                break;
            }
        }
        if(id < 0)
            id = notifs.size();
        if(id >= notifs.size())
            notifs.add(newInfo);
        else
            notifs.set(id, newInfo);

        Notification.Style style;
        if(maxMsgLength > 0){
            Notification.BigTextStyle bstyle = new Notification.BigTextStyle();
            bstyle.setBigContentTitle(name);
            for(int m = 1; m < newInfo.msgs.size() && m < maxCount; m++){
                String msg = newInfo.msgs.get(m);
                if(msg.length() > maxMsgLength)
                    msg = msg.substring(0, maxMsgLength);
                text = text + "\n" + msg;
            }
            bstyle.bigText(text);
            style = bstyle;
        }else{
            Notification.InboxStyle istyle = new Notification.InboxStyle();
            istyle.setBigContentTitle(name);
            for(int m = 0; m < newInfo.msgs.size() && m < maxCount; m++){
                String msg = newInfo.msgs.get(m);
                istyle.addLine(msg);
            }
            style = istyle;
        }

        buildNotification(name, text, isQzone, mul, tag, style, notification, false, true, id  + 1 + id_group0, isGroupMsg);

        if(Build.VERSION.SDK_INT >= 24 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_notif_group", false))//Nougat
            buildNotification(name, text, isQzone, mul, tag, null, notification, true, true, id_group0, false);
        /*
        for(int i = 0; i< notifs.size(); i++){
            Notification notif = notifs.get(i);
            if(name.equals(notif.extras.getString(Notification.EXTRA_TITLE))){
                id = i;
                if(maxMsgLength > 0){
                    String btext = notif.extras.getString(Notification.EXTRA_BIG_TEXT);
                    if(btext == null || btext.isEmpty())
                        break;
                    if(btext.length() > maxCount * maxMsgLength)
                        btext = btext.substring(0, maxCount * maxMsgLength);
                    text = text + "\n" + btext;

                }else{
                    CharSequence[] omsg = notif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if(omsg == null)
                        break;
                    int ic = 0;
                    for(CharSequence c: omsg){
                        ic++;
                        istyle.addLine(c);
                        if(ic > maxCount)
                            break;
                    }
                }
                break;
            }
        }
        bstyle.setBigContentTitle(name);
        bstyle.bigText(text);
        if(id < 0)
            id = notifs.size();
        Notification notif = buildNotification(name, text, isQzone, mul, tag, maxMsgLength > 0 ? bstyle : istyle, notification, false, true, id  + 1 + id_group0);
        if(id >= notifs.size())
            notifs.add(notif);
        else
            notifs.set(id, notif);
        if(Build.VERSION.SDK_INT >= 24)//Nougat
            buildNotification(name, text, isQzone, mul, tag, null, notification, true, true, id_group0);*/
        return true;
    }

    private boolean notif_list(int count, int tag,String title, String notf_ticker, String notf_text, boolean mul, boolean isQzone, Notification notification, int maxMsgLength){
        String msg = notf_ticker + "\n" + (mul ? notf_ticker :notf_text);
        ArrayList<String> msgs = isQzone ? msgQzone : getMsgList(tag);
        msgs.add(0, msg);
        //删除多余消息
        for(int i = count; i< msgs.size(); ){
            msgs.remove(i);
        }
        String first = "";
        Notification.InboxStyle istyle = new Notification.InboxStyle();
        Notification.BigTextStyle bstyle = new Notification.BigTextStyle();
        istyle.setBigContentTitle(title);
        for (String s : msgs) {
            count--;
            String[] ss = s.split("\n");
            String m = ss.length > 1 ? ss[mul ? 0 : 1] : s;
            if(maxMsgLength > 0){
                if (m.length() > maxMsgLength)
                    m = m.substring(0, maxMsgLength) + "...";
                if (first.isEmpty())
                    first = m;
                else
                    first = first + "\n" + m;
            }else{
                istyle.addLine(m);
                if (first.isEmpty())
                    first = m;
            }
            if (count == 0)
                break;
        }
        buildNotification(title, first, isQzone, mul, tag, maxMsgLength > 0 ? bstyle : istyle, notification, false, false, isQzone ? id_qzone : tag, false);
        return true;
    }

    PendingIntent lastIntent;
    private Notification buildNotification(String title, String text, boolean isQzone, boolean mul, int tag, Notification.Style style, Notification notification, boolean setGroupSummary, boolean group, int id, boolean isGroupMsg){
        boolean noFloat = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("disable_float", false);
        noFloat |= isGroupMsg && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("ignore_group_float", false);
        Notification.Builder builder = new Notification.Builder(this)
                //.setSubText(getString(getStringId(tag)))
                .setContentTitle(title)
                .setContentText(text)
                .setColor( getResources().getColor(isQzone ? R.color.colorQzone : R.color.colorPrimary))
                //.setSmallIcon(isQzone ? R.drawable.ic_qzone : getIcon(tag))
                //.setLargeIcon((Bitmap) notification.extras.get(Notification.EXTRA_LARGE_ICON))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(notification.contentIntent)
                .setDeleteIntent(notification.deleteIntent)
                .setPriority(setGroupSummary ? Notification.PRIORITY_MIN : noFloat && notification.priority > Notification.PRIORITY_DEFAULT ?
                        Notification.PRIORITY_DEFAULT : notification.priority)
                //.setSound(notification.sound)
                .setLights(notification.ledARGB, notification.ledOnMS, notification.ledOffMS)
                .setVibrate(notification.vibrate)
                .setShowWhen(true)
                .setGroupSummary(setGroupSummary);
        setIcon(builder, tag, isQzone);

        Bitmap bmp = (Bitmap) notification.extras.get(Notification.EXTRA_LARGE_ICON);
        if(!isQzone && group){
            lastIntent = notification.contentIntent;
            Intent notificationIntent = new Intent(this, NotificationMonitorService.class);
            notificationIntent.putExtra("notfClick",true);
            PendingIntent pendingIntent = PendingIntent.getService(this.getApplicationContext(), 0, notificationIntent, 0);
            builder.setContentIntent(pendingIntent);
            if(mul){
                Bitmap cache = FileUtils.getBitmapFromCache(this, title, "profile");
                if(cache != null)
                    bmp = cache;
            }else if(bmp != null){
                FileUtils.saveBitmapToCache(this, bmp, title, "profile", false);
            }
        }
        builder.setLargeIcon(bmp);

        if(group)
            builder.setGroup("GROUP");
        boolean sound = !isGroupMsg || !PreferenceManager.getDefaultSharedPreferences(this).getBoolean("ignore_group", false);
        if(!setGroupSummary && sound)
            builder.setSound(PreferencesUtils.getRingtone(this));
        /*
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_default_sound", false))
            builder.setDefaults(Notification.DEFAULT_SOUND);
            */
        Notification notf = builder.build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, notf);
        return notf;
    }

    final ArrayList<String> msgQQ = new  ArrayList<String>();
    final ArrayList<String> msgQQLite = new  ArrayList<String>();
    final ArrayList<String> msgTim = new  ArrayList<String>();
    final ArrayList<String> msgQzone = new  ArrayList<String>();
    private ArrayList<String> getMsgList(int tag){
        switch (tag){
            case id_qq://R.string.qq:
                return msgQQ;
            case id_tim://R.string.tim:
                return msgTim;
            case id_qqlite://R.string.qqlite:
                return msgQQLite;
        }
        return msgQQ;
    }

    private int getStringId(int tag){
        switch (tag){
            case id_qq://R.string.qq:
                return R.string.qq;
            case id_tim://R.string.tim:
                return R.string.tim;
            case id_qqlite://R.string.qqlite:
                return R.string.qqlite;
        }
        return R.string.qq;
    }

    private String path = "";
    private Icon icon;
    private void setIcon(Notification.Builder builder, int tag, boolean isQzone){
        if(isQzone) {
            builder.setSmallIcon(R.drawable.ic_qzone);
            return;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int mode = Integer.parseInt(sp.getString("icon_mode","0"));
        if(mode == 2 && Build.VERSION.SDK_INT >= 23){
            String s = sp.getString("icon_path","");
            if(icon == null || !s.equals(path)){
                path = s;
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if(bmp != null)
                    icon = Icon.createWithBitmap(bmp);
            }
            if(icon != null) {
                builder.setSmallIcon(icon);
                return;
            }
        }
        int iconRes = R.drawable.ic_qq;
        switch (tag){
            case id_qq://R.string.qq:
            case id_qqlite://R.string.qqlite:
                iconRes =  mode == 1? R.drawable.ic_qq_full : R.drawable.ic_qq;
                break;
            case id_tim://R.string.tim:
                iconRes =  R.drawable.ic_tim;
                break;
        }
        builder.setSmallIcon(iconRes);
    }

    public static int getTagfromPackageName(String packageName){
        switch (packageName){
            case "com.tencent.mobileqq":
                return id_qq;//R.string.qq;
            case "com.tencent.tim":
                return id_tim;//R.string.tim;
            case "com.tencent.qqlite":
                return id_qqlite;//R.string.qqlite;
        }
        return 0;
    }

    private int getMaxMsgLength(){
        if(!PreferenceManager.getDefaultSharedPreferences(this).
                getBoolean("use_multi_line", false))
            return 0;
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).
                getString("max_single_msg", "0"));
    }
}

/*
public class NotificationMonitorService extends NotificationListenerService {

    private static final int id_qq = 1;
    private static final int id_qqlite = 2;
    private static final int id_tim = 3;
    private static final int id_qzone = 4;
    private static final int id_group0 = 5;

    static final int maxCount = 20;

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
                return id_qq;//R.string.qq;
            case "com.tencent.tim":
                return id_tim;//R.string.tim;
            case "com.tencent.qqlite":
                return id_qqlite;//R.string.qqlite;
        }
        return 0;
    }

    private int getIcon(int tag){
        switch (tag){
            case id_qq://R.string.qq:
            case id_qqlite://R.string.qqlite:
                return PreferenceManager.getDefaultSharedPreferences(this).
                        getBoolean("use_full_icon", false) ? R.drawable.ic_qq_full : R.drawable.ic_qq;
            case id_tim://R.string.tim:
                return R.drawable.ic_tim;
        }
        return R.drawable.ic_qq;
    }

    private int getMaxMsgLength(){
        if(!PreferenceManager.getDefaultSharedPreferences(this).
                getBoolean("use_multi_line", false))
            return 0;
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).
                getString("max_single_msg", "0"));
    }

    final ArrayList<String> msgQQ = new  ArrayList<String>();
    final ArrayList<String> msgQQLite = new  ArrayList<String>();
    final ArrayList<String> msgTim = new  ArrayList<String>();
    final ArrayList<String> msgQzone = new  ArrayList<String>();
    private ArrayList<String> getMsgList(int tag){
        switch (tag){
            case id_qq://R.string.qq:
                return msgQQ;
            case id_tim://R.string.tim:
                return msgTim;
            case id_qqlite://R.string.qqlite:
                return msgQQLite;
        }
        return msgQQ;
    }

    private int getStringId(int tag){
        switch (tag){
            case id_qq://R.string.qq:
                return R.string.qq;
            case id_tim://R.string.tim:
                return R.string.tim;
            case id_qqlite://R.string.qqlite:
                return R.string.qqlite;
        }
        return R.string.qq;
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
        else
            notf_text = "";
        String notf_ticker = "";
        if(notification.tickerText!= null){
            notf_ticker = notification.tickerText.toString();
            notf_ticker = notf_ticker.replaceAll("\n", " ");
        }

        boolean mul = !notf_text.contains(":") && !notf_ticker.endsWith(notf_text);
        //boolean mul = !notf_ticker.endsWith(notf_text); //getString(tag).equals(notf_title);
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
        if (notf_ticker.equals(notf_text)){
            if(matcher.find()){
                isQzone = true;
                count = Integer.parseInt(matcher.group(1));
            }else if("QQ空间动态".equals(title)){
                return;//不拦截
            }
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
        for(int i = count; i< msgs.size(); ){
            msgs.remove(i);
        }
        //count = Math.min(count, msgs.size());

        if(!isQzone)
            notf_group(msg, tag, notification);
        else {
            notf_list(msgs, mul, isQzone, title, tag, count, notification);
        }

        if(Build.VERSION.SDK_INT >= 23)
            setNotificationsShown(new String[]{sbn.getKey()});
        cancelNotification(sbn.getKey());
    }

    private void notf_list(ArrayList<String> msgs, boolean mul, boolean isQzone, String title, int tag, int count, Notification notification){
        Notification.Style style;
        int maxMsgLength = getMaxMsgLength();
        String first = "";
        if (maxMsgLength <= 0) {
            Notification.InboxStyle istyle = new Notification.InboxStyle();
            istyle.setBigContentTitle(title);
            for (String s : msgs) {
                count--;
                String[] ss = s.split("\n");
                String m = ss.length > 1 ? ss[mul ? 0 : 1] : s;
                istyle.addLine(m);
                if (first.isEmpty())
                    first = m;
                if (count == 0)
                    break;
            }
            style = istyle;
        } else {
            Notification.BigTextStyle bstyle = new Notification.BigTextStyle();
            for (String s : msgs) {
                count--;
                String[] ss = s.split("\n");
                String m = ss.length > 1 ? ss[mul ? 0 : 1] : s;
                if (m.length() > maxMsgLength)
                    m = m.substring(0, maxMsgLength) + "...";
                first = first + m;
                if (count == 0)
                    break;
                else
                    first = first + "\n";
            }
            style = bstyle;
        }
        Notification.Builder builder = new Notification.Builder(this)
                .setSubText(getString(getStringId(tag)))
                .setContentTitle(title)
                .setContentText(first)
                .setColor(ContextCompat.getColor(getApplicationContext(), isQzone ? R.color.colorQzone : R.color.colorPrimary))
                .setSmallIcon(isQzone ? R.drawable.ic_qzone : getIcon(tag))
                .setLargeIcon((Bitmap) notification.extras.get(Notification.EXTRA_LARGE_ICON))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(notification.contentIntent)
                .setDeleteIntent(notification.deleteIntent)
                .setPriority(notification.priority)
                .setSound(notification.sound)
                .setLights(notification.ledARGB, notification.ledOnMS, notification.ledOffMS)
                .setVibrate(notification.vibrate);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_default_sound", false))
            builder.setDefaults(Notification.DEFAULT_SOUND);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(isQzone ? id_qzone : tag, builder.build());
    }

    private void notf_group(String msg, int tag, Notification notification){
        //String msg = msgs.get(0);
        String name = null, text = null;
        Matcher matcher = Pattern.compile("(.+)\\((.+)\\):(.+)").matcher(msg);
        if(matcher.find()){
            name = matcher.group(2);
            text = matcher.group(1) + ":" + matcher.group(3);
        }else{
            matcher = Pattern.compile("(.+):(.+)").matcher(msg);
            if(matcher.find()){
                name = matcher.group(1);
                text = matcher.group(2);
            }
        }
        if(name == null || text == null)
            return;
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(name);
        style.addLine(text);
        //ArrayList<String> strings = new ArrayList<>();
        //strings.add(text);
        int id = 0;
        int maxid = id_group0;
        boolean hasGroup = false;
        for(StatusBarNotification sbn : getActiveNotifications()){
            if(!getPackageName().equals(sbn.getPackageName()) || sbn.getId() < id_group0)
                continue;
            if( sbn.getId() == id_group0)
                hasGroup = true;
            if(!name.equals(sbn.getNotification().extras.getString(Notification.EXTRA_TITLE))){
                maxid = Math.max(maxid,sbn.getId());
                continue;
            }
            CharSequence[] omsg = sbn.getNotification().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if(omsg == null)
                continue;
            id = sbn.getId();
            for(CharSequence c: omsg)
                style.addLine(c);//strings.add(c.toString());
        }
        if(id == 0)
            id = maxid + 1;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                //.setSubText(getString(getStringId(tag)))
                .setContentTitle(name)
                .setContentText(text)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))
                .setSmallIcon(getIcon(tag))
                .setLargeIcon((Bitmap) notification.extras.get(Notification.EXTRA_LARGE_ICON))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(notification.contentIntent)
                .setDeleteIntent(notification.deleteIntent)
                .setPriority(notification.priority)
                .setSound(notification.sound)
                .setLights(notification.ledARGB, notification.ledOnMS, notification.ledOffMS)
                .setVibrate(notification.vibrate)
                .setGroup("QQNotifAndShare");
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_default_sound", false))
            builder.setDefaults(Notification.DEFAULT_SOUND);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, builder.build());

        if(Math.max(id, maxid) > id_group0 + 1){//超过一个通知
            NotificationCompat.Builder groupBuilder = new NotificationCompat.Builder(this)
                    .setSubText(getString(getStringId(tag)))
                    //.setContentTitle(name)
                    //.setContentText(text)
                    .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))
                    .setSmallIcon(getIcon(tag))
                    .setGroupSummary(true)
                    .setGroup("QQNotifAndShare");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(id_group0, groupBuilder.build());
        }

    }

    // 在删除消息时触发
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
}
*/