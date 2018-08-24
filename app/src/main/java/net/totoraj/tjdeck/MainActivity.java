package net.totoraj.tjdeck;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        mCustomView = findViewById(R.id.customView);


        mWebView.setWebViewClient(new TJClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mWebView.setWebChromeClient(new TJChromeClient());
        }

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
