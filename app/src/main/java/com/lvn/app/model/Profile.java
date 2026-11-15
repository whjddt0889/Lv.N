package com.lvn.app.model;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 오너 프로필: 누적 스탯, 레벨, 정비 이력. 모든 데이터는 차량(기기) 안에만 저장된다. */
public final class Profile {
    private static final String PREF = "lvn_profile";

    public boolean created;
    public String carId = CarProfile.ALL[0].id;
    public final int[] points = new int[Stat.values().length];
    public double odometerKm = 12000;
    public double lastOilKm = 7500;
    public double lastTireRotateKm = 5000;
    public double lastInspectionKm = 2000;
    public int tripCount;
    public double bestTopSpeed;
    public double best0to100;   // 초, 0 = 기록 없음
    public String etaDay = "";
    public int etaCountToday;
    public boolean voiceOn = true;
    public double staminaCarryKm;   // 체력 점수로 바뀌지 않고 남은 누적 거리

    private final SharedPreferences sp;

    private Profile(Context c) {
        sp = c == null ? null : c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 저장소 없이 쓰는 프로필 (판정 엔진 단위 검증용). */
    public static Profile detached() { return new Profile(null); }

    public static Profile load(Context c) {
        Profile p = new Profile(c);
        SharedPreferences s = p.sp;
        p.created = s.getBoolean("created", false);
        p.carId = s.getString("carId", p.carId);
        for (Stat st : Stat.values()) p.points[st.ordinal()] = s.getInt("pt_" + st.name(), 0);
        p.odometerKm = s.getFloat("odo", (float) p.odometerKm);
        p.lastOilKm = s.getFloat("oil", (float) p.lastOilKm);
        p.lastTireRotateKm = s.getFloat("tire", (float) p.lastTireRotateKm);
        p.lastInspectionKm = s.getFloat("insp", (float) p.lastInspectionKm);
        p.tripCount = s.getInt("trips", 0);
        p.bestTopSpeed = s.getFloat("top", 0f);
        p.best0to100 = s.getFloat("zero100", 0f);
        p.etaDay = s.getString("etaDay", "");
        p.etaCountToday = s.getInt("etaCnt", 0);
        p.voiceOn = s.getBoolean("voice", true);
        p.staminaCarryKm = s.getFloat("stCarry", 0f);
        return p;
    }

    public void save() {
        if (sp == null) return;
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean("created", created);
        e.putString("carId", carId);
        for (Stat st : Stat.values()) e.putInt("pt_" + st.name(), points[st.ordinal()]);
        e.putFloat("odo", (float) odometerKm);
        e.putFloat("oil", (float) lastOilKm);
        e.putFloat("tire", (float) lastTireRotateKm);
        e.putFloat("insp", (float) lastInspectionKm);
        e.putInt("trips", tripCount);
        e.putFloat("top", (float) bestTopSpeed);
        e.putFloat("zero100", (float) best0to100);
        e.putString("etaDay", etaDay);
        e.putInt("etaCnt", etaCountToday);
        e.putBoolean("voice", voiceOn);
        e.putFloat("stCarry", (float) staminaCarryKm);
        e.apply();
    }

    public void reset() {
        if (sp != null) sp.edit().clear().apply();
        created = false;
        for (int i = 0; i < points.length; i++) points[i] = 0;
        odometerKm = 12000; lastOilKm = 7500; lastTireRotateKm = 5000; lastInspectionKm = 2000;
        tripCount = 0; bestTopSpeed = 0; best0to100 = 0; etaDay = ""; etaCountToday = 0;
    }

    /** 시연용 프로필: Lv.19, 체력·매너 점수가 가장 많지만 등급은 테크닉·담대함이 높은 아반떼 N 오너. */
    public void seedDemo() {
        created = true;
        carId = "avante_n_dct";
        points[Stat.TECH.ordinal()] = 230;
        points[Stat.SPEED.ordinal()] = 50;
        points[Stat.BOLD.ordinal()] = 160;
        points[Stat.MANNER.ordinal()] = 300;
        points[Stat.STAMINA.ordinal()] = 330;
        staminaCarryKm = 0;
        odometerKm = 14620;
        lastOilKm = 7400;
        lastTireRotateKm = 6200;
        lastInspectionKm = 4800;
        tripCount = 86;
        bestTopSpeed = 0;
        best0to100 = 0;
        save();
    }

    public CarProfile car() { return CarProfile.byId(carId); }

    public int totalXp() {
        int s = 0;
        for (int p : points) s += p;
        return s;
    }

    /** 레벨 L 에서 L+1 로 가는 데 필요한 XP. */
    public static int xpToNext(int level) { return 20 + 4 * level; }

    /** 레벨 L 에 도달하기 위한 누적 XP. */
    public static int xpForLevel(int level) {
        int s = 0;
        for (int i = 1; i < level; i++) s += xpToNext(i);
        return s;
    }

    public static int levelForXp(int xp) {
        int l = 1;
        while (xpForLevel(l + 1) <= xp) l++;
        return l;
    }

    public int level() { return levelForXp(totalXp()); }

    public float levelProgress() {
        int l = level();
        int base = xpForLevel(l);
        return (totalXp() - base) / (float) xpToNext(l);
    }

    public Stat[] topTwo() {
        Stat best = null, second = null;
        for (Stat s : Stat.values()) {
            int g = s.grade(points[s.ordinal()]);
            if (best == null || g > best.grade(points[best.ordinal()])) { second = best; best = s; }
            else if (second == null || g > second.grade(points[second.ordinal()])) second = s;
        }
        return new Stat[]{best, second};
    }

    public CharacterClass character() {
        Stat[] t = topTwo();
        return CharacterClass.of(t[0], t[1]);
    }

    public int grade(Stat s) { return s.grade(points[s.ordinal()]); }

    /** ETA 보너스는 하루 최대 3회. */
    public boolean canEarnEtaToday() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
        if (!today.equals(etaDay)) { etaDay = today; etaCountToday = 0; }
        return etaCountToday < 3;
    }

    // ---- 내구도 (정비 주기) ----
    public static final double OIL_INTERVAL = 10000, TIRE_INTERVAL = 10000, INSP_INTERVAL = 15000;

    public float oilLife() { return life(odometerKm - lastOilKm, OIL_INTERVAL); }
    public float tireLife() { return life(odometerKm - lastTireRotateKm, TIRE_INTERVAL); }
    public float inspLife() { return life(odometerKm - lastInspectionKm, INSP_INTERVAL); }

    public float durability() { return Math.min(oilLife(), Math.min(tireLife(), inspLife())); }

    private static float life(double used, double interval) {
        return (float) Math.max(0, Math.min(1, 1 - used / interval));
    }
}
