package com.lvn.app.engine;

import com.lvn.app.model.Stat;

/** 판정 결과 하나. stat 이 null 이면 점수 없는 안내(컨디션 알림 등). */
public final class DriveEvent {
    public static final int SCORE = 0, BONUS = 1, INFO = 2, WARN = 3, RECORD = 4;

    public final long tMs;
    public final int kind;
    public final Stat stat;
    public final int points;
    public final String title;
    public final String voice;   // 음성 안내 문구 (null = 효과음만)

    public DriveEvent(long tMs, int kind, Stat stat, int points, String title, String voice) {
        this.tMs = tMs;
        this.kind = kind;
        this.stat = stat;
        this.points = points;
        this.title = title;
        this.voice = voice;
    }

    public String chip() {
        if (stat == null) return title;
        return title + "  " + stat.label + " +" + points;
    }
}
