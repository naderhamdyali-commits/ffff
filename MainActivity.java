package edu.dept.db;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_REQ = 1;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private SharedPreferences prefs;
    private boolean dialogOpen = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);

        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " DeptDBApp");

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void changeServer() {
                runOnUiThread(() -> askServer(null));
            }
        }, "DeptApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    askServer("تعذر الاتصال بالخادم. تأكد أن الهاتف على نفس الشبكة وأن البرنامج يعمل على الكمبيوتر الخادم.");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                String name = Uri.parse(url).getQueryParameter("name");
                if (name == null || name.isEmpty()) name = URLUtil.guessFileName(url, cd, mime);
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setTitle(name);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "بدأ التنزيل: " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر بدء التنزيل", Toast.LENGTH_LONG).show();
            }
        });

        String url = prefs.getString("url", null);
        if (url == null) askServer(null);
        else web.loadUrl(url);
    }

    private void askServer(String msg) {
        if (dialogOpen) return;
        dialogOpen = true;
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        in.setHint("192.168.1.10:4455");
        in.setText(prefs.getString("url", "http://"));
        in.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
        boolean has = prefs.getString("url", null) != null;
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle("عنوان الخادم")
                .setMessage(msg != null ? msg : "اكتب عنوان الكمبيوتر الذي يحوي القاعدة (يظهر في البرنامج على الكمبيوتر ضمن «النسخ والتخزين»).")
                .setView(in)
                .setCancelable(has)
                .setPositiveButton("اتصال", (dlg, w) -> {
                    dialogOpen = false;
                    String u = in.getText().toString().trim();
                    if (u.isEmpty()) { askServer(null); return; }
                    if (!u.startsWith("http")) u = "http://" + u;
                    if (!u.replaceFirst("^https?://", "").contains(":")) u = u + ":4455";
                    prefs.edit().putString("url", u).apply();
                    web.loadUrl(u);
                })
                .setOnDismissListener(x -> dialogOpen = false);
        if (has) d.setNegativeButton("إلغاء", null);
        d.show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else {
            super.onActivityResult(req, res, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
