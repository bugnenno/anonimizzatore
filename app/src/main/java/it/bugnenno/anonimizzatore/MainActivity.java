package it.bugnenno.anonimizzatore;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final String START_URL =
            "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> pendingChooser;
    private Uri pendingIncoming;   // file arrivato da "Condividi" / "Apri con"
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                // eventuali link esterni si aprono nel browser
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
                deliverIncoming();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (pendingChooser != null) pendingChooser.onReceiveValue(null);
                pendingChooser = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/*"});
                try {
                    startActivityForResult(i, REQ_PICK);
                } catch (Exception e) {
                    pendingChooser = null;
                    toast("Impossibile aprire la scelta dei file");
                    return false;
                }
                return true;
            }
        });

        handleIntent(getIntent());
        web.loadUrl(START_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        deliverIncoming();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri u = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            u = intent.getData();
        }
        if (u != null) pendingIncoming = u;
    }

    /** Passa alla pagina il file ricevuto da un'altra app. */
    private void deliverIncoming() {
        if (!pageReady || pendingIncoming == null) return;
        Uri u = pendingIncoming;
        pendingIncoming = null;
        try {
            byte[] bytes = readAll(u);
            String js = "window.loadFromAndroid && window.loadFromAndroid("
                    + JSONObject.quote(displayName(u)) + ","
                    + JSONObject.quote(Base64.encodeToString(bytes, Base64.NO_WRAP)) + ")";
            web.evaluateJavascript(js, null);
        } catch (Exception e) {
            toast("Non riesco a leggere il file condiviso");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && pendingChooser != null) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            pendingChooser.onReceiveValue(result);
            pendingChooser = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ---------- utilità ----------

    private byte[] readAll(Uri u) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String p = u.getLastPathSegment();
        return p != null ? p : "documento";
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    /** Funzioni chiamabili dalla pagina come window.Android.xxx(...) */
    private class Bridge {

        /** Salva nella cartella Download. Restituisce "" se ok, altrimenti il messaggio d'errore. */
        @JavascriptInterface
        public String saveFile(String name, String mime, String b64) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Anonimizzatore");
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "impossibile creare il file";
                try (OutputStream os = cr.openOutputStream(uri)) {
                    os.write(bytes);
                }
                toast("Salvato in Download/Anonimizzatore/" + name);
                return "";
            } catch (Exception e) {
                return String.valueOf(e.getMessage());
            }
        }

        /** Apre il menu "Condividi" di Android con il file (es. per mandarlo all'app Claude). */
        @JavascriptInterface
        public String shareFile(String name, String mime, String b64) {
            try {
                File dir = new File(getCacheDir(), "condivisi");
                dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    fo.write(Base64.decode(b64, Base64.DEFAULT));
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this,
                        getPackageName() + ".files", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Condividi con…")));
                return "";
            } catch (Exception e) {
                return String.valueOf(e.getMessage());
            }
        }
    }
}
