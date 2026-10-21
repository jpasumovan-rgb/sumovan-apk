package ph.sumovan.mmdocs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 11;
    private static final String HOOK_JS =
        "(function(){if(window.__sumoHook)return;window.__sumoHook=1;"
      + "function done(n,b){var fr=new FileReader();fr.onload=function(){var s=String(fr.result);SumoApp.saveFile(n,b.type||'application/pdf',s.substring(s.indexOf(',')+1))};fr.readAsDataURL(b)}"
      + "function xhr(h,n){var x=new XMLHttpRequest();x.open('GET',h);x.responseType='blob';x.onload=function(){done(n,x.response)};"
      + "x.onerror=function(){SumoApp.toast('Could not save the PDF. Please try again.')};x.send()}"
      + "function send(a){try{var n=a.getAttribute('download')||'file.pdf',h=a.href;"
      + "fetch(h).then(function(r){return r.blob()}).then(function(b){done(n,b)}).catch(function(){xhr(h,n)})}catch(e){try{xhr(a.href,a.getAttribute('download')||'file.pdf')}catch(e2){}}}"
      + "var oc=HTMLAnchorElement.prototype.click;"
      + "HTMLAnchorElement.prototype.click=function(){if(this.hasAttribute('download')&&/^(blob|data):/.test(this.href)){send(this);return}return oc.apply(this,arguments)};"
      + "document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[download]');"
      + "if(a&&/^(blob|data):/.test(a.href)){e.preventDefault();send(a)}},true);"
      + "window.print=function(){SumoApp.toast('To print: tap SAVE PDF, then choose OPEN / PRINT.')};"
      + "window.close=function(){SumoApp.exit()};})();";

    private WebView web;
    private ProgressBar bar;
    private View offline;
    private boolean failed;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private File camFile;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B0B0C"));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C8102E")));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, 8);
        bp.gravity = Gravity.TOP;
        root.addView(bar, bp);

        offline = buildOffline();
        offline.setVisibility(View.GONE);
        root.addView(offline, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBuiltInZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "SumoApp");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = new HashSet<>();
            origins.add("https://*.googleusercontent.com");
            origins.add("https://script.google.com");
            WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, origins);
        }

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url != null && url.startsWith("http")) openExternal(Uri.parse(url));
        });

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost();
                if (sch.equals("https") && (host.endsWith("google.com") || host.endsWith("googleusercontent.com"))) return false;
                if (sch.equals("blob") || sch.equals("data") || sch.equals("about")) return false;
                openExternal(u);
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                failed = false;
                bar.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v, String url) {
                bar.setVisibility(View.GONE);
                if (!failed) offline.setVisibility(View.GONE);
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    v.evaluateJavascript(HOOK_JS, null);
                }
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { failed = true; offline.setVisibility(View.VISIBLE); }
            }
            @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { bar.setProgress(p); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                return chooseFile(cb, p);
            }
        });

        if (b != null) web.restoreState(b); else web.loadUrl(BuildConfig.APP_URL);
    }

    // ---------- photo / file picker (camera or gallery) ----------
    private boolean chooseFile(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams p) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        String type = "*/*";
        boolean imageOnly = false;
        String[] acc = p.getAcceptTypes();
        if (acc != null && acc.length > 0 && acc[0] != null && !acc[0].isEmpty()) {
            type = acc[0];
            imageOnly = type.startsWith("image");
        }
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType(type);
        Intent chooser = Intent.createChooser(pick, "Select photo");
        camUri = null; camFile = null;
        if (imageOnly || type.equals("*/*")) {
            try {
                File dir = new File(getCacheDir(), "cam");
                if (!dir.exists()) dir.mkdirs();
                camFile = File.createTempFile("cam_", ".jpg", dir);
                camUri = FileProvider.getUriForFile(this, getPackageName() + ".files", camFile);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
            } catch (IOException e) { camUri = null; camFile = null; }
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            fileCb.onReceiveValue(null); fileCb = null;
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_FILE) { super.onActivityResult(req, res, data); return; }
        if (fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            out = WebChromeClient.FileChooserParams.parseResult(res, data);
            if (out == null && camFile != null && camFile.length() > 0) out = new Uri[]{camUri};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    // ---------- JS bridge ----------
    private class Bridge {
        @JavascriptInterface public void toast(String m) {
            ui.post(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
        }
        @JavascriptInterface public void exit() {
            ui.postDelayed(() -> moveTaskToBack(true), 1500);
        }
        @JavascriptInterface public void saveFile(String name, String mime, String b64) {
            new Thread(() -> doSave(name, mime, b64)).start();
        }
    }

    private void doSave(String name, String mime, String b64) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SUMOVAN");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("insert failed");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            ui.post(() -> showSaved(uri, mime, safe));
        } catch (Exception e) {
            ui.post(() -> Toast.makeText(this, "Could not save the file: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void showSaved(Uri uri, String mime, String name) {
        new AlertDialog.Builder(this)
            .setTitle("PDF SAVED")
            .setMessage(name + "\n\nSaved in Downloads > SUMOVAN.\nShare it to Messenger, Viber or any app?")
            .setPositiveButton("SHARE", (d, w) -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, name);
                send.setClipData(android.content.ClipData.newRawUri(name, uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(Intent.createChooser(send, "Share PDF")); }
                catch (Exception e) { Toast.makeText(this, "No app to share with", Toast.LENGTH_LONG).show(); }
            })
            .setNeutralButton("OPEN / PRINT", (d, w) -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, mime);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) { Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_LONG).show(); }
            })
            .setNegativeButton("DONE", null)
            .show();
    }

    // ---------- helpers ----------
    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
    }

    private View buildOffline() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#0B0B0C"));
        l.setPadding(60, 60, 60, 60);
        TextView t = new TextView(this);
        t.setText("NO CONNECTION");
        t.setTextColor(Color.WHITE); t.setTextSize(24); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        TextView m = new TextView(this);
        m.setText("Check your mobile data or Wi-Fi, then tap Try again.\nYour saved inputs are still on this phone.");
        m.setTextColor(Color.LTGRAY); m.setTextSize(16); m.setGravity(Gravity.CENTER);
        m.setPadding(0, 30, 0, 50);
        Button r = new Button(this);
        r.setText("TRY AGAIN");
        r.setBackgroundColor(Color.parseColor("#C8102E")); r.setTextColor(Color.WHITE);
        r.setOnClickListener(x -> { offline.setVisibility(View.GONE); web.reload(); });
        l.addView(t, new LinearLayout.LayoutParams(-2, -2));
        l.addView(m, new LinearLayout.LayoutParams(-2, -2));
        l.addView(r, new LinearLayout.LayoutParams(-2, -2));
        return l;
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Leave the app?")
            .setMessage("Tap the EXIT button inside the app first, so your inputs are saved.")
            .setNegativeButton("Stay", null)
            .setPositiveButton("Leave", (d, w) -> moveTaskToBack(true))
            .show();
    }

    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); web.saveState(b); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() {
        ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
