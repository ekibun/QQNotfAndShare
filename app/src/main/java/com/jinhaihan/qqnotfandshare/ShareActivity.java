package com.jinhaihan.qqnotfandshare;

import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.jinhaihan.qqnotfandshare.utils.FileUtils;
import com.tencent.connect.share.QQShare;
import com.tencent.tauth.Tencent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (type == null || (!Intent.ACTION_SEND.equals(action) &&
                !Intent.ACTION_SEND_MULTIPLE.equals(action)))
            this.finish();
        Log.v("type", type);

        if(Intent.ACTION_SEND.equals(action)){
            if ("text/plain".equals(type)) {
                final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null){
                    //if (sharedText.startsWith("http")) {
                    //    shareUrl(sharedText, sharedText);
                    //    return;
                    //}
                    Matcher matcher = Pattern.compile("((http|ftp|https)://)(([a-zA-Z0-9\\._-]+\\.[a-zA-Z]{2,6})|([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}))(:[0-9]{1,4})*(/[a-zA-Z0-9\\&%_\\./-~-]*)?").matcher(sharedText);
                    if (matcher.find()) {
                        final String url;
                        url = matcher.group();
                        Log.d("url", url);
                        matcher = Pattern.compile("^(http|https)://music.163.com/song/").matcher(url);
                        if(matcher.find()){
                            shareMusic(sharedText, url);
                        }else
                            shareUrl(sharedText, url);
                        return;
                    }
                }
            }
            if(type.startsWith("image/")){
                Log.v("uri",((Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM)).toString());
                Uri uri = fixUri((Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM), type);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            }
        }

        //TODO
        //startActivity(generateCustomChooserIntent(intent));

        if(isAppInstalled(this, "com.tencent.mobileqq")){
            intent.setClassName("com.tencent.mobileqq","com.tencent.mobileqq.activity.JumpActivity");
            startActivity(intent);
        } else if(isAppInstalled(this, "com.tencent.tim")){
            intent.setClassName("com.tencent.tim","com.tencent.mobileqq.activity.JumpActivity");
            startActivity(intent);
        } else if(isAppInstalled(this, "com.tencent.qqlite")){
            intent.setClassName("com.tencent.qqlite","com.tencent.mobileqq.activity.JumpActivity");
            startActivity(intent);
        }
    }

    private Intent generateCustomChooserIntent(Intent prototype) {
        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        List<HashMap<String, String>> intentMetaInfo = new ArrayList<HashMap<String, String>>();
        Intent chooserIntent;

        Intent dummy = new Intent(prototype.getAction());
        dummy.setType(prototype.getType());
        List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(dummy, 0);

        if (!resInfo.isEmpty()) {
            for (ResolveInfo resolveInfo : resInfo) {
                if (resolveInfo.activityInfo == null || !TextUtils.equals("com.tencent.mobileqq.activity.JumpActivity", resolveInfo.activityInfo.name))
                    continue;

                Intent targetedShareIntent = (Intent)prototype.clone();
                //targetedShareIntent.setPackage(resolveInfo.activityInfo.packageName);
                targetedShareIntent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                targetedShareIntents.add(targetedShareIntent);
            }
            chooserIntent = Intent.createChooser(targetedShareIntents.remove(targetedShareIntents.size() - 1), getString(R.string.activity_share));
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray(new Parcelable[]{}));
            return chooserIntent;
        }

        return Intent.createChooser(prototype, getString(R.string.activity_share));
    }

    public static String get(String url) {
        HttpURLConnection conn = null;
        try {
            // 利用string url构建URL对象
            URL mURL = new URL(url);
            conn = (HttpURLConnection) mURL.openConnection();

            conn.setRequestMethod("GET");
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {

                InputStream is = conn.getInputStream();
                String response = getStringFromInputStream(is);
                return response;
            } else {
                throw new NetworkErrorException("response status is "+responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }

        return null;
    }

    private static String getStringFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        // 模板代码 必须熟练
        byte[] buffer = new byte[1024];
        int len = -1;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        is.close();
        String state = os.toString();// 把流中的数据转换成字符串,采用的编码是utf-8(模拟器默认编码)
        os.close();
        return state;
    }

    private boolean isAppInstalled(Context context,String packagename)
    {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packagename, 0);
        }catch (PackageManager.NameNotFoundException e) {
            packageInfo = null;
            e.printStackTrace();
        }
        if(packageInfo ==null){
            //System.out.println("没有安装");
            return false;
        }else{
            //System.out.println("已经安装");
            return true;
        }
    }

    private void shareUrl(String title, String url){
        Tencent mTencent = Tencent.createInstance("1106259735", getApplicationContext());
        Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
        params.putString(QQShare.SHARE_TO_QQ_TITLE, title);// 标题
        params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);// 内容地址
        params.putString(QQShare.SHARE_TO_QQ_APP_NAME, "应用");// 应用名称
        mTencent.shareToQQ(this, params, null);
        this.finish();
    }

    private void shareMusic(String t, String u){
        final String title = t;
        final String url = u;
        final Activity my = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("url", url);
                String str = get(url);

                int id = 0;
                Matcher matcher = Pattern.compile("song/(\\d+)").matcher(str);
                if(matcher.find()){
                    id = Integer.parseInt(matcher.group(1));  //Log.d("id", matcher.group(1));
                }
                if(id>0){
                    String songName = null;
                    matcher = Pattern.compile("\"songName\":\"([^\"]+)\"").matcher(str);
                    if(matcher.find()){
                        songName = matcher.group(1); //Log.d("song_name", matcher.group(1));
                    }
                    String singerName = null;
                    matcher = Pattern.compile("\"singerName\":\"([^\"]+)\"").matcher(str);
                    if(matcher.find()){
                        singerName = matcher.group(1); //Log.d("singer_name", matcher.group(1));
                    }
                    String coverUrl = null;
                    matcher = Pattern.compile("\"picUrl\":\"([^\"]+)\"").matcher(str);
                    if(matcher.find()){
                        coverUrl = matcher.group(1); //Log.d("url", matcher.group(1));
                    }
                    String musicUrl = "http://music.163.com/song/media/outer/url?id="+ id + ".mp3";

                    Tencent mTencent = Tencent.createInstance("1106259735", getApplicationContext());
                    Bundle params = new Bundle();
                    params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_AUDIO);
                    params.putString(QQShare.SHARE_TO_QQ_TITLE, songName != null ? songName : title);
                    if(singerName!=null)
                        params.putString(QQShare.SHARE_TO_QQ_SUMMARY,  singerName);
                    params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);
                    if(coverUrl != null)
                        params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, coverUrl);
                    params.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, musicUrl);
                    params.putString(QQShare.SHARE_TO_QQ_APP_NAME,  "网易云音乐");
                    mTencent.shareToQQ(my, params, null);
                    my.finish();
                }else
                    shareUrl(title, url);
            }
        }).start();
    }

    private Uri fixUri(Uri uri, String type){
        if(DocumentsContract.isDocumentUri(this, uri))
            return uri;
        File file = FileUtils.saveUriToCache(this, uri, "share", true);
        if (file.exists())
            uri = Uri.parse(buildDocumentUri(file));
        /*
        File file = new File(uri.getPath());
        if(!file.exists()){
            try {
                InputStream input = this.getContentResolver().openInputStream(uri);
                //获取自己数组
                byte[] buffer = new byte[input.available()];
                input.read(buffer);
                String path = getDiskCacheDir(this, "Bitmap").getAbsolutePath();
                File fileFolder = new File(path);
                if (fileFolder.exists())
                    fileFolder.delete();
                fileFolder.mkdirs();

                file = new File(path, uri.getLastPathSegment());
                if (!file.exists()) {
                    file.createNewFile();
                }
                OutputStream outStream = new FileOutputStream(file);
                outStream.write(buffer);
                outStream.flush();
                outStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (file.exists()) {
                uri = Uri.parse(buildDocumentUri(file));
                /*
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.ImageColumns.DATA, file.getAbsolutePath());
                values.put(MediaStore.Images.ImageColumns.TITLE, file.getName());
                values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, file.getName());
                values.put(MediaStore.Images.ImageColumns.MIME_TYPE, type);
                values.put(MediaStore.Images.ImageColumns.SIZE, file.length());
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            }
        }
        */
        return uri;
    }

    private static String buildDocumentUri(File file){
        StringBuilder sb=new StringBuilder("content://com.android.externalstorage.documents/document/primary%3A");
        sb.append(file.getPath().replace("/storage/emulated/0/", "").replace("/", "%2F"));
        Log.v("string", sb.toString());
        return sb.toString();
    }

    @Override
    public void onResume(){
        super.onResume();
        this.finish();
    }

    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }
}
