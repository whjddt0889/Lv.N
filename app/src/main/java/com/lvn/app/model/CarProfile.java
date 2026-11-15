package com.lvn.app.model;

/**
 * 차종별 기어비·종감속비·타이어 둘레.
 * RPM 을 직접 받을 수 없을 때 "속도 × 기어비 × 종감속비 ÷ 타이어 둘레" 로 엔진 회전수를 추정하는 데 쓴다.
 * 값은 공개 제원 기반 근사치이며, 실차 데이터로 보정이 필요하다.
 */
public final class CarProfile {
    public final String id;
    public final String name;
    public final String trans;
    public final double[] ratios;
    public final double finalDrive;
    public final double tireCircM;
    public final int redline;

    private CarProfile(String id, String name, String trans, double[] ratios, double finalDrive, double tireCircM, int redline) {
        this.id = id;
        this.name = name;
        this.trans = trans;
        this.ratios = ratios;
        this.finalDrive = finalDrive;
        this.tireCircM = tireCircM;
        this.redline = redline;
    }

    public static final CarProfile[] ALL = {
            new CarProfile("avante_n_dct", "아반떼 N", "8단 습식 DCT",
                    new double[]{3.437, 2.333, 1.636, 1.217, 1.000, 0.811, 0.677, 0.565}, 4.0, 2.055, 6750),
            new CarProfile("avante_n_mt", "아반떼 N", "6단 수동",
                    new double[]{3.308, 2.130, 1.483, 1.139, 0.949, 0.816}, 4.188, 2.055, 6750),
            new CarProfile("kona_n_dct", "코나 N", "8단 습식 DCT",
                    new double[]{3.437, 2.333, 1.636, 1.217, 1.000, 0.811, 0.677, 0.565}, 4.0, 2.107, 6750),
            new CarProfile("tucson_nline", "투싼 N 라인", "8단 자동",
                    new double[]{4.715, 2.773, 1.836, 1.437, 1.172, 1.000, 0.837, 0.645}, 3.32, 2.328, 6300),
    };

    public static CarProfile byId(String id) {
        for (CarProfile p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }

    public int gearCount() { return ratios.length; }

    /** 속도(km/h)와 단수로 엔진 회전수(rpm)를 추정. */
    public int estimateRpm(double speedKmh, int gear) {
        if (gear < 1 || gear > ratios.length) return 0;
        double wheelRpm = (speedKmh * 1000.0 / 60.0) / tireCircM;
        int rpm = (int) Math.round(wheelRpm * ratios[gear - 1] * finalDrive);
        return Math.max(rpm, speedKmh < 1 ? 800 : 0);
    }

    /** D 모드 자동 변속을 흉내 낼 때 쓰는 속도별 단수. */
    public int autoGear(double speedKmh) {
        int g = 1;
        for (int i = 1; i <= ratios.length; i++) {
            if (estimateRpm(speedKmh, i) >= 1600) g = i; else break;
        }
        return Math.max(1, g);
    }

    public String label() { return name + " · " + trans; }
}
