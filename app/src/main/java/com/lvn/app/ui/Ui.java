package com.lvn.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 화면 구성 도우미 (외부 UI 라이브러리 없이 프레임워크 뷰만 사용). */
public final class Ui {
    public static final int BG = 0xFF0B0D10;
    public static final int CARD = 0xFF151920;
    public static final int CARD2 = 0xFF1C222B;
    public static final int LINE = 0xFF2A313C;
    public static final int TEXT = 0xFFECEFF3;
    public static final int MUTED = 0xFF8A94A3;
    public static final int N_BLUE = 0xFF00B3E3;
    public static final int N_RED = 0xFFE4002B;
    public static final int GOOD = 0xFF3DDC84;
    public static final int WARN = 0xFFFFB000;

    private Ui() { }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setIncludeFontPadding(true);
        return t;
    }

    public static GradientDrawable round(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        int p = dp(c, 20);
        l.setPadding(p, p, p, p);
        l.setBackground(round(CARD, dp(c, 18), LINE, 1));
        return l;
    }

    public static TextView button(Context c, String label, boolean primary, View.OnClickListener cl) {
        TextView b = text(c, label, 18, primary ? 0xFF001018 : TEXT, true);
        b.setGravity(Gravity.CENTER);
        int ph = dp(c, 22), pv = dp(c, 16);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(dp(c, 64));
        b.setBackground(round(primary ? N_BLUE : CARD2, dp(c, 14), primary ? N_BLUE : LINE, 1));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(cl);
        return b;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w);
    }

    public static LinearLayout.LayoutParams weightV(float w) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, w);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(l, t, r, b);
        return p;
    }

    public static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return v;
    }
}
