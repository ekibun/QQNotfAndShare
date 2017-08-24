package com.inklin.qqnotfandshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.finish();
        Intent intent = new Intent(this, PreferencesActivity.class);
        this.startActivity(intent);
    }
}
