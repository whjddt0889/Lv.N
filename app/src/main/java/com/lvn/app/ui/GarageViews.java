package com.lvn.app.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * 차고 화면 전용 위젯. 차고 화면은 1600x900 설계 좌표로 그린 뒤 화면 크기에 맞게 통째로 확대·축소한다.
 * 그래서 여기 위젯들은 dp 가 아니라 설계 좌표(px) 기준으로 그린다.
 */
public final class GarageViews {
    private GarageViews() { }

    /** 원근 차고 배경: 벽, 천장 조명, 원근 바닥, 스포트라이트, 차 그림자. */
    public static final class Backdrop extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Backdrop(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // 조명 번짐(BlurMaskFilter)
        }

        @Override
        protected void onDraw(Canvas c) {
            float k = getWidth() / 1600f;
            c.save();
            c.scale(k, k);
            p.setStyle(Paint.Style.FILL);
            // 벽
            p.setShader(new LinearGradient(0, 0, 0, 560, 0xFF0C1219, 0xFF18222E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 1600, 560, p);
            // 바닥
            p.setShader(new LinearGradient(0, 560, 0, 900, 0xFF1A2430, 0xFF090D12, Shader.TileMode.CLAMP));
            c.drawRect(0, 560, 1600, 900, p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(0xFF1C2734);
            for (int x = 200; x < 1600; x += 200) c.drawLine(x, 0, x, 560, p);
            p.setColor(0xFF1E2A38);
            c.drawLine(0, 470, 1600, 470, p);
            p.setColor(0xCC1F2B39);
            int[] ends = {-400, 0, 400, 800, 1200, 1600, 2000};
            for (int x : ends) c.drawLine(800, 560, x, 900, p);
            p.setColor(0xFF1A2531);
            int[] rows = {600, 660, 750, 870};
            for (int y : rows) c.drawLine(0, y, 1600, y, p);

            p.setStyle(Paint.Style.FILL);
            // 천장 조명 빛줄기
            Path cone = new Path();
            cone.moveTo(620, 50); cone.lineTo(980, 50); cone.lineTo(1180, 620); cone.lineTo(420, 620); cone.close();
            p.setColor(0x0D5AA9E6);
            c.drawPath(cone, p);
            // 스포트라이트
            ellipseGlow(c, 800, 620, 420, 90, 0x733C6E9E);
            // 천장 조명
            p.setColor(0xCC00A3E0);
            p.setMaskFilter(new BlurMaskFilter(14, BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(new RectF(460, 40, 1140, 50), 5, 5, p);
            p.setMaskFilter(null);
            p.setColor(0xFFBFEAFF);
            c.drawRoundRect(new RectF(460, 42, 1140, 48), 3, 3, p);
            // 차 그림자
            ellipseGlow(c, 800, 636, 330, 40, 0xB3000000);
            c.restore();
        }

        private void ellipseGlow(Canvas c, float cx, float cy, float rx, float ry, int color) {
            c.save();
            c.translate(cx, cy);
            c.scale(1f, ry / rx);
            p.setShader(new RadialGradient(0, 0, rx, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(0, 0, rx, p);
            p.setShader(null);
            c.restore();
        }
    }

    /** 스탯 오각형 (설계 크기 344x345). */
    public static final class Pentagon extends View {
        private static final int[] COLORS = {0xFF4FC3F7, 0xFFFF6B74, 0xFFFFC24D, 0xFF5FE3A1, 0xFFC3A6FF};
        private static final float[][] LABEL_AT = {{172, 22}, {306, 128}, {262, 304}, {82, 304}, {36, 128}};
        private final String[] names = {"테크닉", "스피드", "담대함", "매너", "체력"};
        private int[] grades = new int[5];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Pentagon(Context c) { super(c); }

        public void set(int[] grades) { this.grades = grades.clone(); invalidate(); }

        private static float px(int i, float r) { return 172 + (float) Math.cos(-Math.PI / 2 + 2 * Math.PI * i / 5) * r; }
        private static float py(int i, float r) { return 178 + (float) Math.sin(-Math.PI / 2 + 2 * Math.PI * i / 5) * r; }

        @Override
        protected void onDraw(Canvas c) {
            float k = getWidth() / 344f;
            c.save();
            c.scale(k, k);
            final float R = 118;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(0xFF2A3646);
            float[] rings = {1f, 0.75f, 0.5f, 0.25f};
            for (float f : rings) c.drawPath(poly(R, f, null), p);
            p.setStrokeWidth(1);
            p.setColor(0xFF223040);
            for (int i = 0; i < 5; i++) c.drawLine(172, 178, px(i, R), py(i, R), p);

            Path v = poly(R, 1f, grades);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x4700A3E0);
            c.drawPath(v, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xFF00A3E0);
            c.drawPath(v, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFEEF2F6);
            for (int i = 0; i < 5; i++) {
                float r = R * Math.max(0.04f, grades[i] / 100f);
                c.drawCircle(px(i, r), py(i, r), 5, p);
            }

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = 0; i < 5; i++) {
                p.setColor(COLORS[i]);
                p.setTextSize(17);
                c.drawText(names[i], LABEL_AT[i][0], LABEL_AT[i][1], p);
                p.setColor(0xFFEEF2F6);
                p.setTextSize(20);
                c.drawText(String.valueOf(grades[i]), LABEL_AT[i][0], LABEL_AT[i][1] + 24, p);
            }
            c.restore();
        }

        private Path poly(float R, float f, int[] g) {
            Path path = new Path();
            for (int i = 0; i < 5; i++) {
                float r = g == null ? R * f : R * Math.max(0.04f, g[i] / 100f);
                if (i == 0) path.moveTo(px(i, r), py(i, r)); else path.lineTo(px(i, r), py(i, r));
            }
            path.close();
            return path;
        }
    }

    /** 둥근 막대 게이지 (바탕색 지정 가능). */
    public static final class Pill extends View {
        private float value;
        private int track, fill;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Pill(Context c, int track, int fill, float value) {
            super(c);
            this.track = track;
            this.fill = fill;
            this.value = Math.max(0, Math.min(1, value));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(track);
            c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            if (value <= 0) return;
            p.setColor(fill);
            c.drawRoundRect(new RectF(0, 0, Math.max(h, w * value), h), h / 2, h / 2, p);
        }
    }
}
