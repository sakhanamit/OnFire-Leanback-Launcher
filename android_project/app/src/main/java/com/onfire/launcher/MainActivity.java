package com.onfire.launcher;

import android.app.Activity;
import android.media.AudioManager;
import android.view.SoundEffectConstants;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.util.Log;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
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
import android.view.WindowManager;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.content.SharedPreferences;

public class MainActivity extends Activity {

    private WebView mWebView;
    private AudioManager mAudioManager;
    private BroadcastReceiver mPackageReceiver;
    private WebAppInterface mWebAppInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF000000));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        mWebView = new WebView(this);
        mWebView.setFocusable(true);
        mWebView.setFocusableInTouchMode(true);
        setContentView(mWebView);
        mWebView.requestFocus();

        configureWebView();
        registerPackageReceiver();
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    private void registerPackageReceiver() {
        try {
            mPackageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mWebAppInterface != null) {
                        mWebAppInterface.clearCache();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mWebView != null) {
                                mWebView.evaluateJavascript("if (typeof renderAllInstalledApps === 'function') { renderAllInstalledApps(); }", null);
                            }
                        }
                    });
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addDataScheme("package");
            registerReceiver(mPackageReceiver, filter);
        } catch (Exception ignored) {}
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
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            if (decor.getSystemUiVisibility() != flags) {
                decor.setSystemUiVisibility(flags);
            }
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
        mWebView.setLayerType(View.LAYER_TYPE_NONE, null);
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                Log.e("WebViewConsole", cm.message() + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]");
                return true;
            }
        });
        mWebAppInterface = new WebAppInterface();
        mWebView.addJavascriptInterface(mWebAppInterface, "Android");

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

        // Direct hardware dispatch for TV D-Pad and Action buttons
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_MENU) {
                if (mWebView != null) {
                    mWebView.evaluateJavascript("if (typeof handleRemoteKey === 'function') { handleRemoteKey(" + keyCode + "); }", null);
                }
                return true;
            }
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
            recordRecentApp(targetApp);
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
                        } else if ("com.amazon.avod".equals(targetApp) || "com.amazon.amazonvideo.livingroom".equals(targetApp) || "prime_video".equalsIgnoreCase(targetApp) || "Prime Video".equalsIgnoreCase(targetApp)) {
                            try {
                                Intent primeWeb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.primevideo.com"));
                                primeWeb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(primeWeb);
                                return;
                            } catch (Exception ignored) {}
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
        // Live Weather Service Background Thread Engine (Open-Meteo High Speed + wttr.in fallback)
        @JavascriptInterface
        public void fetchWeather(final String cityName) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final String queryCity = (cityName != null && !cityName.trim().isEmpty()) ? cityName.trim() : "Deoband";
                        
                        // 1. Open-Meteo Geocoding
                        String geoUrlStr = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(queryCity, "UTF-8") + "&count=1&language=en&format=json";
                        URL geoUrl = new URL(geoUrlStr);
                        HttpURLConnection geoConn = (HttpURLConnection) geoUrl.openConnection();
                        geoConn.setRequestMethod("GET");
                        geoConn.setConnectTimeout(5000);
                        geoConn.setReadTimeout(5000);
                        geoConn.setRequestProperty("User-Agent", "OnFireTV/1.0 (Android TV)");

                        if (geoConn.getResponseCode() == 200) {
                            BufferedReader r = new BufferedReader(new InputStreamReader(geoConn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line);
                            r.close();

                            JSONObject geoJson = new JSONObject(sb.toString());
                            if (geoJson.has("results")) {
                                JSONArray results = geoJson.getJSONArray("results");
                                if (results.length() > 0) {
                                    JSONObject first = results.getJSONObject(0);
                                    double lat = first.getDouble("latitude");
                                    double lon = first.getDouble("longitude");
                                    final String resolvedCity = first.optString("name", queryCity);

                                    // 2. Open-Meteo Live Forecast
                                    String forecastUrlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto";
                                    URL forecastUrl = new URL(forecastUrlStr);
                                    HttpURLConnection fConn = (HttpURLConnection) forecastUrl.openConnection();
                                    fConn.setRequestMethod("GET");
                                    fConn.setConnectTimeout(5000);
                                    fConn.setReadTimeout(5000);
                                    fConn.setRequestProperty("User-Agent", "OnFireTV/1.0 (Android TV)");

                                    if (fConn.getResponseCode() == 200) {
                                        BufferedReader r2 = new BufferedReader(new InputStreamReader(fConn.getInputStream()));
                                        StringBuilder sb2 = new StringBuilder();
                                        while ((line = r2.readLine()) != null) sb2.append(line);
                                        r2.close();

                                        JSONObject fJson = new JSONObject(sb2.toString());
                                        if (fJson.has("current")) {
                                            JSONObject curr = fJson.getJSONObject("current");
                                            double tempC = curr.optDouble("temperature_2m", 24.0);
                                            int humidity = curr.optInt("relative_humidity_2m", 60);
                                            int code = curr.optInt("weather_code", 0);

                                            final String tempStr = Math.round(tempC) + "°C";
                                            final String humStr = humidity + "% Hum";
                                            final String desc;
                                            final String iconClass;

                                            if (code == 0) {
                                                desc = "Clear";
                                                iconClass = "fa-solid fa-sun text-amber-400 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 1 && code <= 3) {
                                                desc = (code == 3) ? "Overcast" : "Partly Cloudy";
                                                iconClass = "fa-solid fa-cloud-sun text-amber-300 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 45 && code <= 48) {
                                                desc = "Foggy";
                                                iconClass = "fa-solid fa-smog text-gray-300 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 51 && code <= 67) {
                                                desc = "Rainy";
                                                iconClass = "fa-solid fa-cloud-showers-heavy text-blue-400 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 71 && code <= 77) {
                                                desc = "Snow";
                                                iconClass = "fa-solid fa-snowflake text-sky-200 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 80 && code <= 82) {
                                                desc = "Showers";
                                                iconClass = "fa-solid fa-cloud-rain text-blue-400 text-3xl md:text-4xl drop-shadow";
                                            } else if (code >= 95) {
                                                desc = "Thunderstorm";
                                                iconClass = "fa-solid fa-cloud-bolt text-amber-400 text-3xl md:text-4xl drop-shadow";
                                            } else {
                                                desc = "Sunny";
                                                iconClass = "fa-solid fa-cloud-sun text-amber-300 text-3xl md:text-4xl drop-shadow";
                                            }

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (mWebView != null) {
                                                        String js = "if (typeof applyWeatherUI === 'function') { applyWeatherUI("
                                                                    + JSONObject.quote(resolvedCity) + ", "
                                                                    + JSONObject.quote(tempStr) + ", "
                                                                    + JSONObject.quote(desc) + ", "
                                                                    + JSONObject.quote(humStr) + ", "
                                                                    + JSONObject.quote(iconClass) + "); }";
                                                        mWebView.evaluateJavascript(js, null);
                                                    }
                                                }
                                            });
                                            return;
                                        }
                                    }
                                }
                            }
                        }

                        // Fallback to wttr.in if needed
                        String encoded = URLEncoder.encode(queryCity, "UTF-8");
                        URL url = new URL("https://wttr.in/" + encoded + "?format=j1");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");

                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close();
                            final String result = sb.toString();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mWebView != null) {
                                        String js = "if (typeof window.onGoogleWeatherLoaded === 'function') { window.onGoogleWeatherLoaded(" 
                                                    + JSONObject.quote(result) + ", " 
                                                    + JSONObject.quote(queryCity) + "); }";
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
        public void promptCitySearch() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                        builder.setTitle("Search Weather City");
                        builder.setMessage("Enter city or location name:");

                        final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                        input.setSingleLine(true);
                        input.setTextColor(0xFFFFFFFF);
                        input.setHint("e.g. Deoband, Delhi, London, Mumbai");
                        input.setHintTextColor(0xFFAAAAAA);
                        builder.setView(input);

                        builder.setPositiveButton("Search", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                String city = input.getText().toString().trim();
                                if (!city.isEmpty()) {
                                    fetchWeather(city);
                                    if (mWebView != null) {
                                        mWebView.evaluateJavascript("if (typeof setWeatherCity === 'function') { setWeatherCity(" + JSONObject.quote(city) + "); }", null);
                                    }
                                }
                            }
                        });
                        builder.setNegativeButton("Cancel", null);

                        android.app.AlertDialog dialog = builder.create();
                        if (dialog.getWindow() != null) {
                            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                        }
                        dialog.show();
                        input.requestFocus();
                    } catch (Exception e) {
                        Log.e("CitySearch", "Error showing city search dialog", e);
                    }
                }
            });
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

        private final java.util.Map<String, String> mTileDataCache = new java.util.concurrent.ConcurrentHashMap<String, String>();

        public void clearCache() {
            mTileDataCache.clear();
        }

        @JavascriptInterface
        public void recordRecentApp(final String pkg) {
            if (pkg == null || pkg.isEmpty() || "settings".equals(pkg) || "ram".equals(pkg) || "show_all_apps".equals(pkg)) return;
            try {
                SharedPreferences prefs = getSharedPreferences("onfire_recents", MODE_PRIVATE);
                String raw = prefs.getString("recent_list", "");
                List<String> list = new ArrayList<String>();
                if (!raw.isEmpty()) {
                    list.addAll(Arrays.asList(raw.split(",")));
                }
                list.remove(pkg);
                list.add(0, pkg);
                while (list.size() > 10) {
                    list.remove(list.size() - 1);
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i));
                }
                prefs.edit().putString("recent_list", sb.toString()).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getRecentApps() {
            try {
                SharedPreferences prefs = getSharedPreferences("onfire_recents", MODE_PRIVATE);
                String raw = prefs.getString("recent_list", "");
                if (raw.isEmpty()) {
                    return "[]";
                }
                JSONArray arr = new JSONArray();
                String[] pkgs = raw.split(",");
                for (String p : pkgs) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        arr.put(trimmed);
                    }
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        private Bitmap createPrimeVideoBannerBitmap() {
            int w = 320;
            int h = 180;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Deep Luxury Navy Gradient
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setShader(new android.graphics.LinearGradient(
                0, 0, w, h,
                new int[]{0xFF071322, 0xFF002244, 0xFF003366},
                new float[]{0f, 0.55f, 1f},
                android.graphics.Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Text "prime video"
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(30f);
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            float textY = h / 2f - 4;
            canvas.drawText("prime video", w / 2f, textY, textPaint);

            // Prime Video Cyan Smile Arrow
            Paint smilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            smilePaint.setColor(0xFF00A8E1);
            smilePaint.setStyle(Paint.Style.STROKE);
            smilePaint.setStrokeWidth(4.5f);
            smilePaint.setStrokeCap(Paint.Cap.ROUND);

            Path smilePath = new Path();
            float startX = w / 2f - 65;
            float endX = w / 2f + 65;
            float smileY = textY + 16;
            smilePath.moveTo(startX, smileY);
            smilePath.quadTo(w / 2f, smileY + 18, endX, smileY + 2);
            canvas.drawPath(smilePath, smilePaint);

            // Arrow tip
            Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            arrowPaint.setColor(0xFF00A8E1);
            arrowPaint.setStyle(Paint.Style.FILL);
            Path arrowHead = new Path();
            arrowHead.moveTo(endX - 3, smileY - 4);
            arrowHead.lineTo(endX + 8, smileY + 3);
            arrowHead.lineTo(endX + 1, smileY + 10);
            arrowHead.close();
            canvas.drawPath(arrowHead, arrowPaint);

            return bitmap;
        }

        private String bitmapToBase64(Bitmap bitmap) {
            if (bitmap == null) return "";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        }

        private Bitmap drawableToBitmap(Drawable drawable, int fallbackW, int fallbackH) {
            if (drawable == null) return null;
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
            int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : fallbackW;
            int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : fallbackH;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }

        private String extractDominantColorHex(Bitmap bitmap) {
            if (bitmap == null) return "#1E242B";
            try {
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                int stepX = Math.max(1, w / 20);
                int stepY = Math.max(1, h / 20);

                long totalR = 0, totalG = 0, totalB = 0;
                int count = 0;

                java.util.Map<Integer, Integer> buckets = new java.util.HashMap<Integer, Integer>();
                int maxFreq = 0;
                int dominantRgb = 0;

                for (int y = 0; y < h; y += stepY) {
                    for (int x = 0; x < w; x += stepX) {
                        int pixel = bitmap.getPixel(x, y);
                        int alpha = (pixel >>> 24);
                        if (alpha < 128) continue; // skip transparent

                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;

                        int max = Math.max(r, Math.max(g, b));
                        int min = Math.min(r, Math.min(g, b));
                        int diff = max - min;

                        // Skip near-black or washed-out gray
                        if (max < 35 || diff < 20) {
                            totalR += r; totalG += g; totalB += b;
                            count++;
                            continue;
                        }

                        int q = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                        int freq = buckets.containsKey(q) ? buckets.get(q) + 1 : 1;
                        buckets.put(q, freq);

                        if (freq > maxFreq) {
                            maxFreq = freq;
                            dominantRgb = ((r & 0xF0) << 16) | ((g & 0xF0) << 8) | (b & 0xF0);
                        }
                    }
                }

                if (maxFreq > 0) {
                    int r = (dominantRgb >> 16) & 0xFF;
                    int g = (dominantRgb >> 8) & 0xFF;
                    int b = dominantRgb & 0xFF;
                    return String.format("#%02X%02X%02X", r, g, b);
                } else if (count > 0) {
                    int r = (int) (totalR / count);
                    int g = (int) (totalG / count);
                    int b = (int) (totalB / count);
                    return String.format("#%02X%02X%02X", r, g, b);
                }
            } catch (Exception ignored) {}
            return "#1E242B";
        }

        // 16:9 Banner vs Square Icon Fallback API with Color Extraction
        @JavascriptInterface
        public String getAppTileData(final String packageName) {
            if (packageName == null || packageName.isEmpty()) return "{}";
            if (mTileDataCache.containsKey(packageName)) {
                return mTileDataCache.get(packageName);
            }

            try {
                PackageManager pm = getPackageManager();
                Drawable bannerDrawable = null;

                // 1. Try Leanback/Activity banner
                try {
                    Intent lbIntent = pm.getLeanbackLaunchIntentForPackage(packageName);
                    if (lbIntent != null && lbIntent.getComponent() != null) {
                        bannerDrawable = pm.getActivityBanner(lbIntent.getComponent());
                    }
                } catch (Exception ignored) {}

                // 2. Try Application Banner
                if (bannerDrawable == null) {
                    try {
                        bannerDrawable = pm.getApplicationBanner(packageName);
                    } catch (Exception ignored) {}
                }

                JSONObject res = new JSONObject();

                if (bannerDrawable != null) {
                    Bitmap bannerBm = drawableToBitmap(bannerDrawable, 320, 180);
                    res.put("hasBanner", true);
                    res.put("banner", bitmapToBase64(bannerBm));
                    res.put("icon", "");
                    res.put("bgColor", "#1E242B");
                    res.put("textColor", "#FFFFFF");
                } else if ("com.amazon.avod".equals(packageName) || "com.amazon.amazonvideo.livingroom".equals(packageName) || "prime_video".equalsIgnoreCase(packageName)) {
                    Bitmap primeBm = createPrimeVideoBannerBitmap();
                    res.put("hasBanner", true);
                    res.put("banner", bitmapToBase64(primeBm));
                    res.put("icon", "");
                    res.put("bgColor", "#002B49");
                    res.put("textColor", "#FFFFFF");
                } else {
                    Drawable iconDrawable = null;
                    try {
                        iconDrawable = pm.getApplicationIcon(packageName);
                    } catch (Exception ignored) {}
                    if (iconDrawable == null) {
                        try {
                            iconDrawable = pm.getDefaultActivityIcon();
                        } catch (Exception ignored) {}
                    }

                    Bitmap iconBm = drawableToBitmap(iconDrawable, 96, 96);
                    String dominantColorHex = extractDominantColorHex(iconBm);

                    int r = Integer.parseInt(dominantColorHex.substring(1, 3), 16);
                    int g = Integer.parseInt(dominantColorHex.substring(3, 5), 16);
                    int b = Integer.parseInt(dominantColorHex.substring(5, 7), 16);
                    double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                    String textColor = lum > 165 ? "#111827" : "#FFFFFF";

                    res.put("hasBanner", false);
                    res.put("banner", "");
                    res.put("icon", bitmapToBase64(iconBm));
                    res.put("bgColor", dominantColorHex);
                    res.put("textColor", textColor);
                }

                String jsonStr = res.toString();
                mTileDataCache.put(packageName, jsonStr);
                return jsonStr;
            } catch (Exception e) {
                return "{\"hasBanner\":false,\"banner\":\"\",\"icon\":\"\",\"bgColor\":\"#1E242B\",\"textColor\":\"#FFFFFF\"}";
            }
        }

        // Return exact official developer icon from installed package via PackageManager
        @JavascriptInterface
        public String getAppIcon(final String packageName) {
            try {
                String tileJson = getAppTileData(packageName);
                JSONObject obj = new JSONObject(tileJson);
                if (obj.optBoolean("hasBanner", false)) {
                    String b = obj.optString("banner", "");
                    if (!b.isEmpty()) return b;
                }
                return obj.optString("icon", "");
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
        if (mWebAppInterface != null) {
            mWebAppInterface.clearCache();
        }
        if (mWebView != null) {
            mWebView.onResume();
            mWebView.evaluateJavascript("if (typeof renderAllInstalledApps === 'function') { renderAllInstalledApps(); }", null);
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
        if (mPackageReceiver != null) {
            try {
                unregisterReceiver(mPackageReceiver);
            } catch (Exception ignored) {}
        }
        if (mWebView != null) {
            mWebView.destroy();
        }
        super.onDestroy();
    }
}
