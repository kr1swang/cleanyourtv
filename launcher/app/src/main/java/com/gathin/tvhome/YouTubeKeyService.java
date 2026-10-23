package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Routes Chromecast Home and app keys without disabling Google TV's Cast dependencies.
 * The component name stays unchanged so installed accessibility settings survive upgrades.
 */
public class YouTubeKeyService extends AccessibilityService {
    private static final String TAG = "YouTubeKeyService";
    private boolean repairRecord;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Share upstream's opt-in repair registry, preserving this installed component's identity.
        repairRecord = Config.load(this).restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        // Stock Home may already be visible before this service can observe its window event.
        // Handle startup once per boot; package upgrades and service reconnects must not steal focus.
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return;
        SharedPreferences prefs = getSharedPreferences("remote_keys", MODE_PRIVATE);
        if (prefs.getInt("home_boot", -1) == boot) return;
        prefs.edit().putInt("home_boot", boot).apply();
        if (!getSystemService(PowerManager.class).isInteractive()) return;
        openHome();
        Log.i(TAG, "Home opened on service connection for boot " + boot);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            // Avoid a pause/resume cycle when Home is already visible, so its transitions keep running.
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (!MainActivity.returnToVisibleHome()) openHome();
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
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {}
}
