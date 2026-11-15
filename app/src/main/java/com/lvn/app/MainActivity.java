package com.lvn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lvn.app.engine.DriveEvent;
import com.lvn.app.engine.DriveSample;
import com.lvn.app.engine.JudgeEngine;
import com.lvn.app.model.CarProfile;
import com.lvn.app.model.CharacterClass;
import com.lvn.app.model.Profile;
import com.lvn.app.model.Stat;
import com.lvn.app.source.LiveCarSource;
import com.lvn.app.source.Scenario;
import com.lvn.app.ui.GarageViews;
import com.lvn.app.ui.Ui;
import com.lvn.app.ui.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.lvn.app.ui.Ui.dp;

public class MainActivity extends Activity {
    private static final long TICK_MS = 50;

    private Profile profile;
    private Feedback fx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;

    // 주행 세션
    private JudgeEngine engine;
    private Scenario playing;
    private int playIdx;
    private double simCursor;
    private float baseMul = 1f;
    private boolean live, driving;
    private LiveCarSource liveSrc;
    private DriveSample lastSample;
    private int levelAtStart;
    private String charAtStart;
    private int tripXp;

    // 주행 화면 뷰
    private Views.Ring dRing;
    private TextView dTripXp, dClock, dLabel, dSpeed, dGear, dRpm, dFlags, dCondText, dMulBtn, dLiveStatus;
    private Views.Bar dCondBar;
    private LinearLayout dLog, dSidePanel, dLiveHidden;
    private final List<CharSequence> logLines = new ArrayList<>();

    // 영상용 자동 데모
    private boolean autoDemo;
    private int autoStep;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0E13);
        root.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> fitGarage());
        setContentView(root);
        profile = Profile.load(this);
        fx = new Feedback(this);
        fx.voiceOn = profile.voiceOn;
        try {
            requestPermissions(new String[]{"android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS"}, 1);
        } catch (Throwable ignored) { }
        if (!profile.created) showOnboarding(); else showGarage();
    }

    @Override
    protected void onDestroy() {
        stopDriveLoop();
        fx.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (driving) {
            // 중간에 멈춘 주행은 반영하지 않는다
            autoDemo = false;
            h.removeCallbacksAndMessages(null);
            stopDriveLoop();
            profile = Profile.load(this);
            fx.voiceOn = profile.voiceOn;
            showGarage();
            return;
        }
        if (autoDemo) { autoDemo = false; h.removeCallbacksAndMessages(null); }
        showGarage();
    }

    // =====================================================================
    // 공통 프레임
    // =====================================================================
    private LinearLayout frame(String title, String sub) {
        root.removeAllViews();
        LinearLayout page = Ui.col(this);
        int p = dp(this, 28);
        page.setPadding(p, dp(this, 20), p, dp(this, 22));
        LinearLayout head = Ui.row(this);
        TextView logo = Ui.text(this, "", 30, Ui.TEXT, true);
        SpannableString ss = new SpannableString("Lv.N");
        ss.setSpan(new ForegroundColorSpan(Ui.N_BLUE), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        logo.setText(ss);
        head.addView(logo);
        LinearLayout tcol = Ui.col(this);
        tcol.setPadding(dp(this, 18), 0, 0, 0);
        tcol.addView(Ui.text(this, title, 20, Ui.TEXT, true));
        if (sub != null) tcol.addView(Ui.text(this, sub, 14, Ui.MUTED, false));
        head.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(head);
        page.addView(Ui.space(this, 1, 16));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    private void addBack(LinearLayout head) {
        head.addView(Ui.button(this, "← 차고", false, v -> onBackPressed()));
    }

    private LinearLayout headOf(LinearLayout page) { return (LinearLayout) page.getChildAt(0); }

    private TextView small(String s) { return Ui.text(this, s, 14, Ui.MUTED, false); }

    // =====================================================================
    // 첫 실행
    // =====================================================================
    private void showOnboarding() {
        LinearLayout page = frame("처음 오셨네요", "운전할수록 내 N 캐릭터가 성장합니다");
        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));

        LinearLayout left = Ui.card(this);
        left.addView(Ui.text(this, "1. 내 차를 골라 주세요", 20, Ui.TEXT, true));
        left.addView(small("기어비·타이어 둘레로 엔진 회전수를 추정해 변속을 판정합니다"));
        left.addView(Ui.space(this, 1, 12));
        final TextView[] chosen = new TextView[1];
        for (CarProfile cp : CarProfile.ALL) {
            final CarProfile c = cp;
            TextView bt = Ui.button(this, cp.label(), cp.id.equals(profile.carId), null);
            bt.setOnClickListener(v -> {
                profile.carId = c.id;
                showOnboarding();
            });
            left.addView(bt, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, dp(this, 10)));
            if (cp.id.equals(profile.carId)) chosen[0] = bt;
        }
        body.addView(left, Ui.margins(Ui.weight(1), 0, 0, dp(this, 16), 0));

        LinearLayout right = Ui.card(this);
        right.addView(Ui.text(this, "2. 시작 방법", 20, Ui.TEXT, true));
        right.addView(Ui.space(this, 1, 8));
        right.addView(Ui.text(this, "· 주행 중에는 화면을 보지 않아도 됩니다. 판정은 짧은 효과음으로만 알려드려요.\n"
                + "· 결과·캐릭터·보상은 정차했을 때만 열립니다.\n"
                + "· 주행 데이터는 차 안에서만 처리하고, 위치는 100m 격자로만 기록합니다.\n"
                + "· 가솔린 N / N 라인 전용입니다 (전기차·하이브리드 제외).", 16, Ui.TEXT, false));
        right.addView(Ui.space(this, 1, 18));
        right.addView(Ui.button(this, "새 프로필로 시작 (Lv.1)", true, v -> {
            profile.created = true;
            profile.save();
            showGarage();
        }), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, dp(this, 10)));
        right.addView(Ui.button(this, "시연용 프로필로 시작 (Lv.19 아반떼 N 오너)", false, v -> {
            profile.seedDemo();
            showGarage();
        }));
        body.addView(right, Ui.weight(1));
    }

    // =====================================================================
    // 차고 (홈): 1600x900 설계 좌표로 배치한 뒤 화면 크기에 맞춰 확대·축소
    // =====================================================================
    private static final int ACCENT = 0xFF00A3E0;
    private static final int SUB = 0xFF9FB0C2;
    private FrameLayout garageStage;

    private void fitGarage() {
        if (garageStage == null || garageStage.getParent() != root) return;
        int w = root.getWidth(), hh = root.getHeight();
        if (w == 0 || hh == 0) { root.post(this::fitGarage); return; }
        float s = Math.min(w / 1600f, hh / 900f);
        garageStage.setPivotX(0);
        garageStage.setPivotY(0);
        garageStage.setScaleX(s);
        garageStage.setScaleY(s);
        garageStage.setTranslationX((w - 1600 * s) / 2f);
        garageStage.setTranslationY((hh - 900 * s) / 2f);
    }

    private TextView gt(String s, float px, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hh);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private TextView gButton(String label, boolean primary, View.OnClickListener cl) {
        TextView b = gt(label, primary ? 20 : 18, primary ? 0xFF04121C : 0xFFEEF2F6, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.round(primary ? ACCENT : 0xFF141C26, 16, primary ? ACCENT : 0xFF2A3646, primary ? 0 : 1));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(cl);
        return b;
    }

    private LinearLayout gPanel() {
        LinearLayout l = Ui.col(this);
        l.setPadding(28, 28, 28, 28);
        l.setBackground(Ui.round(0xEB121821, 24, 0xFF243041, 1));
        return l;
    }

    private LinearLayout.LayoutParams gap(int w, int hh, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, hh);
        lp.topMargin = top;
        return lp;
    }

    private void showGarage() {
        driving = false;
        root.removeAllViews();
        CarProfile car = profile.car();
        FrameLayout st = new FrameLayout(this);
        garageStage = st;
        root.addView(st, new FrameLayout.LayoutParams(1600, 900));
        fitGarage();

        st.addView(new GarageViews.Backdrop(this), at(0, 0, 1600, 900));

        // 상단 바
        TextView logo = gt("", 40, 0xFFEEF2F6, true);
        SpannableString ss = new SpannableString("Lv.N");
        ss.setSpan(new ForegroundColorSpan(ACCENT), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        logo.setText(ss);
        st.addView(logo, at(48, 34, -2, -2));
        st.addView(gt("내 차고 · " + car.name + " · " + car.trans, 18, SUB, false), at(160, 50, -2, -2));
        LinearLayout tr = Ui.row(this);
        tr.addView(gt(String.format(Locale.KOREA, "누적 %,.0fkm · 주행 %d회", profile.odometerKm, profile.tripCount), 16, SUB, false));
        TextView voice = gt(profile.voiceOn ? "음성 안내 켬" : "음성 안내 끔", 15, 0xFFC9D6E3, true);
        TextView setting = gt("설정", 15, 0xFFC9D6E3, true);
        TextView[] pills = {voice, setting};
        for (TextView pv : pills) {
            pv.setPadding(18, 10, 18, 10);
            pv.setBackground(Ui.round(0xFF141C26, 999, 0xFF2A3646, 1));
            pv.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = 14;
            tr.addView(pv, lp);
        }
        voice.setOnClickListener(v -> {
            profile.voiceOn = !profile.voiceOn;
            fx.voiceOn = profile.voiceOn;
            profile.save();
            showGarage();
        });
        setting.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams trp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        trp.topMargin = 38;
        trp.rightMargin = 48;
        st.addView(tr, trp);

        // 왼쪽: 스탯 오각형
        Stat[] top = profile.topTwo();
        LinearLayout left = gPanel();
        left.addView(gt("스탯", 22, 0xFFEEF2F6, true));
        left.addView(gt("스탯별 등급 0~100 · 상위 두 스탯이 캐릭터를 정해요", 14, SUB, false), gap(-2, -2, 12));
        GarageViews.Pentagon pent = new GarageViews.Pentagon(this);
        pent.set(grades());
        left.addView(pent, gap(344, 345, 12));
        LinearLayout chips = Ui.row(this);
        for (int i = 0; i < 2; i++) {
            TextView ch = gt((i + 1) + "위 " + top[i].label, 14, 0xFFC9D6E3, false);
            ch.setPadding(12, 7, 12, 7);
            ch.setBackground(Ui.round(0xFF1A2532, 999, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = 8;
            chips.addView(ch, lp);
        }
        left.addView(chips, gap(-2, -2, 12));
        st.addView(left, at(48, 140, 400, 560));

        // 가운데: 차
        if (car.id.startsWith("avante_n")) {
            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setImageResource(R.drawable.car_avante_n);
            img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            st.addView(img, at(470, 386, 660, 254));
        } else {
            TextView ph = gt(car.name, 64, 0xFF2A3646, true);
            ph.setGravity(Gravity.CENTER);
            st.addView(ph, at(470, 430, 660, 180));
        }

        // 차 아래: 레벨 · 캐릭터
        int lv = profile.level();
        CharacterClass cc = profile.character();
        LinearLayout lvRow = Ui.row(this);
        lvRow.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lvRow.addView(gt("Lv." + lv, 56, 0xFFEEF2F6, true));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = 16;
        nlp.bottomMargin = 6;
        lvRow.addView(gt(cc.name, 24, 0xFFDCE6F0, true), nlp);
        st.addView(lvRow, at(470, 650, 660, 66));

        int need = Profile.xpForLevel(lv + 1) - profile.totalXp();
        st.addView(new GarageViews.Pill(this, 0xFF1F2A37, ACCENT, profile.levelProgress()), at(630, 730, 340, 12));
        FrameLayout xpRow = new FrameLayout(this);
        xpRow.addView(gt(top[0].label + " + " + top[1].label, 15, SUB, false), new FrameLayout.LayoutParams(-2, -2, Gravity.START));
        xpRow.addView(gt("다음 레벨까지 " + need + " XP", 15, SUB, false), new FrameLayout.LayoutParams(-2, -2, Gravity.END));
        st.addView(xpRow, at(630, 752, 340, 24));

        // 오른쪽: 상태
        LinearLayout right = gPanel();
        right.addView(gt("상태", 22, 0xFFEEF2F6, true));
        statusBlock(right, "컨디션", 0xFFFF6B74, 100, 0xFF2A1A1E, 0xFFE5202E, "연속 운전 0분 · 2시간이 되면 졸음쉼터를 안내해요");
        float d = profile.durability();
        String[] names = {"엔진오일 교체", "타이어 위치 교환", "정기 점검"};
        double[] left2 = {Profile.OIL_INTERVAL - (profile.odometerKm - profile.lastOilKm),
                Profile.TIRE_INTERVAL - (profile.odometerKm - profile.lastTireRotateKm),
                Profile.INSP_INTERVAL - (profile.odometerKm - profile.lastInspectionKm)};
        float[] lives = {profile.oilLife(), profile.tireLife(), profile.inspLife()};
        int u = 0;
        for (int i = 1; i < 3; i++) if (lives[i] < lives[u]) u = i;
        final String urgent = names[u].replace(" 교체", "");
        String dNote = left2[u] > 0 ? String.format(Locale.KOREA, "%s까지 %,.0fkm", names[u], left2[u]) : names[u] + " 시기가 지났어요";
        statusBlock(right, "내구도", 0xFF6CB8FF, Math.round(d * 100), 0xFF16233A, d < 0.2f ? 0xFFE5202E : 0xFF2F7DF6, dNote);
        right.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        right.addView(gt("다음 보상", 14, SUB, false));
        TextView reward = gt(nextMilestone(lv), 16, 0xFFEEF2F6, false);
        reward.setLineSpacing(6, 1);
        reward.setPadding(16, 16, 16, 16);
        reward.setBackground(Ui.round(0xFF16202B, 16, 0xFF263445, 1));
        right.addView(reward, gap(-1, -2, 12));
        TextView bh = gt("블루핸즈 예약", 17, 0xFFCFE4FF, true);
        bh.setGravity(Gravity.CENTER);
        bh.setBackground(Ui.round(0x00000000, 14, 0xFF2F7DF6, 1));
        bh.setClickable(true);
        bh.setOnClickListener(v -> showBooking(urgent));
        right.addView(bh, gap(-1, 56, 12));
        st.addView(right, at(1152, 140, 400, 560));

        // 하단 메뉴
        LinearLayout bar = Ui.row(this);
        Object[][] menu = {
                {"주행 시작", 1.2f, (Runnable) this::startLive},
                {"시나리오 재생", 1.1f, (Runnable) this::showScenarioPicker},
                {"정비", 0.9f, (Runnable) this::showMaintenance},
                {"오너 랭킹", 1f, (Runnable) this::showRanking},
                {"레벨 보상", 1f, (Runnable) this::showRewards},
                {"영상용 자동 데모", 1.2f, (Runnable) this::startAutoDemo},
        };
        for (int i = 0; i < menu.length; i++) {
            final Runnable go = (Runnable) menu[i][2];
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, (Float) menu[i][1]);
            if (i > 0) lp.leftMargin = 14;
            bar.addView(gButton((String) menu[i][0], i == 0, v -> go.run()), lp);
        }
        st.addView(bar, at(48, 800, 1504, 64));
    }

    private void statusBlock(LinearLayout parent, String name, int nameColor, int pct, int track, int fill, String note) {
        FrameLayout row = new FrameLayout(this);
        row.addView(gt(name, 18, nameColor, true), new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.BOTTOM));
        row.addView(gt(pct + "%", 26, 0xFFEEF2F6, true), new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM));
        parent.addView(row, gap(-1, -2, 28));
        parent.addView(new GarageViews.Pill(this, track, fill, pct / 100f), gap(-1, 22, 10));
        parent.addView(gt(note, 14, SUB, false), gap(-1, -2, 10));
    }

    private int[] grades() {
        int[] g = new int[5];
        for (Stat s : Stat.values()) g[s.ordinal()] = profile.grade(s);
        return g;
    }

    // =====================================================================
    // 설정
    // =====================================================================
    private void showSettings() {
        LinearLayout page = frame("설정", "차종 · 프로필");
        addBack(headOf(page));
        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "내 차", 18, Ui.TEXT, true));
        c1.addView(Ui.space(this, 1, 8));
        for (CarProfile cp : CarProfile.ALL) {
            final CarProfile c = cp;
            c1.addView(Ui.button(this, cp.label(), cp.id.equals(profile.carId), v -> {
                profile.carId = c.id; profile.save(); showSettings();
            }), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, dp(this, 10)));
        }
        c1.addView(small("기어비는 공개 제원 기반 근사치입니다. 실차 데이터로 보정 예정."));
        body.addView(c1, Ui.margins(Ui.weight(1), 0, 0, dp(this, 16), 0));
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "프로필", 18, Ui.TEXT, true));
        c2.addView(Ui.space(this, 1, 8));
        c2.addView(Ui.button(this, "시연용 프로필 불러오기 (Lv.19)", false, v -> { profile.seedDemo(); toast("시연용 프로필을 불러왔어요"); showGarage(); }),
                Ui.margins(Ui.lp(-1, -2), 0, 0, 0, dp(this, 10)));
        c2.addView(Ui.button(this, "처음부터 다시 시작", false, v -> { profile.reset(); showOnboarding(); }));
        c2.addView(Ui.space(this, 1, 14));
        c2.addView(small("모든 주행 데이터는 차량 안에만 저장됩니다."));
        body.addView(c2, Ui.weight(1));
    }

    // =====================================================================
    // 시나리오 선택
    // =====================================================================
    private void showScenarioPicker() {
        LinearLayout page = frame("시나리오 재생", "실차에서 매번 재현하기 어려운 상황을 같은 판정 엔진으로 재생합니다");
        addBack(headOf(page));
        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));
        List<Scenario> list = Scenario.all(profile.car());
        String[] notes = {
                "누적 5km마다 체력 · 깜빡이 차선변경 → 매너\n완만한 커브·교차로 회전은 차선변경으로 세지 않음\n부드럽게 일찍 도착 → 스피드 +1",
                "패들 적정 회전수 변속 10회·적정 단수 10분 → 테크닉\n처음 가는 산길·야간·비 → 담대함\n100km 돌파 → 체력 · 2시간 → 졸음쉼터 안내",
                "등록 서킷 안에서만 스피드가 크게 오릅니다\n제로백·최고 속도 = 내 차 기록\n급가속·급제동 제한 해제",
        };
        for (int i = 0; i < list.size(); i++) {
            final Scenario sc = list.get(i);
            LinearLayout c = Ui.card(this);
            c.addView(small("시나리오 " + (i + 1)));
            c.addView(Ui.text(this, sc.title, 24, Ui.TEXT, true));
            c.addView(Ui.text(this, sc.subtitle, 15, Ui.MUTED, false));
            c.addView(Ui.space(this, 1, 12));
            c.addView(Ui.text(this, notes[i], 15, Ui.TEXT, false));
            View sp = new View(this);
            c.addView(sp, Ui.weightV(1));
            long min = sc.durationMs() / 60000;
            c.addView(small("주행 시간 " + (min / 60 > 0 ? (min / 60) + "시간 " : "") + (min % 60) + "분 → 시연 약 " + Math.round(realSeconds(sc)) + "초"));
            c.addView(Ui.space(this, 1, 10));
            c.addView(Ui.button(this, "▶  재생", true, v -> startScenario(sc)));
            body.addView(c, Ui.margins(Ui.weight(1), 0, 0, i < list.size() - 1 ? dp(this, 16) : 0, 0));
        }
    }

    private static double realSeconds(Scenario sc) {
        double s = 0;
        for (int i = 0; i < sc.rates.size(); i++) s += Scenario.STEP_MS / sc.rates.get(i);
        return s / 1000.0;
    }

    // =====================================================================
    // 주행 세션
    // =====================================================================
    private void beginSession() {
        levelAtStart = profile.level();
        charAtStart = profile.character().name;
        tripXp = 0;
        logLines.clear();
        engine = new JudgeEngine(profile.car(), this::onDriveEvent, profile.staminaCarryKm);
        engine.setDurabilityZero(profile.durability() <= 0f);
        driving = true;
        lastSample = null;
    }

    private void startScenario(Scenario sc) {
        live = false;
        playing = sc;
        playIdx = 0;
        simCursor = 0;
        beginSession();
        showDriveScreen();
        h.removeCallbacks(playTick);
        h.post(playTick);
    }

    private void startLive() {
        live = true;
        playing = null;
        beginSession();
        showDriveScreen();
        liveSrc = new LiveCarSource(this);
        boolean ok = liveSrc.start(this::onLiveSample);
        DriveRecordService.start(this);   // 내비 화면으로 바꿔도 기록이 이어지도록
        if (dLiveStatus != null) dLiveStatus.setText(liveSrc.status());
        if (!ok) toast(liveSrc.status());
    }

    private final Runnable playTick = this::playStep;

    private void playStep() {
        {
            if (!driving || playing == null) return;
            int n = playing.samples.size();
            if (playIdx >= n) { finishDrive(playing.etaActualMin); return; }
            float rate = playing.rates.get(playIdx);
            simCursor += TICK_MS * rate * baseMul;
            DriveSample s = null;
            while (playIdx < n && playing.samples.get(playIdx).tMs <= simCursor) {
                s = playing.samples.get(playIdx++);
                engine.process(s);
                if (!driving) return;
            }
            if (s != null) { lastSample = s; updateDriveUi(s); }
            h.postDelayed(playTick, TICK_MS);
        }
    }

    private void onLiveSample(DriveSample s) {
        if (!driving) return;
        engine.process(s);
        lastSample = s;
        updateDriveUi(s);
        JudgeEngine.Trip t = engine.trip();
        if (s.gearSel == 'P' && t.distanceKm > 0.02 && t.durationMs > 20000) finishDrive(0);
    }

    private void onDriveEvent(DriveEvent e) {
        fx.onEvent(e);
        if (e.stat != null && e.points > 0) {
            int before = profile.level();
            profile.points[e.stat.ordinal()] += e.points;
            tripXp += e.points;
            int after = profile.level();
            if (after > before) {
                fx.levelUp(after);
                addLog("★ LEVEL UP!  Lv." + after, Ui.N_RED);
            }
        }
        int color = e.stat != null ? e.stat.color : e.kind == DriveEvent.WARN ? Ui.WARN : Ui.MUTED;
        addLog(e.chip(), color);
    }

    private void addLog(String s, int color) {
        String time = lastSample == null ? "" : clock(lastSample.tMs) + "  ";
        SpannableString ss = new SpannableString(time + s);
        ss.setSpan(new ForegroundColorSpan(Ui.MUTED), 0, time.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(color), time.length(), ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        logLines.add(0, ss);
        while (logLines.size() > 9) logLines.remove(logLines.size() - 1);
        if (dLog != null) {
            dLog.removeAllViews();
            for (CharSequence l : logLines) {
                TextView t = Ui.text(this, "", 15, Ui.TEXT, false);
                t.setText(l);
                t.setSingleLine(true);
                dLog.addView(t);
            }
        }
    }

    private void stopDriveLoop() {
        driving = false;
        h.removeCallbacks(playTick);
        if (liveSrc != null) { liveSrc.stop(); liveSrc = null; }
        DriveRecordService.stop(this);
    }

    private void finishDrive(int etaActualMin) {
        if (!driving) return;
        long t = lastSample == null ? 0 : lastSample.tMs;
        JudgeEngine.Trip trip = engine.finish(t, etaActualMin, profile);
        stopDriveLoop();
        profile.odometerKm += trip.distanceKm;
        profile.staminaCarryKm = engine.staminaLeftoverKm();
        profile.tripCount++;
        profile.save();
        showResult(trip);
    }

    // =====================================================================
    // 주행 화면 — 주행 중에는 최소 정보만. 오른쪽 패널은 시연용.
    // =====================================================================
    private void showDriveScreen() {
        root.removeAllViews();
        LinearLayout page = Ui.row(this);
        int p = dp(this, 24);
        page.setPadding(p, p, p, p);
        page.setGravity(Gravity.TOP);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = Ui.col(this);
        main.setGravity(Gravity.CENTER_HORIZONTAL);
        main.setBackground(Ui.round(0xFF07080A, dp(this, 18), Ui.LINE, 1));
        main.setPadding(p, p, p, p);
        TextView title = Ui.text(this, "주행 중", 22, Ui.MUTED, true);
        main.addView(title);
        dRing = new Views.Ring(this);
        main.addView(dRing, Ui.weightV(1));
        dTripXp = Ui.text(this, "이번 주행 +0 XP", 22, Ui.TEXT, true);
        dTripXp.setGravity(Gravity.CENTER);
        main.addView(dTripXp);
        TextView hint = Ui.text(this, "화면은 보지 않아도 됩니다 · 성장은 효과음으로 알려드려요\n결과는 정차하면 열립니다", 16, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        main.addView(hint);
        page.addView(main, Ui.margins(Ui.weight(1.25f), 0, 0, dp(this, 20), 0));

        dSidePanel = Ui.card(this);
        LinearLayout side = dSidePanel;
        LinearLayout tagRow = Ui.row(this);
        TextView tag = Ui.text(this, live ? "  차량 신호 · 정차 중에만 표시  " : "  시연용 시나리오 패널 · 실제 주행 화면에는 표시되지 않음  ", 13, 0xFF001018, true);
        tag.setBackground(Ui.round(live ? Ui.GOOD : Ui.WARN, dp(this, 8), 0, 0));
        tagRow.addView(tag);
        side.addView(tagRow);
        side.addView(Ui.space(this, 1, 8));
        side.addView(Ui.text(this, live ? "실차 / Virtual Console 신호" : playing.title, 22, Ui.TEXT, true));
        dLabel = Ui.text(this, "", 16, Ui.N_BLUE, true);
        side.addView(dLabel);
        side.addView(Ui.space(this, 1, 8));

        LinearLayout stats = Ui.row(this);
        dClock = metric(stats, "시간");
        dSpeed = metric(stats, "속도");
        dGear = metric(stats, "기어");
        dRpm = metric(stats, "RPM(추정)");
        side.addView(stats);
        dFlags = Ui.text(this, "", 15, Ui.TEXT, false);
        side.addView(dFlags);
        side.addView(Ui.space(this, 1, 6));
        LinearLayout condRow = Ui.row(this);
        condRow.addView(Ui.text(this, "컨디션 ", 15, Ui.MUTED, false));
        dCondBar = new Views.Bar(this);
        condRow.addView(dCondBar, new LinearLayout.LayoutParams(0, dp(this, 10), 1));
        dCondText = Ui.text(this, "", 15, Ui.TEXT, false);
        dCondText.setPadding(dp(this, 10), 0, 0, 0);
        condRow.addView(dCondText);
        side.addView(condRow);
        side.addView(Ui.space(this, 1, 10));
        side.addView(Ui.text(this, "판정 기록 (운전자에게는 효과음으로만 전달)", 14, Ui.MUTED, false));
        dLog = Ui.col(this);
        side.addView(dLog, Ui.weightV(1));

        LinearLayout ctr = Ui.row(this);
        if (live) {
            dLiveStatus = Ui.text(this, "연결 중…", 14, Ui.MUTED, false);
            side.addView(dLiveStatus);
            side.addView(small("Virtual Console: 기어(P/D/수동 단수) · 속도 · 방향지시등 · 조향각"));
            ctr.addView(Ui.button(this, "주행 종료", true, v -> finishDrive(0)), Ui.weight(1));
            ctr.addView(Ui.space(this, 10, 1));
            ctr.addView(Ui.button(this, "취소", false, v -> onBackPressed()), Ui.weight(1));
        } else {
            dMulBtn = Ui.button(this, "배속 x" + (int) baseMul, false, v -> {
                baseMul = baseMul >= 4 ? 1 : baseMul * 2;
                dMulBtn.setText("배속 x" + (int) baseMul);
            });
            ctr.addView(dMulBtn, Ui.weight(1));
            ctr.addView(Ui.space(this, 10, 1));
            ctr.addView(Ui.button(this, "끝까지 빠르게", false, v -> baseMul = 40), Ui.weight(1));
            ctr.addView(Ui.space(this, 10, 1));
            ctr.addView(Ui.button(this, "중지", false, v -> onBackPressed()), Ui.weight(0.7f));
        }
        side.addView(Ui.space(this, 1, 8));
        side.addView(ctr, Ui.lp(-1, dp(this, 64)));

        dLiveHidden = Ui.card(this);
        dLiveHidden.setGravity(Gravity.CENTER);
        TextView hid = Ui.text(this, "주행 중에는 숨겨집니다\n정차하면 다시 보여요", 20, Ui.MUTED, true);
        hid.setGravity(Gravity.CENTER);
        dLiveHidden.addView(hid);
        dLiveHidden.setVisibility(View.GONE);

        FrameLayout sideWrap = new FrameLayout(this);
        sideWrap.addView(side, new FrameLayout.LayoutParams(-1, -1));
        sideWrap.addView(dLiveHidden, new FrameLayout.LayoutParams(-1, -1));
        page.addView(sideWrap, Ui.weight(1));
        updateRing();
    }

    private TextView metric(LinearLayout row, String label) {
        LinearLayout c = Ui.col(this);
        c.addView(Ui.text(this, label, 13, Ui.MUTED, false));
        TextView v = Ui.text(this, "-", 24, Ui.TEXT, true);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return v;
    }

    private void updateRing() {
        if (dRing == null) return;
        int lv = profile.level();
        dRing.set(profile.levelProgress(), lv > levelAtStart ? Ui.N_RED : Ui.N_BLUE, "Lv." + lv, profile.character().name).bigSize(48);
        if (dTripXp != null) dTripXp.setText("이번 주행 +" + tripXp + " XP");
    }

    private void updateDriveUi(DriveSample s) {
        updateRing();
        if (dClock == null) return;
        dClock.setText(clock(s.tMs));
        dSpeed.setText(String.format(Locale.KOREA, "%.0f", s.speedKmh));
        String g = s.gearSel == 'M' ? "M" + s.gear : s.gearSel == 'D' ? "D" + (s.gear > 0 ? s.gear : "") : String.valueOf(s.gearSel);
        dGear.setText(g);
        dRpm.setText(String.valueOf(engine.rpmOf(s)));
        dLabel.setText(s.label == null ? "" : s.label);
        StringBuilder f = new StringBuilder();
        if (s.night) f.append("● 야간   ");
        if (s.rain) f.append("● 비   ");
        if (s.newRoad) f.append("● 처음 가는 길   ");
        if (s.circuit) f.append("● 등록 서킷   ");
        if (s.turnSignal == 1) f.append("◀ 깜빡이   ");
        if (s.turnSignal == 2) f.append("깜빡이 ▶   ");
        if (Math.abs(s.steerDeg) >= 1) f.append(String.format(Locale.KOREA, "조향 %+.0f°", s.steerDeg));
        dFlags.setText(f.length() == 0 ? " " : f.toString());
        float c = engine.condition();
        dCondBar.set(c, c < 0.3f ? Ui.N_RED : c < 0.55f ? Ui.WARN : Ui.GOOD);
        long m = engine.continuousMs() / 60000;
        dCondText.setText(Math.round(c * 100) + "% · 연속 " + (m / 60) + "시간 " + (m % 60) + "분");
        if (live && dLiveHidden != null) {
            boolean moving = s.speedKmh > 1;
            dLiveHidden.setVisibility(moving ? View.VISIBLE : View.GONE);
            dSidePanel.setVisibility(moving ? View.INVISIBLE : View.VISIBLE);
            if (liveSrc != null && dLiveStatus != null) dLiveStatus.setText(liveSrc.status());
        }
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.KOREA, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    // =====================================================================
    // 주행 결과 (정차 후)
    // =====================================================================
    private void showResult(JudgeEngine.Trip t) {
        String title = playing != null ? playing.title : "실차 주행";
        LinearLayout page = frame("주행 결과 · " + title, String.format(Locale.KOREA, "%.1fkm · %s · 최고 %.0fkm/h",
                t.distanceKm, durationText(t.durationMs), t.maxSpeed));
        LinearLayout head = headOf(page);
        if (playing != null && !autoDemo) {
            final Scenario sc = playing;
            head.addView(Ui.button(this, "다시 재생", false, v -> startScenario(Scenario.all(profile.car()).get(indexOf(sc)))),
                    Ui.margins(Ui.lp(-2, -2), 0, 0, dp(this, 10), 0));
        }
        addBack(head);

        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));

        // 획득 스탯
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "획득 스탯", 18, Ui.TEXT, true));
        int max = 1;
        for (int e : t.earned) max = Math.max(max, e);
        for (Stat s : Stat.values()) {
            int e = t.earned[s.ordinal()];
            LinearLayout r = Ui.row(this);
            TextView lb = Ui.text(this, s.label, 17, s.color, true);
            r.addView(lb, new LinearLayout.LayoutParams(dp(this, 80), -2));
            Views.Bar bar = new Views.Bar(this);
            bar.set(e / (float) max, s.color);
            r.addView(bar, new LinearLayout.LayoutParams(0, dp(this, 12), 1));
            TextView v = Ui.text(this, "+" + e, 18, e > 0 ? Ui.TEXT : Ui.MUTED, true);
            v.setGravity(Gravity.END);
            r.addView(v, new LinearLayout.LayoutParams(dp(this, 64), -2));
            c1.addView(r, Ui.margins(Ui.lp(-1, -2), 0, dp(this, 14), 0, 0));
        }
        View sp = new View(this);
        c1.addView(sp, Ui.weightV(1));
        c1.addView(Ui.text(this, "합계 +" + t.total() + " XP", 26, Ui.N_BLUE, true));
        body.addView(c1, Ui.margins(Ui.weight(1), 0, 0, dp(this, 16), 0));

        // 하이라이트
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "이번 주행 하이라이트", 18, Ui.TEXT, true));
        c2.addView(Ui.space(this, 1, 6));
        List<String> hl = new ArrayList<>();
        if (t.scoredShifts > 0) hl.add("적정 회전수 변속 " + t.scoredShifts + "회" + (t.overRevs > 0 ? " · 과회전 위험 " + t.overRevs + "회(감점 없음)" : ""));
        if (t.laneChanges > 0) hl.add("차선변경 " + t.laneChanges + "회 중 깜빡이 " + t.signaledLaneChanges + "회");
        if (t.newRoadKm > 0.1) hl.add(String.format(Locale.KOREA, "처음 가는 길 %.1fkm 칠함", t.newRoadKm));
        if (t.nightMs > 60000) hl.add("야간 주행 " + (t.nightMs / 60000) + "분");
        if (t.rainMs > 60000) hl.add("빗길 주행 " + (t.rainMs / 60000) + "분");
        if (t.circuit) hl.add(String.format(Locale.KOREA, "서킷 최고 %.0fkm/h%s", t.circuitTopSpeed,
                t.zeroTo100 > 0 ? String.format(Locale.KOREA, " · 제로백 %.1f초", t.zeroTo100) : ""));
        if (t.harshAccel + t.harshBrake > 0) hl.add("급가속 " + t.harshAccel + " · 급제동 " + t.harshBrake + " (스피드 보너스 제외)");
        else if (!t.circuit) hl.add("급가속·급제동 0회");
        if (t.conditionAlerted || hasEvent(t, "졸음쉼터")) hl.add("2시간 연속 운전 → 졸음쉼터 안내" + (hasEvent(t, "컨디션 회복") ? " → 휴식 후 컨디션 회복" : ""));
        for (String s : hl) c2.addView(Ui.text(this, "·  " + s, 16, Ui.TEXT, false), Ui.margins(Ui.lp(-1, -2), 0, dp(this, 6), 0, 0));
        if (!t.circuit && t.laneChanges > 0) {
            int par = Math.max(2, (int) Math.round(t.distanceKm / 6.0));
            int diff = t.laneChanges - par;
            String res = diff <= -2 ? "이글" : diff == -1 ? "버디" : diff == 0 ? "파" : diff == 1 ? "보기" : "더블 보기+";
            c2.addView(Ui.space(this, 1, 12));
            c2.addView(Ui.text(this, "기타 미션 · 차선변경 골프: " + t.laneChanges + "타 / 파 " + par + " → " + res, 16, Ui.WARN, true));
        }
        body.addView(c2, Ui.margins(Ui.weight(1.25f), 0, 0, dp(this, 16), 0));

        // 레벨·캐릭터·보상
        LinearLayout c3 = Ui.card(this);
        int lv = profile.level();
        c3.addView(Ui.text(this, lv > levelAtStart ? "LEVEL UP!" : "레벨", 18, lv > levelAtStart ? Ui.N_RED : Ui.TEXT, true));
        c3.addView(Ui.text(this, lv > levelAtStart ? "Lv." + levelAtStart + " → Lv." + lv : "Lv." + lv, 36, Ui.TEXT, true));
        Views.Bar xb = new Views.Bar(this);
        xb.set(profile.levelProgress(), Ui.N_BLUE);
        c3.addView(xb, Ui.margins(Ui.lp(-1, dp(this, 10)), 0, dp(this, 6), 0, dp(this, 10)));
        String now = profile.character().name;
        c3.addView(Ui.text(this, now.equals(charAtStart) ? "캐릭터: " + now : "캐릭터 진화: " + charAtStart + " → " + now, 17,
                now.equals(charAtStart) ? Ui.TEXT : Ui.WARN, true));
        c3.addView(Ui.space(this, 1, 10));
        List<String> rw = new ArrayList<>();
        for (int l = levelAtStart + 1; l <= lv; l++) rw.addAll(rewardsFor(l));
        if (rw.isEmpty()) c3.addView(small("다음 보상: " + nextMilestone(lv)));
        else {
            c3.addView(Ui.text(this, "획득한 보상", 15, Ui.MUTED, false));
            for (String r : rw) c3.addView(Ui.text(this, "🎁  " + r, 16, Ui.TEXT, false));
        }
        body.addView(c3, Ui.weight(1));

        if (autoDemo) h.postDelayed(this::nextAuto, 8000);
    }

    private static boolean hasEvent(JudgeEngine.Trip t, String key) {
        for (DriveEvent e : t.events) if (e.title.contains(key)) return true;
        return false;
    }

    private int indexOf(Scenario sc) {
        switch (sc.id) { case "friday": return 1; case "track": return 2; default: return 0; }
    }

    private static String durationText(long ms) {
        long m = ms / 60000;
        return m >= 60 ? (m / 60) + "시간 " + (m % 60) + "분" : m + "분";
    }

    // =====================================================================
    // 보상
    // =====================================================================
    static List<String> rewardsFor(int level) {
        List<String> r = new ArrayList<>();
        r.add("Lv." + level + " 디지털 배지");
        if (level % 5 == 0) r.add("한정 계기판 테마");
        if (level % 10 == 0) r.add("N 브랜드 굿즈 할인권");
        if (level == 20 || level == 40) r.add("인제스피디움 N 오너 트랙데이 참가권 · 할인권 (내 차로 스피드 획득)");
        if (level == 30) r.add("HMG 드라이빙 익스피리언스 센터 우선 예약권 (보조 보상)");
        return r;
    }

    static String nextMilestone(int lv) {
        int next10 = (lv / 10 + 1) * 10;
        if (lv < 20) return "Lv.20 인제 N 오너 트랙데이 참가권";
        if (lv < 30) return "Lv.30 HMG 드라이빙 센터 우선 예약권";
        if (lv < 40) return next10 == 40 || next10 > 40 ? "Lv.40 인제 N 오너 트랙데이 참가권" : "Lv." + next10 + " N 굿즈 할인권";
        return "Lv." + next10 + " N 굿즈 할인권";
    }

    private void showRewards() {
        LinearLayout page = frame("레벨 보상", "레벨업마다 배지 · 10레벨마다 N 굿즈 할인권 · Lv.20/40 인제 N 오너 트랙데이 · Lv.30 HMG 드라이빙 센터");
        addBack(headOf(page));
        int lv = profile.level();
        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));
        int[][] groups = {{5, 10, 15}, {20, 25, 30}, {35, 40, 50}};
        for (int gi = 0; gi < groups.length; gi++) {
            LinearLayout c = Ui.card(this);
            for (int l : groups[gi]) {
                boolean got = lv >= l;
                c.addView(Ui.text(this, (got ? "✔ " : "🔒 ") + "Lv." + l, 22, got ? Ui.N_BLUE : Ui.MUTED, true));
                for (String r : rewardsFor(l)) if (!r.endsWith("디지털 배지")) c.addView(Ui.text(this, "   " + r, 16, got ? Ui.TEXT : Ui.MUTED, false));
                c.addView(Ui.space(this, 1, 14));
            }
            body.addView(c, Ui.margins(Ui.weight(1), 0, 0, gi < 2 ? dp(this, 16) : 0, 0));
        }
        page.addView(Ui.space(this, 1, 10));
        page.addView(small("현재 Lv." + lv + " · 보상 지급·쿠폰 연동은 운영 단계에서 현대 N 행사·서킷 운영사와 연결합니다 (시연 화면)"));
        if (autoDemo) h.postDelayed(this::nextAuto, 5000);
    }

    // =====================================================================
    // 오너 랭킹 (선택 참여 · 닉네임과 캐릭터명만)
    // =====================================================================
    private void showRanking() {
        LinearLayout page = frame("전체 오너 레벨 랭킹", "선택 참여 · 닉네임과 캐릭터명만 공개 · 아래는 예시 데이터");
        addBack(headOf(page));
        Object[][] owners = {
                {"트랙위의고양이", 47, "그리드의 제왕"}, {"새벽세시인제", 41, "새벽 고개의 전설"}, {"패들장인", 38, "마에스트로"},
                {"코나N아빠", 35, "철인 크루저"}, {"벨N7년차", 33, "스트리트 패밀리 보스"}, {"지도칠하는중", 29, "지도 밖의 개척자"},
                {"화이트아반떼N", 24, "마에스트로"}, {"주말엔서킷", 21, "그리드의 제왕"}, {"출퇴근N", 16, "철인 크루저"},
                {"N라인입문", 9, "지도 밖의 개척자"},
        };
        List<Object[]> list = new ArrayList<>();
        int lv = profile.level();
        boolean inserted = false;
        for (Object[] o : owners) {
            if (!inserted && lv >= (Integer) o[1]) { list.add(new Object[]{"나", lv, profile.character().name}); inserted = true; }
            list.add(o);
        }
        if (!inserted) list.add(new Object[]{"나", lv, profile.character().name});
        LinearLayout card = Ui.card(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout rows = Ui.col(this);
        for (int i = 0; i < list.size(); i++) {
            Object[] o = list.get(i);
            boolean me = "나".equals(o[0]);
            LinearLayout r = Ui.row(this);
            r.setPadding(dp(this, 14), dp(this, 10), dp(this, 14), dp(this, 10));
            if (me) r.setBackground(Ui.round(0x3300B3E3, dp(this, 10), Ui.N_BLUE, 1));
            r.addView(Ui.text(this, (i + 1) + "", 20, i < 3 ? Ui.WARN : Ui.MUTED, true), new LinearLayout.LayoutParams(dp(this, 56), -2));
            r.addView(Ui.text(this, (String) o[0], 20, Ui.TEXT, me), new LinearLayout.LayoutParams(0, -2, 1.2f));
            r.addView(Ui.text(this, (String) o[2], 18, me ? Ui.N_BLUE : Ui.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1.4f));
            TextView l = Ui.text(this, "Lv." + o[1], 20, Ui.TEXT, true);
            l.setGravity(Gravity.END);
            r.addView(l, new LinearLayout.LayoutParams(dp(this, 100), -2));
            rows.addView(r);
        }
        sv.addView(rows);
        card.addView(sv, Ui.weightV(1));
        page.addView(card, Ui.weightV(1));
        if (autoDemo) h.postDelayed(this::nextAuto, 6000);
    }

    // =====================================================================
    // 정비 · 내구도
    // =====================================================================
    private void showMaintenance() {
        LinearLayout page = frame("정비 · 내구도", "제때 정비하면 체력 +10 · 블루핸즈 원탭 예약");
        addBack(headOf(page));
        LinearLayout body = Ui.row(this);
        body.setGravity(Gravity.TOP);
        page.addView(body, Ui.weightV(1));

        LinearLayout c0 = Ui.card(this);
        c0.setGravity(Gravity.CENTER_HORIZONTAL);
        c0.addView(Ui.text(this, "내구도", 18, Ui.TEXT, true));
        Views.Ring r = new Views.Ring(this);
        float d = profile.durability();
        r.set(d, d < 0.2f ? Ui.N_RED : d < 0.4f ? Ui.WARN : Ui.N_BLUE, Math.round(d * 100) + "%", "가장 급한 항목 기준");
        c0.addView(r, Ui.weightV(1));
        c0.addView(small(String.format(Locale.KOREA, "누적 주행 %,.0fkm", profile.odometerKm)));
        body.addView(c0, Ui.margins(Ui.weight(0.8f), 0, 0, dp(this, 16), 0));

        LinearLayout c1 = Ui.card(this);
        maintRow(c1, "엔진오일", profile.odometerKm - profile.lastOilKm, Profile.OIL_INTERVAL, profile.oilLife(), () -> profile.lastOilKm = profile.odometerKm);
        maintRow(c1, "타이어 위치 교환", profile.odometerKm - profile.lastTireRotateKm, Profile.TIRE_INTERVAL, profile.tireLife(), () -> profile.lastTireRotateKm = profile.odometerKm);
        maintRow(c1, "정기 점검", profile.odometerKm - profile.lastInspectionKm, Profile.INSP_INTERVAL, profile.inspLife(), () -> profile.lastInspectionKm = profile.odometerKm);
        body.addView(c1, Ui.weight(2f));
        if (autoDemo) h.postDelayed(this::nextAuto, 5000);
    }

    private void maintRow(LinearLayout parent, String name, double used, double interval, float life, Runnable done) {
        LinearLayout r = Ui.row(this);
        LinearLayout info = Ui.col(this);
        info.addView(Ui.text(this, name, 20, Ui.TEXT, true));
        info.addView(small(String.format(Locale.KOREA, "교환·점검 후 %,.0fkm / 주기 %,.0fkm", used, interval)));
        Views.Bar bar = new Views.Bar(this);
        bar.set(life, life < 0.2f ? Ui.N_RED : life < 0.4f ? Ui.WARN : Ui.N_BLUE);
        info.addView(bar, Ui.margins(Ui.lp(-1, dp(this, 10)), 0, dp(this, 8), 0, 0));
        r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(Ui.space(this, 16, 1));
        r.addView(Ui.button(this, "블루핸즈 예약", life < 0.4f, v -> showBooking(name)));
        r.addView(Ui.space(this, 10, 1));
        r.addView(Ui.button(this, "정비 완료 기록", false, v -> {
            int before = profile.level();
            done.run();
            profile.points[Stat.STAMINA.ordinal()] += 10;
            profile.save();
            toast(name + " 정비 완료 · 체력 +10");
            if (profile.level() > before) fx.levelUp(profile.level());
            showMaintenance();
        }));
        parent.addView(r, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, dp(this, 22)));
    }

    private void showBooking(String item) {
        new AlertDialog.Builder(this)
                .setTitle("블루핸즈 원탭 예약 · " + item)
                .setMessage("가까운 블루핸즈와 가능한 시간을 불러와 한 번에 예약합니다.\n동의한 경우 정비 시기가 되면 자동으로 예약을 제안해요.\n\n※ 시연 화면입니다. 실제 예약은 블루핸즈 예약 시스템 연동 후 제공됩니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    // =====================================================================
    // 영상용 자동 데모: 차고 → 출근 → 결과 → 금요일 밤 → 결과(레벨업) → 서킷 → 결과 → 정비 → 랭킹 → 보상 → 차고
    // =====================================================================
    private void startAutoDemo() {
        profile.seedDemo();
        autoDemo = true;
        autoStep = 0;
        baseMul = 2;   // 영상 길이 약 3분 30초
        toast("영상용 자동 데모 시작 (시연용 프로필 Lv.19)");
        showGarage();
        h.postDelayed(this::nextAuto, 5000);
    }

    private void nextAuto() {
        if (!autoDemo) return;
        List<Scenario> l = Scenario.all(profile.car());
        switch (autoStep++) {
            case 0: startScenario(l.get(0)); break;
            case 1: startScenario(l.get(1)); break;
            case 2: startScenario(l.get(2)); break;
            case 3: showMaintenance(); break;
            case 4: showRanking(); break;
            case 5: showRewards(); break;
            default: autoDemo = false; showGarage(); toast("자동 데모 끝"); break;
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @SuppressWarnings("unused")
    private static Typeface bold() { return Typeface.DEFAULT_BOLD; }
}
