package com.lvn.app.model;

/** Lv.N 의 5가지 성장 스탯. (구형 D8 호환을 위해 enum 대신 고정 인스턴스 클래스로 구현) */
public final class Stat {
    public static final Stat TECH = new Stat(0, "TECH", "테크닉", 0xFF00B3E3, 770f);
    public static final Stat SPEED = new Stat(1, "SPEED", "스피드", 0xFFE4002B, 240f);
    public static final Stat BOLD = new Stat(2, "BOLD", "담대함", 0xFFFFB000, 640f);
    public static final Stat MANNER = new Stat(3, "MANNER", "매너", 0xFF3DDC84, 1400f);
    public static final Stat STAMINA = new Stat(4, "STAMINA", "체력", 0xFFB388FF, 1540f);
    private static final Stat[] ALL = {TECH, SPEED, BOLD, MANNER, STAMINA};

    private final int ordinal;
    private final String key;
    public final String label;
    public final int color;
    /** 등급(0~100) 계산용 기준 포인트. 스탯마다 얻기 쉬운 정도가 달라 기준을 따로 둔다. */
    public final float gradeScale;

    private Stat(int ordinal, String key, String label, int color, float gradeScale) {
        this.ordinal = ordinal;
        this.key = key;
        this.label = label;
        this.color = color;
        this.gradeScale = gradeScale;
    }

    public static Stat[] values() { return ALL.clone(); }
    public int ordinal() { return ordinal; }
    public String name() { return key; }

    /** 누적 포인트를 0~100 등급으로 환산(체감형 곡선). */
    public int grade(int points) {
        double g = 100.0 * (1.0 - Math.exp(-points / (double) gradeScale));
        return (int) Math.round(Math.min(100.0, g));
    }
}
