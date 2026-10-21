package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Routes Chromecast Home and app keys without disabling Google TV's Cast dependencies.
 * The component name stays unchanged so installed accessibility settings survive upgrades.
 */
public class YouTubeKeyService extends AccessibilityService {
    private static final String TAG = "YouTubeKeyService";

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            // Keep CATEGORY_HOME: MainActivity uses it to reset the Dock without recreating itself.
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                openHome();
                Log.i(TAG, "Home opened from HOME key");
            }
            return true;
        }
        String app;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_3) app = "com.google.android.youtube.tv";
        else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_4) app = "com.netflix.ninja";
        else return false;
        // Consume both halves of the key press, but launch only once on release.
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(app);
            if (launch == null) {
                Log.w(TAG, "App is unavailable: " + app);
                return true;
            }
            try {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Log.i(TAG, "Opened " + app + " from key " + event.getKeyCode());
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Launch activity is unavailable: " + app, e);
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The firmware can consume the wake-up Home key before accessibility sees it.
        // Match the actual stock Home activity, not its settings, dialogs or Cast activities.
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !TextUtils.equals("com.google.android.apps.tv.launcherx", event.getPackageName())
                || !TextUtils.equals("com.google.android.apps.tv.launcherx.home.HomeActivity",
                        event.getClassName())) return;
        if (!getSystemService(PowerManager.class).isInteractive()) return;
        openHome();
        Log.i(TAG, "Home opened after stock Home window");
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override
    public void onInterrupt() {}
}
