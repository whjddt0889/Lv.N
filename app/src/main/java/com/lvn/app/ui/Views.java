package com.lvn.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.lvn.app.model.Stat;

/** 직접 그리는 위젯: 스탯 레이더, 원형 게이지, 막대 게이지. */
public final class Views {
    private Views() { }

    /** 5각형 스탯 레이더. */
    public static final class Radar extends View {
        private int[] grades = new int[5];
        private int[] gains = new int[5];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Radar(Context c) { super(c); }

        public void set(int[] grades, int[] gains) {
            this.grades = grades.clone();
            this.gains = gains == null ? new int[5] : gains.clone();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f + Ui.dp(getContext(), 6);
            float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 46);
            Stat[] st = Stat.values();
            int n = st.length;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(), 1));
            for (int ring = 1; ring <= 4; ring++) {
                Path path = new Path();
                for (int i = 0; i <= n; i++) {
                    double a = -Math.PI / 2 + 2 * Math.PI * (i % n) / n;
                    float x = cx + (float) (Math.cos(a) * r * ring / 4f), y = cy + (float) (Math.sin(a) * r * ring / 4f);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                p.setColor(Ui.LINE);
                c.drawPath(path, p);
            }
            Path val = new Path();
            for (int i = 0; i <= n; i++) {
                int k = i % n;
                double a = -Math.PI / 2 + 2 * Math.PI * k / n;
                float rr = r * Math.max(0.04f, grades[k] / 100f);
                float x = cx + (float) (Math.cos(a) * rr), y = cy + (float) (Math.sin(a) * rr);
                if (i == 0) val.moveTo(x, y); else val.lineTo(x, y);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x4400B3E3);
            c.drawPath(val, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(), 2.5f));
            p.setColor(Ui.N_BLUE);
            c.drawPath(val, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            for (int k = 0; k < n; k++) {
                double a = -Math.PI / 2 + 2 * Math.PI * k / n;
                float x = cx + (float) (Math.cos(a) * (r + Ui.dp(getContext(), 28)));
                float y = cy + (float) (Math.sin(a) * (r + Ui.dp(getContext(), 24)));
                p.setColor(st[k].color);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(Ui.dp(getContext(), 15));
                c.drawText(st[k].label, x, y, p);
                p.setColor(Ui.TEXT);
                p.setTextSize(Ui.dp(getContext(), 17));
                String g = String.valueOf(grades[k]) + (gains[k] > 0 ? "  +" + gains[k] : "");
                c.drawText(g, x, y + Ui.dp(getContext(), 19), p);
            }
        }
    }

    /** 원형 게이지 (레벨 진행도, 컨디션, 내구도). */
    public static final class Ring extends View {
        private float value;
        private int color = Ui.N_BLUE;
        private String big = "", small = "";
        private float bigSp = 34;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Ring(Context c) { super(c); }

        public Ring set(float value, int color, String big, String small) {
            this.value = Math.max(0, Math.min(1, value));
            this.color = color;
            this.big = big;
            this.small = small;
            invalidate();
            return this;
        }

        public Ring bigSize(float sp) { bigSp = sp; return this; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            float sw = s * 0.07f;
            RectF r = new RectF((w - s) / 2 + sw, (h - s) / 2 + sw, (w + s) / 2 - sw, (h + s) / 2 - sw);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(sw);
            p.setColor(Ui.LINE);
            c.drawArc(r, 135, 270, false, p);
            p.setColor(color);
            c.drawArc(r, 135, Math.max(0.5f, 270 * value), false, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.TEXT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(s * bigSp / 200f);
            c.drawText(big, w / 2, h / 2 + s * 0.05f, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(Ui.MUTED);
            p.setTextSize(s * 0.075f);
            c.drawText(small, w / 2, h / 2 + s * 0.2f, p);
        }
    }

    /** 가로 막대 게이지. */
    public static final class Bar extends View {
        private float value;
        private int color = Ui.N_BLUE;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Bar(Context c) { super(c); }

        public Bar set(float v, int color) {
            this.value = Math.max(0, Math.min(1, v));
            this.color = color;
            invalidate();
            return this;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(0, 0, w, h);
            p.setColor(Ui.LINE);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(color);
            c.drawRoundRect(new RectF(0, 0, Math.max(h, w * value), h), h / 2, h / 2, p);
        }
    }
}
