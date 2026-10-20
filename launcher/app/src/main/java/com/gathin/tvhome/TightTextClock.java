package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.widget.TextClock;

/** A single-line clock sized to the digits, while retaining TextClock's updates and baseline. */
public final class TightTextClock extends TextClock {
    private final Rect digitBounds = new Rect();

    public TightTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (text == null) {
            super.setText(null, type);
            return;
        }
        SpannableString clock = new SpannableString(text);
        for (int i = 0; i < clock.length(); i++) {
            if (clock.charAt(i) == ':') {
                clock.setSpan(new CenteredColonSpan(), i, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        super.setText(clock, type);
    }

    /** Align punctuation to the numeral ink, independent of the font's punctuation baseline. */
    private static final class CenteredColonSpan extends ReplacementSpan {
        private final Rect numbers = new Rect();
        private final Rect colon = new Rect();
        private float baselineOffset;

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt metrics) {
            paint.getTextBounds("0123456789", 0, 10, numbers);
            paint.getTextBounds(":", 0, 1, colon);
            baselineOffset = numbers.exactCenterY() - colon.exactCenterY();
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            canvas.drawText(text, start, end, x, y + baselineOffset, paint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // Use all digits so the height stays constant as the time changes.
        String sample = "0123456789:" + getText();
        getPaint().getTextBounds(sample, 0, sample.length(), digitBounds);
        int height = digitBounds.height() + getCompoundPaddingTop() + getCompoundPaddingBottom();
        setMeasuredDimension(getMeasuredWidth(), resolveSize(height, heightMeasureSpec));
    }

    @Override
    public int getBaseline() {
        return digitBounds == null || digitBounds.isEmpty()
                ? super.getBaseline() : getCompoundPaddingTop() - digitBounds.top;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null) return;
        getPaint().setColor(getCurrentTextColor());
        int save = canvas.save();
        canvas.translate(getCompoundPaddingLeft(), getBaseline() - layout.getLineBaseline(0));
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }
}
