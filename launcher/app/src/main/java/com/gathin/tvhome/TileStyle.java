package com.gathin.tvhome;

/** Only the Apple geometry differs; row fitting and navigation remain shared with upstream. */
enum TileStyle {
    DEFAULT, APPLE;

    static TileStyle from(String layout) {
        return "apple".equals(layout) ? APPLE : DEFAULT;
    }

    float designHeight() {
        return this == APPLE ? 180f * 74f / 124f : AppAdapter.DESIGN_H_DP;
    }

    int width(int inner, int columns, float density) {
        if (this == DEFAULT) return AppAdapter.tileWidthPx(inner, columns, density);
        int design = Math.round(AppAdapter.DESIGN_W_DP * density);
        if (inner <= 0 || columns <= 0) return design;
        return Math.max(Math.round(80 * density), Math.min(design, Math.round((inner / columns) * 0.83f)));
    }

    int height(int width) {
        return Math.round(width * designHeight() / AppAdapter.DESIGN_W_DP);
    }

    float radius(int width, float density) {
        return this == APPLE ? 15f * density * Math.min(1f, width / (124f * density))
                : AppAdapter.cornerRadius(width, density);
    }

    float focusScale(boolean advancedEffects) {
        return this == APPLE ? 1.20f : advancedEffects ? 1.12f : AppAdapter.FOCUS_SCALE;
    }

    int dockInset(float density) {
        return this == APPLE ? Math.round(16 * density) : 0;
    }
}
