package com.feelingtheway.app;

import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * FEELING THE WAY — the puzzle runs as one HTML file in a WebView, and this
 * activity supplies the three things a web page cannot do on its own:
 * a real AdMob banner, interstitial and rewarded video.
 *
 * The page calls Android.setBanner / showInterstitial / showRewarded;
 * this class answers with window.onAdRewarded / onAdClosed / onAdFailed.
 */
public class MainActivity extends AppCompatActivity {

    private WebView web;
    private FrameLayout bannerHolder;
    private AdView banner;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private ConsentInformation consent;
    private boolean adsReady = false;
    private boolean adsStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        bannerHolder = findViewById(R.id.bannerHolder);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // the game saves progress in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);                        // ignore the system font-size setting

        // The page is served from a real https origin rather than file://, so the
        // player's progress in localStorage survives updates and restarts.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });
        web.setBackgroundColor(0xFFF7F2E6);
        web.addJavascriptInterface(new JsBridge(), "Android");
        web.loadUrl("https://appassets.androidplatform.net/assets/feeling-the-way.html");

        startConsentThenAds();
    }

    /* ---------------- consent (required wherever GDPR/UK GDPR applies) ----------------
       Google's User Messaging Platform decides whether a form is needed from the
       player's region, shows it, and only then may ads be requested. Outside those
       regions it resolves immediately and nothing is shown. */
    private void startConsentThenAds() {
        consent = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(this, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> initAds()),
                requestError -> initAds());
        if (consent.canRequestAds()) initAds();
    }

    private void initAds() {
        if (adsStarted) return;
        if (consent != null && !consent.canRequestAds()) return;
        adsStarted = true;
        MobileAds.initialize(this, initializationStatus -> {
            adsReady = true;
            loadInterstitial();
            loadRewarded();
        });
    }

    /* ---------------- the page's side of the bridge ---------------- */

    public class JsBridge {
        @JavascriptInterface
        public void setBanner(final boolean show) {
            runOnUiThread(() -> {
                if (show) showBanner();
                else hideBanner();
            });
        }

        @JavascriptInterface
        public void showInterstitial(final String tag) {
            runOnUiThread(() -> presentInterstitial(tag));
        }

        @JavascriptInterface
        public void showRewarded(final String tag) {
            runOnUiThread(() -> presentRewarded(tag));
        }
    }

    private void toPage(final String js) {
        runOnUiThread(() -> web.evaluateJavascript(js, (ValueCallback<String>) null));
    }

    /* ---------------- banner ---------------- */

    private void showBanner() {
        if (banner != null) return;
        banner = new AdView(this);
        banner.setAdUnitId(getString(R.string.admob_banner_id));
        banner.setAdSize(adaptiveSize());
        bannerHolder.addView(banner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        banner.loadAd(new AdRequest.Builder().build());
    }

    private void hideBanner() {
        if (banner == null) return;
        bannerHolder.removeAllViews();
        banner.destroy();
        banner = null;
    }

    private AdSize adaptiveSize() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp);
    }

    /* ---------------- interstitial: shown once, when a stage unlocks ---------------- */

    private void loadInterstitial() {
        InterstitialAd.load(this, getString(R.string.admob_interstitial_id),
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) { interstitial = ad; }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { interstitial = null; }
                });
    }

    private void presentInterstitial(final String tag) {
        if (!adsReady || interstitial == null) {
            toPage("window.onAdFailed && window.onAdFailed('" + esc(tag) + "')");
            loadInterstitial();
            return;
        }
        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                interstitial = null; loadInterstitial();
                toPage("window.onAdClosed && window.onAdClosed('" + esc(tag) + "')");
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                interstitial = null; loadInterstitial();
                toPage("window.onAdFailed && window.onAdFailed('" + esc(tag) + "')");
            }
        });
        interstitial.show(this);
    }

    /* ---------------- rewarded: the player asks for it, to earn a hint ---------------- */

    private void loadRewarded() {
        RewardedAd.load(this, getString(R.string.admob_rewarded_id),
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull RewardedAd ad) { rewarded = ad; }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { rewarded = null; }
                });
    }

    private void presentRewarded(final String tag) {
        if (!adsReady || rewarded == null) {
            toPage("window.onAdFailed && window.onAdFailed('" + esc(tag) + "')");
            loadRewarded();
            return;
        }
        rewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                rewarded = null; loadRewarded();
                toPage("window.onAdClosed && window.onAdClosed('" + esc(tag) + "')");
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                rewarded = null; loadRewarded();
                toPage("window.onAdFailed && window.onAdFailed('" + esc(tag) + "')");
            }
        });
        rewarded.show(this, reward ->
                toPage("window.onAdRewarded && window.onAdRewarded('" + esc(tag) + "')"));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /* ---------------- lifecycle ---------------- */

    @Override protected void onPause() {
        if (banner != null) banner.pause();
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        web.resumeTimers();
        web.onResume();
        if (banner != null) banner.resume();
    }

    @Override protected void onDestroy() {
        if (banner != null) banner.destroy();
        super.onDestroy();
    }

    /** Back goes through the game's own screens first, and only then leaves. */
    @Override public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){ try{ return window.onBackPressed ? !!window.onBackPressed() : false; }catch(e){ return false; } })()",
                value -> {
                    if (!"true".equals(value)) finish();
                });
    }
}
