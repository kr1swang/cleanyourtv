package com.gathin.tvhome;

import android.os.PowerManager;
import android.view.KeyEvent;

/** Keeps the installed service identity while sharing upstream's boot, redirect and app-key handling.
 * Only Home is consumed immediately, so a visible Home can close its panels without a pause/resume.
 */
public class YouTubeKeyService extends HomeKeyService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return super.onKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openHome();
        return true;
    }

    @Override
    protected void openHome() {
        if (!getSystemService(PowerManager.class).isInteractive()) return;
        if (!MainActivity.returnToVisibleHome()) super.openHome();
    }
}
