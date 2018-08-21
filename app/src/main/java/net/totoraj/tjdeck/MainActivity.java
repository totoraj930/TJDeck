package net.totoraj.tjdeck;

import android.app.Activity;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private View mContent;
    private WebView mWebView = null;
    private String tjDeckScript = "";
    private String tjCheckScript = "";

    public static final int INPUT_FILE_REQUEST_CODE = 1;
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FORM_NOTIFICATION";
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);



        /* jsの用意 */
        try {
            tjDeckScript = loadAssets("tj-deck.js");
            tjCheckScript = loadAssets("test.js");
        } catch (Exception error) {
            System.out.println("onError");
        }

        mWebView = (WebView) findViewById(R.id.webView1);

        mWebView.setWebViewClient(new TJClient());
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

    /* アセットからjsファイルを読み込んでStringで返すやつ */
    private String loadAssets(String fileName) throws Exception {
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


    /* 戻るボタンでブラウザバックするようにする */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* WebViewの内容を保持する */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }

    /* WebViewの内容を保持する */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWebView.restoreState(savedInstanceState);
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if(requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        Uri[] results = null;

        // Check that the response is a good one
        if(resultCode == Activity.RESULT_OK) {
            if(data == null) {
                // If there is not data, then we may have taken a photo
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


    // https://github.com/googlearchive/chromium-webview-samples/blob/master/input-file-example/app/src/main/java/inputfilesample/android/chrome/google/com/inputfilesample/MainFragment.java
    public class TJChromeClient extends WebChromeClient {
        public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> filePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePathCallback;

            Intent takePicutureIntent = new Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
            if (takePicutureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                    takePicutureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                } catch (IOException error) {

                }

                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                    takePicutureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                } else {
                    takePicutureIntent = null;
                }
            }

            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("*/*");

            Intent[] intentArray;
            if (takePicutureIntent != null) {
                intentArray = new Intent[]{takePicutureIntent};
            } else {
                intentArray = new Intent[0];
            }

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Media Chooser");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
            chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            String[] mimetypes = {"image/*", "video/*"};
            chooserIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);

            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

            return true;
        }

        private File createImageFile() throws IOException {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = timeStamp;
            File storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
            File file = File.createTempFile(fileName, "*", storageDir);
            return file;
        }
    }


    /* WebViewのクライアント */
    public class TJClient extends WebViewClient {
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
            boolean itDeck = Pattern.matches("^https://tweetdeck\\.twitter\\.com.*$", url);
            boolean itTwiLogin = Pattern.matches("^https://(.+\\.|)twitter\\.com/(login|logout).*$", url);

            // DeckでもTwitterログインページでもなければブラウザに飛ばす
            if (!itDeck && !itTwiLogin) {
                view.stopLoading();
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
                return false;
            }

            return super.shouldOverrideUrlLoading(view, request);
        }


//        @Override
//        public void onLoadResource(WebView view, String url) {
//            System.out.println("onLoadResource: " + url);
//        }
//        @Override
//        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
//            handler.proceed();
//        }

        /* tj-deck.jsを実行する */
        private void runTJDeckScript(final WebView view, final String url) {

            boolean itDeck = Pattern.matches("^https://tweetdeck\\.twitter\\.com.*$", url);
            boolean itTwiLogin = Pattern.matches("^https://(.+\\.|)twitter\\.com/(login|logout).*$", url);
            System.out.println(url);
            if (itDeck) {
                System.out.println("TweetDeckです");

                // TweetDeckにログインしていてなおかつtj-deckが実行されていないか確認する
                view.evaluateJavascript(tjCheckScript, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        System.out.println(value);
                        if (Boolean.parseBoolean(value)) {
                            // 実行！！
                            view.evaluateJavascript(tjDeckScript, null);
                        }
                        else if (Pattern.matches("^.*/\\?via_twitter_login=true$", url)) {
                            System.out.println("via_twitter_login=true");
                            view.evaluateJavascript(tjDeckScript, null);
                        }
                    }
                });
            }
            else if (itTwiLogin) {
                System.out.println("Twitterのログインページです");
            }
            else {
                System.out.println("それ以外です");
            }
        }

    }
}
