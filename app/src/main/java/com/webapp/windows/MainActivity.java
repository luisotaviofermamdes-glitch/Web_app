package com.webapp.windows;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private VmBridge vm;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        vm = new VmBridge(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(vm, "AndroidVM");
        web.addJavascriptInterface(new BrowserBridge(), "BrowserBridge");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    private class BrowserBridge {
        @JavascriptInterface
        public void openOperaGX(String url) {
            runOnUiThread(() -> {
                String target = (url == null || url.trim().isEmpty()) ? "https://www.google.com" : url;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                intent.setPackage("com.opera.gx");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                    startActivity(fallback);
                }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (vm != null) vm.handleActivityResult(requestCode, resultCode, data);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (vm != null) vm.stopVm();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
