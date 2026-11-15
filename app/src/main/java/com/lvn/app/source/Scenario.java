package com.lvn.app.source;

import com.lvn.app.engine.DriveSample;
import com.lvn.app.model.CarProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오 재생기용 주행 기록.
 * 실차·에뮬레이터에서 매번 똑같이 재현하기 어려운 상황(야간 산길, 빗길, 2시간 연속 운전, 서킷)을
 * 0.2초 간격의 차량 신호로 만들어 판정 엔진에 그대로 넣는다. 구간마다 재생 배속이 다르다.
 */
public final class Scenario {
    public static final int STEP_MS = 200;

    public final String id, title, subtitle;
    public final List<DriveSample> samples = new ArrayList<>();
    public final List<Float> rates = new ArrayList<>();
    public int etaActualMin;

    Scenario(String id, String title, String subtitle) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
    }

    public long durationMs() { return samples.isEmpty() ? 0 : samples.get(samples.size() - 1).tMs; }

    // ------------------------------------------------------------------
    static final class B {
        final Scenario sc;
        final CarProfile car;
        long t;
        double v;
        char sel = 'P';
        int gear = 0;
        boolean night, rain, newRoad, circuit, brake;
        String label = "";
        float rate = 1f;

        B(Scenario sc, CarProfile car) { this.sc = sc; this.car = car; }

        B label(String l) { label = l; return this; }
        B rate(float r) { rate = r; return this; }
        B night(boolean b) { night = b; return this; }
        B rain(boolean b) { rain = b; return this; }
        B newRoad(boolean b) { newRoad = b; return this; }
        B circuit(boolean b) { circuit = b; return this; }

        B select(char s) {
            sel = s;
            if (s == 'D') gear = car.autoGear(v);
            if (s == 'P' || s == 'N' || s == 'R') gear = 0;
            hold(0.6, 0, 0);
            return this;
        }

        B manual(int g) { sel = 'M'; gear = g; hold(0.4, 0, 0); return this; }

        /** 패들 변속. */
        B shift(int g) { gear = g; hold(0.4, 0, 0); return this; }

        interface Steer { double at(double u); }

        private void add(double steer, int signal) {
            DriveSample s = new DriveSample();
            s.tMs = t; s.speedKmh = Math.max(0, v); s.gearSel = sel;
            s.gear = sel == 'D' ? car.autoGear(v) : gear;
            if (sel == 'D') gear = s.gear;
            s.turnSignal = signal; s.steerDeg = steer; s.brake = brake;
            s.night = night; s.rain = rain; s.newRoad = newRoad; s.circuit = circuit; s.label = label;
            sc.samples.add(s);
            sc.rates.add(rate);
            t += STEP_MS;
        }

        /** sec 초 동안 속도를 target 까지 선형으로 바꾸며 주행. */
        B drive(double sec, double target) { return drive(sec, target, null, 0); }

        B drive(double sec, double target, Steer steer, int signal) {
            int n = Math.max(1, (int) Math.round(sec * 1000 / STEP_MS));
            double v0 = v;
            for (int i = 1; i <= n; i++) {
                double u = i / (double) n;
                v = v0 + (target - v0) * u;
                add(steer == null ? 0 : steer.at(u), signal);
            }
            return this;
        }

        B hold(double sec, double steer, int signal) {
            int n = Math.max(1, (int) Math.round(sec * 1000 / STEP_MS));
            for (int i = 0; i < n; i++) add(steer, signal);
            return this;
        }

        B brakeTo(double sec, double target) { brake = true; drive(sec, target); brake = false; return this; }

        /** 차선변경: (깜빡이 2초) → 5초 S자 조향(최대 6°). dir +1 좌, -1 우. */
        B laneChange(int dir, boolean signal) {
            int sig = signal ? (dir > 0 ? 1 : 2) : 0;
            if (signal) hold(2.0, 0, sig);
            final int d = dir;
            int n = (int) (5000 / STEP_MS);
            for (int i = 1; i <= n; i++) {
                double u = i / (double) n;
                add(d * 6.0 * Math.sin(2 * Math.PI * u), u < 0.6 ? sig : 0);
            }
            hold(1.5, 0, 0);
            return this;
        }

        /** 코너/교차로 회전. */
        B corner(int dir, double peakDeg, double sec, double target) {
            final double p = peakDeg * dir;
            return drive(sec, target, u -> p * Math.sin(Math.PI * u), 0);
        }

        /** 완만한 커브(차선변경 오판정 방지 확인용). */
        B gentleCurve(int dir, double sec, double target) {
            final double p = 8.0 * dir;
            return drive(sec, target, u -> p * Math.sin(Math.PI * u), 0);
        }

        B park() {
            if (v > 0) brakeTo(Math.max(3, v / 4), 0);
            select('P');
            hold(1.0, 0, 0);
            return this;
        }

        Scenario build(int etaSlackMin) {
            int actual = (int) Math.ceil(t / 60000.0);
            sc.etaActualMin = actual;
            if (etaSlackMin > 0) for (DriveSample s : sc.samples) s.etaPlannedMin = actual + etaSlackMin;
            return sc;
        }
    }

    // ------------------------------------------------------------------
    public static List<Scenario> all(CarProfile car) {
        List<Scenario> l = new ArrayList<>();
        l.add(commute(car));
        l.add(fridayNight(car));
        l.add(trackDay(car));
        return l;
    }

    /** 1. 평일 아침 출근 — 일상 운전에서도 매너·스피드(ETA)가 조금씩 쌓인다. */
    public static Scenario commute(CarProfile car) {
        B b = new B(new Scenario("commute", "평일 아침 출근", "도심 + 도시고속도로 · D 모드 · 내비 ETA 연동"), car);
        b.label("시동 · 출발 준비").rate(2f).hold(2, 0, 0).select('D');
        b.label("아파트 단지 → 큰길").rate(3f).drive(12, 30).drive(20, 50).brakeTo(8, 0);
        b.label("신호 대기").rate(20f).hold(45, 0, 0);
        b.label("도심 주행").rate(25f).drive(15, 60).hold(150, 0, 0).drive(10, 40).hold(60, 0, 0).drive(10, 70);
        b.label("도시고속도로 진입").rate(3f).drive(10, 80).laneChange(1, true);
        b.label("도시고속도로 정속 주행").rate(60f).hold(300, 0, 0);
        b.label("도시고속도로 · 완만한 커브").rate(3f).gentleCurve(1, 6, 80).hold(3, 0, 0).laneChange(-1, true);
        b.label("도시고속도로 정속 주행").rate(60f).hold(360, 0, 0);
        b.label("끼어들기 차선변경").rate(3f).laneChange(-1, false);
        b.label("도시고속도로 정속 주행").rate(60f).hold(120, 0, 0);
        b.label("출구 → 교차로 우회전").rate(3f).drive(12, 35).corner(-1, 40, 5, 25);
        b.label("회사 앞 도심").rate(30f).drive(10, 50).hold(120, 0, 0);
        b.label("주차").rate(3f).park();
        return b.build(2);
    }

    /** 2. 금요일 밤 드라이브 — SPORT 모드·패들 변속, 처음 가는 산길, 비, 2시간 연속 운전 알림. */
    public static Scenario fridayNight(CarProfile car) {
        B b = new B(new Scenario("friday", "금요일 밤 드라이브", "야간 · 고속도로 → 처음 가는 산길(패들) · 비 · 졸음쉼터"), car);
        b.night(true);
        b.label("퇴근 후 출발").rate(2f).hold(2, 0, 0).select('D');
        b.label("도심 빠져나가기").rate(60f).drive(20, 60).hold(240, 0, 0).brakeTo(10, 0).hold(40, 0, 0).drive(15, 70).hold(240, 0, 0);
        b.label("고속도로 합류").rate(3f).drive(12, 100).laneChange(1, true);
        b.label("고속도로 장거리 주행").rate(400f).hold(3000, 0, 0);
        b.label("고속도로 추월").rate(3f).laneChange(1, true).hold(4, 0, 0).laneChange(-1, true);
        b.label("고속도로 장거리 주행").rate(400f).hold(2860, 0, 0);
        b.label("IC 진출 · 산길 입구").rate(4f).drive(15, 65);

        b.label("SPORT 모드 · 패들 시프트 전환").rate(2f).manual(4);
        b.newRoad(true);
        for (int i = 0; i < 6; i++) {
            if (i == 1) b.rain(true);
            String road = "처음 가는 산길" + (b.rain ? " · 비" : "") + " (코너 " + (i + 1) + "/6)";
            b.label(road).rate(16f).hold(32, 0, 0);                 // 4단 적정 회전수 유지
            b.label(road + " · 다운시프트").rate(2f).shift(3).drive(3.2, 50);
            b.corner(i % 2 == 0 ? 1 : -1, 40, 4, 45);                 // 코너
            b.label(road + " · 탈출 가속").drive(8, 80).shift(4);       // 업시프트
            if (i == 3) {
                b.label("직선 구간 · 무리한 다운시프트").drive(4, 100).shift(2).shift(4).drive(6, 70);
            }
            b.label(road).rate(16f).gentleCurve(i % 2 == 0 ? -1 : 1, 6, 70).hold(55, 0, 0).drive(4, 65);
        }
        b.rain(false).newRoad(false);
        b.label("산길 종료 · D 모드").rate(3f).select('D').drive(10, 90);
        b.label("졸음쉼터 진입").rate(4f).brakeTo(15, 0).select('P');
        b.label("졸음쉼터 휴식 16분").rate(300f).hold(960, 0, 0);
        b.label("귀가").rate(3f).select('D').drive(12, 90);
        b.label("귀가 고속도로").rate(200f).hold(900, 0, 0);
        b.label("집 도착 · 주차").rate(4f).drive(15, 30).park();
        return b.build(0);
    }

    /** 3. 서킷 트랙데이 — 공도에서는 거의 오르지 않는 스피드를 서킷에서 내 차 기록으로 쌓는다. */
    public static Scenario trackDay(CarProfile car) {
        B b = new B(new Scenario("track", "서킷 트랙데이", "등록 서킷 안 · 제로백 · 최고 속도 · 랩"), car);
        b.circuit(true);
        b.label("피트 출발 준비 · 런치 컨트롤").rate(1f).hold(2, 0, 0).manual(1).hold(1.5, 0, 0);
        b.label("제로백 측정").rate(1f).drive(1.9, 55).shift(2).drive(1.7, 85).shift(3).drive(1.1, 102).drive(3, 140).shift(4);
        for (int lap = 1; lap <= 2; lap++) {
            String l = "랩 " + lap + "/2";
            b.label(l + " · 메인 스트레이트").rate(2.5f).drive(5, 165).shift(5).drive(6, 205).shift(6).drive(3, 212);
            b.label(l + " · 1번 코너 하드 브레이킹").brakeTo(3.5, 95).shift(5).hold(0.6, 0, 0).shift(4).hold(0.6, 0, 0).shift(3);
            b.corner(-1, 55, 4, 85);
            b.label(l + " · 섹터 2").drive(3, 115).shift(4).drive(4, 160).brakeTo(2.5, 110).shift(3).corner(1, 45, 3.5, 100);
            b.label(l + " · 섹터 3 연속 코너").drive(3, 120).corner(-1, 35, 3, 110).corner(1, 35, 3, 105).shift(4).drive(6, 165);
        }
        b.label("쿨다운 랩").rate(10f).brakeTo(5, 80).shift(3).drive(60, 70).circuit(false);
        b.label("피트 인").rate(2f).drive(8, 20).park();
        return b.build(0);
    }
}
