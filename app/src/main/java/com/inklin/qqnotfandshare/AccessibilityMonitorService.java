package com.inklin.qqnotfandshare;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

public class AccessibilityMonitorService extends AccessibilityService {


    @Override
    protected void onServiceConnected() {
        //辅助服务被打开后 执行此方法
        super.onServiceConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.v("class",event.getClassName().toString());
                if("com.tencent.mobileqq.activity.SplashActivity".equals(event.getClassName()) ||
                        "com.dataline.activities.LiteActivity".equals(event.getClassName())){
                    int tag = NotificationMonitorService.getTagfromPackageName(event.getPackageName().toString());
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(tag);
                }
        }
    }

    @Override
    public void onInterrupt() {
        //辅助服务被关闭 执行此方法

    }
}
