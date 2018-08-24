package net.totoraj.tjdeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private String TAG = "TJDeck";
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private FrameLayout mCustomView;
    private WebView mWebView;

    public static final int INPUT_FILE_REQUEST_CODE = 1;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigationView);
        mNavigationView.setNavigationItemSelectedListener(new TJNavigationListener());

        mWebView = (WebView) findViewById(R.id.webView);
        mCustomView = (FrameLayout) findViewById(R.id.customView);

        TextView versionText = (TextView) mNavigationView.findViewById(R.id.menuVersionText);
        if (versionText != null) versionText.setText("Version: "+getVersionName());

        /* アップデートをチェック */
        checkUpdate(false);


        mWebView.setWebViewClient(new TJClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mWebView.setWebChromeClient(new TJChromeClient());

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        if (savedInstanceState == null) {
            mWebView.loadUrl("https://tweetdeck.twitter.com");
        }
    }

    private String githubLatestUrl = "https://github.com/totoraj930/TJDeck/releases/latest";
    private String githubJsonUrl = "https://api.github.com/repos/totoraj930/TJDeck/releases/latest";

    /* アップデートがあるかチェックする */
    public void checkUpdate(boolean showLatestMessage) {
        String nowVersion = getVersionName();
        String latestVersion = getLatestVersionName();

        if (isLatestVersion(nowVersion, latestVersion)) {
            Log.d(TAG, "最新版です: "+nowVersion+":"+latestVersion);
            if (showLatestMessage)
                Toast.makeText(MainActivity.this, "最新バージョンです", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "古いです: "+nowVersion+":"+latestVersion);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("アップデートがあります")
                    .setMessage("最新版のダウンロードページを開きますか？")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubLatestUrl));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    /* バージョン文字列を比較して最新かどうがをチェックする */
    public boolean isLatestVersion(String nowVersion, String version) {
        String[] nowVersionList = nowVersion.split("\\.");
        String[] versionList = version.split("\\.");
        for (int i=0; i < versionList.length; i++) {
            if (nowVersionList.length-1 < i) return false;
            if (Double.parseDouble(nowVersionList[i]) < Double.parseDouble(versionList[i]))
                return false;
        }
        return true;
    }

    /* 現在のversionNameを取得する */
    public String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {

        }
        return null;
    }

    /* GitHubのReleasesから最新のversionNameを取得する */
    public String getLatestVersionName() {
        JSONObject jsonObject = null;
        try {
            jsonObject = new AsyncJsonTask().execute(githubJsonUrl).get();
            return jsonObject.getString("tag_name");
        } catch (Exception error) {
        }
        return null;
    }


    /* 戻るボタンの制御 */
    @Override
    public void onBackPressed() {

        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {// ドロワーが開いていたら閉じる
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        else if (mCustomView.getVisibility() != View.VISIBLE && mWebView.canGoBack()) {// WebViewで戻れたら戻る
            mWebView.goBack();
            return;
        }
        super.onBackPressed();
    }

    /* WebViewの内容を保持する */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }

    /* WebViewの内容を戻す */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWebView.restoreState(savedInstanceState);
    }

    /* Activityから値を受け取る */
    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if(requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        Uri[] results = null;

        // 正常な返り値か
        if(resultCode == Activity.RESULT_OK) {
            if(data == null) {
                // データがない場合は写真を撮った可能性があります？？？
                if(mCameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }

        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
        return;
    }

    /* JSONを取得するタスク */
    public class AsyncJsonTask extends AsyncTask<String, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(String... urls) {

            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                String resStr = inputStreamToString(connection.getInputStream());
                return new JSONObject(resStr);
            } catch (IOException error) {

            } catch (JSONException error) {

            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {

        }

        /* InputStreamをStringにする */
        private String inputStreamToString(InputStream inputStream) throws IOException {
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String tmp;
            while ((tmp = br.readLine()) != null) {
                sb.append(tmp);
            }
            br.close();
            return sb.substring(0);
        }
    }

    /* ドロワーのナビゲーションリスナ */
    public class TJNavigationListener implements NavigationView.OnNavigationItemSelectedListener {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
            int id = menuItem.getItemId();
            if (id == R.id.menuShowTJDeckOption) {// TJDeck内の設定を表示
                mWebView.evaluateJavascript("tj_deck.showOptionPanel()", null);
            }

            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
    }


    /* WebViewのクライアント */
    public class TJClient extends WebViewClient {
        private String tjDeckScript = "";
        private String tjCheckScript = "";
        private String regexDeck = "^https://tweetdeck\\.twitter\\.com.*$";
        private String regexTwitter = "^https://(.+\\.|)twitter\\.com/(login|logout|sessions|account/login_verification).*$";

        public TJClient() {
            /* jsの用意 */
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "デバッグ用");
                    tjDeckScript = loadAssets("tj-deck-debug.js");
                } else {
                    Log.d(TAG, "リリース用");
                    tjDeckScript = loadAssets("tj-deck.js");
                }
                tjCheckScript = loadAssets("test.js");
            } catch (IOException error) {
                Log.d(TAG, "onError");
            }
        }

        // ページのロード終了時
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            runTJDeckScript(view, url);
        }

        // URLが開かれたときにチェックする
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            Log.d(TAG, url);
            boolean itDeck = Pattern.matches(regexDeck, url);
            boolean itTwiLogin = Pattern.matches(regexTwitter, url);

            // DeckでもTwitterログインページでもなければブラウザに飛ばす
            if (!itDeck && !itTwiLogin) {
                view.stopLoading();
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
                return false;
            }

            return super.shouldOverrideUrlLoading(view, request);
        }

        /* アセットからjsファイルを読み込んでStringで返すやつ */
        private String loadAssets(String fileName) throws IOException {
            StringBuilder res = new StringBuilder();
            InputStream is = null;
            BufferedReader br = null;
            is = getAssets().open(fileName);
            br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            String tmp;
            while ( (tmp = br.readLine()) != null) {
                res.append(tmp);
            }

            return res.substring(0);
        }

        /* tj-deck.jsを実行する */
        private void runTJDeckScript(final WebView view, final String url) {

            boolean itDeck = Pattern.matches(regexDeck, url);
            boolean itTwiLogin = Pattern.matches(regexTwitter, url);
            Log.d(TAG, url);
            if (itDeck) {
                Log.d(TAG, "TweetDeckです");

                // TweetDeckにログインしていてなおかつtj-deckが実行されていないか確認する
                view.evaluateJavascript(tjCheckScript, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.d(TAG, value);
                        if (Boolean.parseBoolean(value)) {
                            // 実行！！
                            view.evaluateJavascript(tjDeckScript, null);
                        }
                        else if (Pattern.matches("^.*/\\?via_twitter_login=true$", url)) {
                            Log.d(TAG, "via_twitter_login=true");
                            view.evaluateJavascript(tjDeckScript, null);
                        }
                    }
                });
            }
            else if (itTwiLogin) {
                Log.d(TAG, "Twitterのログインページです");
            }
            else {
                Log.d(TAG, "それ以外です");
            }
        }

    }

    // https://github.com/googlearchive/chromium-webview-samples/blob/master/input-file-example/app/src/main/java/inputfilesample/android/chrome/google/com/inputfilesample/MainFragment.java
    public class TJChromeClient extends WebChromeClient {
        private View mVideoView = null;
        /* フルスクリーン対応 */
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            super.onShowCustomView(view, callback);
            if (mVideoView != null) {
                mCustomView.removeView(mVideoView);
            }
            mVideoView = view;
            mCustomView.addView(mVideoView);
            mWebView.setVisibility(View.INVISIBLE);
            mCustomView.setVisibility(View.VISIBLE);
            mCustomView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        public void onHideCustomView() {
            super.onHideCustomView();
            mCustomView.removeView(mVideoView);
            mWebView.setVisibility(View.VISIBLE);
            mCustomView.setVisibility(View.INVISIBLE);
            mVideoView = null;
        }

        /* ファイル選択画面の表示 */
        public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> filePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePathCallback;

            Intent takeMediaIntent = new Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
            if (takeMediaIntent.resolveActivity(getPackageManager()) != null) {
                File mediaFile = null;
                try {
                    mediaFile = createMediaFile();
                    takeMediaIntent.putExtra("PhotoPath", mCameraPhotoPath);
                } catch (IOException error) {
                    Log.d(TAG, "onError");
                }

                if (mediaFile != null) {
                    mCameraPhotoPath = "file:" + mediaFile.getAbsolutePath();
                    takeMediaIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mediaFile));
                } else {
                    takeMediaIntent = null;
                }
            }

            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("*/*");

            Intent[] intentArray;
            if (takeMediaIntent != null) {
                intentArray = new Intent[]{takeMediaIntent};
            } else {
                intentArray = new Intent[0];
            }

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Media Chooser");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
            chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            String[] mimeTypes = {"image/*", "video/*"};
            chooserIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

            return true;
        }

        /* Fileを作成する */
        private File createMediaFile() throws IOException {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = timeStamp;
            File storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
            File file = File.createTempFile(fileName, "*", storageDir);
            return file;
        }
    }
}
