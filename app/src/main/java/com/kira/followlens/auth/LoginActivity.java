package com.kira.followlens.auth;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kira.followlens.R;

/**
 * Logs in by letting the user sign in to Instagram normally, then reading the
 * session cookie the WebView received. This replaces copying a cookie out of
 * desktop DevTools by hand.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String LOGIN_URL = "https://www.instagram.com/accounts/login/";

    private WebView webView;
    private SessionStore sessionStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        sessionStore = new SessionStore(this);

        CookieManager.getInstance().setAcceptCookie(true);

        webView = findViewById(R.id.login_web_view);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                captureSessionIfPresent();
            }
        });
        webView.loadUrl(LOGIN_URL);
    }

    /**
     * Instagram sets the session cookie as soon as login succeeds, so every
     * page load is a chance to find it.
     */
    private void captureSessionIfPresent() {
        String header = CookieManager.getInstance().getCookie("https://www.instagram.com");
        String sessionId = CookieParser.sessionIdFrom(header);
        if (sessionId == null) {
            return;
        }
        try {
            sessionStore.save(sessionId);
        } catch (IllegalArgumentException e) {
            return;
        }
        Toast.makeText(this, R.string.login_captured, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
