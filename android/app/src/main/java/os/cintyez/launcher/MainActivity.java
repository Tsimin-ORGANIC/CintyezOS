package os.cintyez.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "CintyezOS";
    private static final String START_URL =
        "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private KemonBridge bridge;
    private boolean imeVisible;
    private long lastBackAt;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int bg = getColor(R.color.bg);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        web = new WebView(this);
        configureWebView();
        root.addView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        web.addOnLayoutChangeListener((v, left, top, right, bottom,
                                          oldLeft, oldTop, oldRight, oldBottom)
            -> updateGestureExclusions());

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) top = cutout.getSafeInsetTop();
            }
            if (web.getPaddingTop() != top) web.setPadding(0, top, 0, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                imeVisible = insets.isVisible(WindowInsets.Type.ime());
            }
            return insets;
        });

        setContentView(root);
        hideSystemBars();

        boolean night = (getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        web.loadUrl(START_URL + "?systemDark=" + (night ? "1" : "0"));
        web.post(this::updateGestureExclusions);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        web.setBackgroundColor(getColor(R.color.bg));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.setHapticFeedbackEnabled(false);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        bridge = new KemonBridge(web);
        web.addJavascriptInterface(bridge, "CintyezNative");

        web.setWebViewClient(new WebViewClient() {
            private final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    new WebViewAssetLoader.AssetsPathHandler(MainActivity.this))
                .build();

            @Override
            public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("appassets.androidplatform.net".equals(url.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {
                    // 没有可处理该链接的应用时留在原地
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                hideSystemBars();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG, message.message() + " @" + message.lineNumber());
                return true;
            }
        });
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // decor 未创建时 Window.getInsetsController() 会 NPE（Android 11+ 都会）
            WindowInsetsController controller =
                getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    /* 设计稿的左右缘手势区和系统返回手势重叠，把两条边留给抽屉 */
    private void updateGestureExclusions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || web == null) return;
        int width = web.getWidth();
        int height = web.getHeight();
        if (width <= 0 || height <= 0) return;

        int edge = Math.round(30 * getResources().getDisplayMetrics().density);
        edge = Math.min(edge, width / 4);
        List<Rect> rects = new ArrayList<>(2);
        rects.add(new Rect(0, 0, edge, height));
        rects.add(new Rect(width - edge, 0, width, height));
        web.setSystemGestureExclusionRects(rects);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    public void onBackPressed() {
        if (imeVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller =
                getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.ime());
                return;
            }
        }
        if (web == null) {
            super.onBackPressed();
            return;
        }

        web.evaluateJavascript(
            "(function(){try{return !!(window.kemonAndroidBack&&window.kemonAndroidBack())}"
                + "catch(e){return false}})()",
            value -> {
                if ("true".equals(value)) {
                    lastBackAt = 0;
                    return;
                }
                long now = SystemClock.uptimeMillis();
                if (now - lastBackAt < 2200) {
                    finish();
                } else {
                    lastBackAt = now;
                    Toast.makeText(this, "再按一次返回桌面", Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) {
            bridge.shutdown();
            bridge = null;
        }
        if (web != null) {
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
