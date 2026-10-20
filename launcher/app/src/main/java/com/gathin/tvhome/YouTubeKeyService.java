package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Opens YouTube for the Chromecast remote's BUTTON_3, even outside the launcher. */
public class YouTubeKeyService extends AccessibilityService {
    private static final String TAG = "YouTubeKeyService";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube.tv";

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_3) return false;
        // Consume both halves of the key press, but launch only once on release.
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE_PACKAGE);
            if (launch == null) {
                Log.w(TAG, "YouTube is unavailable");
                return true;
            }
            try {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Log.i(TAG, "YouTube opened from BUTTON_3");
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "YouTube launch activity is unavailable", e);
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
