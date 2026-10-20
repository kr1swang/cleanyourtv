package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/** Shared, pre-rendered focus shadow: no live blur or elevation during navigation. */
public final class AppTileLayout extends FrameLayout {
    private static Bitmap shadow;
    private static int cachedWidth, cachedHeight, cachedPadding;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();

    public AppTileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        View card = findViewById(R.id.card);
        if (card != null && card.getVisibility() == VISIBLE && card.getWidth() > 0) {
            float scale = card.getScaleX();
            float strength = Math.max(0f, Math.min(1f, (scale - 1f) / (AppAdapter.FOCUS_SCALE - 1f)));
            if (card.isActivated()) strength = 1f;
            if (strength > 0f) {
                float density = getResources().getDisplayMetrics().density;
                int padding = Math.round(24 * density);
                int width = card.getWidth(), height = card.getHeight();
                if (shadow == null || cachedWidth != width || cachedHeight != height || cachedPadding != padding) {
                    shadow = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888);
                    Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
                    blur.setColor(0x99000000);
                    blur.setShadowLayer(10 * density, 0, 6 * density, 0xBB000000);
                    float radius = AppAdapter.cornerRadius(width, density);
                    new Canvas(shadow).drawRoundRect(padding, padding, padding + width, padding + height, radius, radius, blur);
                    cachedWidth = width;
                    cachedHeight = height;
                    cachedPadding = padding;
                }
                float cx = card.getLeft() + width / 2f;
                float cy = card.getTop() + height / 2f;
                float halfWidth = (width / 2f + padding) * scale;
                float halfHeight = (height / 2f + padding) * scale;
                bounds.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
                paint.setAlpha(Math.round(255 * strength));
                canvas.drawBitmap(shadow, null, bounds, paint);
            }
        }
        super.dispatchDraw(canvas);
    }
}
