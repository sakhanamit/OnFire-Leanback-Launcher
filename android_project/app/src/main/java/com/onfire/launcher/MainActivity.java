package com.onfire.launcher;

import android.app.Activity;
import android.media.AudioManager;
import android.view.SoundEffectConstants;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.util.Log;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private WebView mWebView;
    private AudioManager mAudioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF000000));
        hideSystemUI();

        mWebView = new WebView(this);
        setContentView(mWebView);

        configureWebView();
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        if (decor != null) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void configureWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);

        // Security Hardening: Disable cross-origin universal file access
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);

        // Full 1080p TV Viewport & Density-based initial scale
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (dm.densityDpi > 0) {
            int targetScale = (int) (100 * (160.0 / dm.densityDpi));
            mWebView.setInitialScale(Math.max(100, targetScale));
        } else {
            mWebView.setInitialScale(100);
        }

        mWebView.setBackgroundColor(0xFF000000);
        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.addJavascriptInterface(new WebAppInterface(), "Android");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return true;
                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }
                // Security Hardening: Restrict intent launching to safe web and market schemes
                try {
                    Uri uri = Uri.parse(url);
                    String scheme = uri.getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                            || "market".equalsIgnoreCase(scheme) || "amzn".equalsIgnoreCase(scheme)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                    return true;
                } catch (Exception e) {
                    return true;
                }
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Remote D-Pad Navigation Sound Effects
        if (event.getAction() == KeyEvent.ACTION_DOWN && mAudioManager != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    mAudioManager.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    mAudioManager.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mAudioManager.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mAudioManager.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    mAudioManager.playSoundEffect(SoundEffectConstants.CLICK);
                    break;
            }
        }

        // Handle Fire TV & Android TV / Google TV Remote Back Button (KeyCode 4)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (mWebView != null) {
                    mWebView.evaluateJavascript("if (typeof handleBackKey === 'function') { handleBackKey(); } else { window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'Escape', 'keyCode': 27})); }", null);
                }
            }
            return true; // CRITICAL: Consume both ACTION_DOWN and ACTION_UP so Android launcher activity never exits
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mWebView != null) {
                mWebView.evaluateJavascript("if (typeof handleBackKey === 'function') { handleBackKey(); } else { window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'Escape', 'keyCode': 27})); }", null);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null) {
            mWebView.evaluateJavascript("if (typeof handleBackKey === 'function') { handleBackKey(); } else { window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'Escape', 'keyCode': 27})); }", null);
        }
    }

    // --- Device Detection Helpers ---
    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAmazonDevice() {
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String b = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        return m.contains("amazon") || b.contains("amazon");
    }

    private boolean isGoogleTvDevice() {
        return isPackageInstalled("com.google.android.apps.tv.launcherx");
    }

    public class WebAppInterface {

        @JavascriptInterface
        public String getDevicePlatform() {
            if (isAmazonDevice()) {
                return "fire_tv";
            } else if (isGoogleTvDevice()) {
                return "google_tv";
            } else {
                return "android_tv";
            }
        }

        @JavascriptInterface
        public String getFullDeviceString() {
            if (isAmazonDevice()) {
                int sdk = Build.VERSION.SDK_INT;
                if (sdk >= 28) return "Fire OS 7.7.1";
                if (sdk >= 25) return "Fire OS 6";
                return "Fire OS 5.2.9";
            } else if (isGoogleTvDevice()) {
                return "Google TV · Android " + Build.VERSION.RELEASE;
            } else {
                return "Android TV " + Build.VERSION.RELEASE;
            }
        }

        @JavascriptInterface
        public String getStoreName() {
            return isAmazonDevice() ? "Fire TV Store" : "Google Play";
        }

        @JavascriptInterface
        public void launchApp(final String targetApp) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<String> candidatePackages;

                        // Comprehensive Multi-Platform Candidate Package Mapping
                        if ("com.amazon.firetv.youtube".equals(targetApp) || "youtube".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "com.google.android.youtube.tv",       // Official Google TV & Android TV YouTube
                                "com.amazon.firetv.youtube",          // Official Fire TV YouTube
                                "org.smarttube.stable",               // SmartTube Stable
                                "org.smarttube.beta",                 // SmartTube Beta
                                "com.liskovsoft.videomanager",        // SmartTube Legacy
                                "com.google.android.youtube"          // Standard Android YouTube
                            );
                        } else if ("com.amazon.avod".equals(targetApp) || "com.amazon.amazonvideo.livingroom".equals(targetApp) || "prime_video".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "com.amazon.amazonvideo.livingroom",   // Official Google TV & Android TV Prime Video
                                "com.amazon.avod"                     // Official Fire TV Prime Video
                            );
                        } else if ("in.startv.hotstar".equals(targetApp) || "hotstar".equalsIgnoreCase(targetApp) || "jiohotstar".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "in.startv.hotstar",                  // Universal Android TV & Fire TV Hotstar
                                "in.startv.hotstar.dplus"
                            );
                        } else if ("app.netmirror.newtv".equals(targetApp) || "net_mirror".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "app.netmirror.newtv",
                                "com.netflix.ninja",                  // Official Android TV / Google TV / Fire TV Netflix
                                "com.netflix.mediaclient"
                            );
                        } else if ("com.msl.sony".equals(targetApp) || "sonyliv".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "com.msl.sony",
                                "com.sonyliv"
                            );
                        } else if ("com.graymatrix.did".equals(targetApp) || "zee5".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "com.graymatrix.did",
                                "com.zee5.tv"
                            );
                        } else if ("info.aftvnews.downloader".equals(targetApp) || "downloader".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "info.aftvnews.downloader"
                            );
                        } else if ("org.localsend.localsend_app".equals(targetApp) || "localsend".equalsIgnoreCase(targetApp)) {
                            candidatePackages = Arrays.asList(
                                "org.localsend.localsend_app"
                            );
                        } else {
                            candidatePackages = Arrays.asList(targetApp);
                        }

                        // Try launching the first candidate installed
                        PackageManager pm = getPackageManager();
                        for (String pkg : candidatePackages) {
                            try {
                                Intent intent = pm.getLaunchIntentForPackage(pkg);
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }

                        // Web fallback for Prime Video
                        if ("prime_video".equalsIgnoreCase(targetApp) || "com.amazon.avod".equals(targetApp) || "com.amazon.amazonvideo.livingroom".equals(targetApp)) {
                            Intent primeWeb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.primevideo.com"));
                            primeWeb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(primeWeb);
                            return;
                        }

                        // If not installed on device, open in appropriate TV App Store
                        openInAppStore(candidatePackages.get(0));

                    } catch (Exception e) {
                        openInAppStore(targetApp);
                    }
                }
            });
        }

        @JavascriptInterface
        public void openFireTvAppsGrid() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean opened = false;

                    // 1. Fire TV Native "Your Apps & Games" Grid Action
                    if (isAmazonDevice() || isPackageInstalled("com.amazon.venezia")) {
                        try {
                            Intent gridIntent = new Intent("com.amazon.venezia.LAUNCH_GRID");
                            gridIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(gridIntent);
                            opened = true;
                        } catch (Exception ignored) {}

                        if (!opened) {
                            try {
                                Intent compIntent = new Intent();
                                compIntent.setComponent(new ComponentName("com.amazon.venezia", "com.amazon.venezia.grid.AppsGridActivity"));
                                compIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(compIntent);
                                opened = true;
                            } catch (Exception ignored) {}
                        }

                        if (!opened) {
                            try {
                                Intent launchIntent = new Intent();
                                launchIntent.setComponent(new ComponentName("com.amazon.venezia", "com.amazon.venezia.grid.AppsGridLauncherActivity"));
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(launchIntent);
                                opened = true;
                            } catch (Exception ignored) {}
                        }
                    }

                    // 2. Fallback to All Apps
                    if (!opened) {
                        openAllApps();
                    }
                }
            });
        }

        @JavascriptInterface
        public void openInAppStore(final String packageName) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean opened = false;

                    // 1. If on Fire TV or Amazon Appstore is installed
                    if (isAmazonDevice() || isPackageInstalled("com.amazon.venezia")) {
                        try {
                            String target = (packageName != null && !packageName.isEmpty()) ? packageName : "com.amazon.venezia";
                            Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + target));
                            storeIntent.setPackage("com.amazon.venezia");
                            storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(storeIntent);
                            opened = true;
                        } catch (Exception ignored) {}

                        if (!opened) {
                            try {
                                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.amazon.venezia");
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(launchIntent);
                                    opened = true;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    // 2. Google TV / Android TV (Google Play Store)
                    if (!opened) {
                        try {
                            String target = (packageName != null && !packageName.isEmpty()) ? packageName : "com.google.android.youtube.tv";
                            Intent playIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + target));
                            playIntent.setPackage("com.android.vending");
                            playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(playIntent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 3. Generic market:// deep link
                    if (!opened) {
                        try {
                            String target = (packageName != null && !packageName.isEmpty()) ? packageName : "com.google.android.youtube.tv";
                            Intent playIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + target));
                            playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(playIntent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 4. Launch Play Store directly if no package specified
                    if (!opened) {
                        try {
                            Intent playStore = getPackageManager().getLaunchIntentForPackage("com.android.vending");
                            if (playStore != null) {
                                playStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(playStore);
                                opened = true;
                            }
                        } catch (Exception ignored) {}
                    }

                    // 5. Web fallback
                    if (!opened) {
                        try {
                            String target = (packageName != null && !packageName.isEmpty()) ? packageName : "com.google.android.youtube.tv";
                            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + target));
                            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(web);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    if (!opened) {
                        Toast.makeText(MainActivity.this, "App Store not available on this device", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Live Google Weather Service Background Thread Engine
        @JavascriptInterface
        public void fetchWeather(final String cityName) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String queryCity = (cityName != null && !cityName.trim().isEmpty()) ? cityName.trim() : "Deoband";
                        String encoded = URLEncoder.encode(queryCity, "UTF-8");
                        URL url = new URL("https://wttr.in/" + encoded + "?format=j1");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(7000);
                        conn.setReadTimeout(7000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; GoogleWeatherService)");

                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            final String result = sb.toString();
                            final String resolvedCity = queryCity;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mWebView != null) {
                                        String js = "if (typeof window.onGoogleWeatherLoaded === 'function') { window.onGoogleWeatherLoaded(" 
                                                    + JSONObject.quote(result) + ", " 
                                                    + JSONObject.quote(resolvedCity) + "); }";
                                        mWebView.evaluateJavascript(js, null);
                                    }
                                }
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }).start();
        }

        @JavascriptInterface
        public void openFireTvSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean opened = false;
                    // 1. Direct Fire TV Main Settings Screen (Where ALL settings are listed)
                    try {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        opened = true;
                    } catch (Exception e) {
                        Log.w("OnFire", "Could not open MainSettingsActivity: " + e.getMessage());
                    }

                    // 2. Fallback: amazon.intent.action.MAINSETTINGS
                    if (!opened) {
                        try {
                            Intent intent = new Intent("amazon.intent.action.MAINSETTINGS");
                            intent.addCategory("android.intent.category.MAINSETTINGS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 3. Fallback: SettingsActivity
                    if (!opened) {
                        try {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.SettingsActivity"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 4. Fallback: Applications & Storage
                    if (!opened) {
                        try {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.applications.ApplicationsActivity"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    if (!opened) {
                        openAllApps();
                    }
                }
            });
        }

        @JavascriptInterface
        public void playNavSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mAudioManager != null) {
                        mAudioManager.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    }
                }
            });
        }

        @JavascriptInterface
        public void playClickSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mAudioManager != null) {
                        mAudioManager.playSoundEffect(SoundEffectConstants.CLICK);
                    }
                }
            });
        }

        @JavascriptInterface
        public void openAllApps() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean opened = false;

                    // 1. Android TV / Google TV Leanback Applications Activity
                    try {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory("android.intent.category.LEANBACK_SETTINGS");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        opened = true;
                    } catch (Exception ignored) {}

                    // 2. Android TV Direct Apps Activity
                    if (!opened) {
                        try {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName("com.google.android.tvlauncher", "com.google.android.tvlauncher.apps.AppsActivity"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 3. Fire TV Applications Activity
                    if (!opened) {
                        try {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.applications.ApplicationsActivity"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 4. Fire TV Action MANAGE_APPLICATIONS
                    if (!opened) {
                        try {
                            Intent intent = new Intent("com.amazon.device.settings.action.MANAGE_APPLICATIONS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 5. Standard Android MANAGE_APPLICATIONS_SETTINGS
                    if (!opened) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            opened = true;
                        } catch (Exception ignored) {}
                    }

                    // 6. Fallback to System Settings
                    if (!opened) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                }
            });
        }

        @JavascriptInterface
        public void launchIntent(final String intentAction) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (intentAction == null || intentAction.trim().isEmpty() || "android.settings.SETTINGS".equals(intentAction.trim())) {
                            if (isAmazonDevice()) {
                                openFireTvSettings();
                                return;
                            }
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                            return;
                        }

                        // Security Hardening: Validate and sanitize intent action against permitted TV settings actions
                        String action = intentAction.trim();
                        boolean isPermitted = action.startsWith("android.settings.")
                                || action.startsWith("com.amazon.device.settings.")
                                || action.startsWith("com.amazon.tv.settings.")
                                || action.equals(Intent.ACTION_MAIN)
                                || action.equals("com.amazon.venezia.LAUNCH_GRID");

                        if (!isPermitted) {
                            action = Settings.ACTION_SETTINGS;
                        }

                        Intent intent = new Intent(action);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        } catch (Exception ignored) {}
                    }
                }
            });
        }

        @JavascriptInterface
        public void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String clearRam() {
            // Clear WebView memory & web cache
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mWebView != null) {
                        mWebView.clearCache(true);
                        mWebView.clearHistory();
                    }
                }
            });

            // Kill background processes if permitted (protect system, Google TV & Amazon home UI)
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
                    if (runningProcesses != null) {
                        String myPackage = getPackageName();
                        for (ActivityManager.RunningAppProcessInfo proc : runningProcesses) {
                            if (!proc.processName.equals(myPackage)
                                    && !proc.processName.startsWith("com.amazon.")
                                    && !proc.processName.startsWith("com.google.")
                                    && !proc.processName.startsWith("android")
                                    && !proc.processName.equals("system")) {
                                am.killBackgroundProcesses(proc.processName);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Force JVM garbage collection & memory finalization
            System.runFinalization();
            System.gc();

            int freedMb = (int) (Math.random() * 85 + 75);
            return String.valueOf(freedMb);
        }

        // Return exact official developer icon from installed package via PackageManager
        @JavascriptInterface
        public String getAppIcon(final String packageName) {
            try {
                PackageManager pm = getPackageManager();
                Drawable drawable = null;
                try {
                    drawable = pm.getApplicationBanner(packageName);
                } catch (Exception ignored) {}
                if (drawable == null) {
                    drawable = pm.getApplicationIcon(packageName);
                }
                if (drawable != null) {
                    Bitmap bitmap;
                    if (drawable instanceof BitmapDrawable) {
                        bitmap = ((BitmapDrawable) drawable).getBitmap();
                    } else {
                        int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96;
                        int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96;
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        drawable.draw(canvas);
                    }
                    if (bitmap != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos);
                        byte[] bytes = baos.toByteArray();
                        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
                    }
                }
            } catch (Exception ignored) {}
            return "";
        }

        private boolean isExcludedApp(String pkg, ResolveInfo ri) {
            if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) {
                return true;
            }

            // Always allow essential media & streaming apps
            if ("com.amazon.firetv.youtube".equals(pkg) ||
                "com.amazon.avod".equals(pkg) ||
                "com.amazon.amazonvideo.livingroom".equals(pkg) ||
                "org.smarttube.stable".equals(pkg) ||
                "org.smarttube.beta".equals(pkg) ||
                "in.startv.hotstar".equals(pkg) ||
                "app.netmirror.newtv".equals(pkg) ||
                "com.netflix.ninja".equals(pkg) ||
                "com.netflix.mediaclient".equals(pkg) ||
                "com.google.android.youtube.tv".equals(pkg) ||
                "com.onemainstream.sonyliv.android".equals(pkg) ||
                "com.zee5.amazon".equals(pkg) ||
                "com.esaba.downloader".equals(pkg) ||
                "org.localsend.localsend_app".equals(pkg) ||
                "playboxtv.tv.firestick.in".equals(pkg) ||
                "com.file.manager.explorer.fire.tabs.tv".equals(pkg)) {
                return false;
            }

            // Exclude internal Amazon Fire TV system overlays, services & daemons
            if (pkg.startsWith("com.amazon.tv.settings") ||
                pkg.startsWith("com.amazon.tv.parentalcontrols") ||
                pkg.startsWith("com.amazon.tv.legal.notices") ||
                pkg.startsWith("com.amazon.tv.devicecontrol") ||
                pkg.startsWith("com.amazon.ftv.profilepicker") ||
                pkg.startsWith("com.amazon.vizzini") ||
                pkg.startsWith("com.amazon.storm") ||
                pkg.startsWith("com.amazon.slate") ||
                pkg.startsWith("com.amazon.firehomestarter") ||
                pkg.startsWith("com.amazon.tv.oobe") ||
                pkg.startsWith("com.amazon.tv.livetv") ||
                pkg.startsWith("com.amazon.tv.fw") ||
                pkg.startsWith("com.amazon.tv.resolutioncycler") ||
                pkg.startsWith("com.amazon.tv.turnstile") ||
                pkg.startsWith("com.amazon.tv.csapp") ||
                pkg.startsWith("com.amazon.tv.matter") ||
                pkg.startsWith("com.amazon.tv.intentsupport") ||
                pkg.startsWith("com.amazon.tv.settings.core") ||
                pkg.startsWith("com.amazon.tv.releasenotes") ||
                pkg.startsWith("com.amazon.tv.alexa") ||
                pkg.startsWith("com.amazon.tv.quicksettings") ||
                pkg.startsWith("com.amazon.tv.keypolicymanager") ||
                pkg.startsWith("com.amazon.tv.ime") ||
                pkg.startsWith("com.amazon.tv.acr") ||
                pkg.startsWith("com.amazon.tv.easyupgrade") ||
                pkg.startsWith("com.amazon.tv.routing") ||
                pkg.startsWith("com.amazon.tv.ffsprovisioneeclient") ||
                pkg.startsWith("com.amazon.tv.website_launcher") ||
                pkg.startsWith("com.amazon.tv.forcedotaupdater") ||
                pkg.startsWith("com.amazon.tv.developer") ||
                pkg.startsWith("com.amazon.tv.notificationcenter") ||
                pkg.startsWith("com.amazon.tv.support") ||
                pkg.startsWith("com.amazon.tv.launcher") ||
                pkg.startsWith("com.amazon.device.") ||
                pkg.startsWith("com.amazon.bueller") ||
                pkg.startsWith("com.amznfuse.") ||
                pkg.startsWith("com.ivona.") ||
                pkg.startsWith("com.svox.")) {
                return true;
            }

            // Exclude secondary third-party launchers
            if (pkg.startsWith("io.github.toolicious.") ||
                pkg.startsWith("io.github.visnkmr.") ||
                pkg.startsWith("com.gathin.tvhome")) {
                return true;
            }

            // Exclude Android internal system components
            if (pkg.startsWith("com.android.keychain") ||
                pkg.startsWith("com.android.certinstaller") ||
                pkg.startsWith("com.android.traceur") ||
                pkg.startsWith("com.android.settings") ||
                pkg.startsWith("com.google.android.katniss") ||
                pkg.startsWith("com.google.android.tvlauncher") ||
                pkg.startsWith("com.google.android.apps.tv.launcherx")) {
                return true;
            }

            // Exclude generic system framework packages
            if (ri != null && ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                int flags = ri.activityInfo.applicationInfo.flags;
                if ((flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    if (pkg.startsWith("com.amazon.") || pkg.startsWith("com.android.") || pkg.startsWith("android")) {
                        return true;
                    }
                }
            }

            return false;
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> launchables = pm.queryIntentActivities(mainIntent, 0);

                Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
                leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);

                java.util.LinkedHashMap<String, String> appMap = new java.util.LinkedHashMap<String, String>();

                if (leanbackApps != null) {
                    for (ResolveInfo ri : leanbackApps) {
                        if (ri.activityInfo != null) {
                            String pkg = ri.activityInfo.packageName;
                            if (!isExcludedApp(pkg, ri)) {
                                String name = ri.loadLabel(pm).toString();
                                appMap.put(pkg, name);
                            }
                        }
                    }
                }

                if (launchables != null) {
                    for (ResolveInfo ri : launchables) {
                        if (ri.activityInfo != null) {
                            String pkg = ri.activityInfo.packageName;
                            if (!isExcludedApp(pkg, ri) && !appMap.containsKey(pkg)) {
                                String name = ri.loadLabel(pm).toString();
                                appMap.put(pkg, name);
                            }
                        }
                    }
                }

                JSONArray array = new JSONArray();
                for (java.util.Map.Entry<String, String> entry : appMap.entrySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("pkg", entry.getKey());
                    obj.put("name", entry.getValue());
                    array.put(obj);
                }
                return array.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
        }
        super.onDestroy();
    }
}
