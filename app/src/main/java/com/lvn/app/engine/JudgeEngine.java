package com.lvn.app.engine;

import com.lvn.app.model.CarProfile;
import com.lvn.app.model.Profile;
import com.lvn.app.model.Stat;

import java.util.ArrayList;
import java.util.List;

/**
 * Lv.N 판정 엔진.
 * 원칙: "방법이 아니라 결과" — 결과가 매끄러우면 점수, 실수는 감점 없이 점수만 안 준다.
 * 급가속·급제동 기준은 한국교통안전공단 DTG 위험운전행동 기준(급가속 11km/h/s, 급감속 7.5km/h/s)을 따른다.
 */
public final class JudgeEngine {

    public interface Listener { void onEvent(DriveEvent e); }

    // 판정 기준값
    static final double HARSH_ACCEL = 11.0;      // km/h per s
    static final double HARSH_DECEL = -7.5;      // km/h per s
    static final long SHIFT_COOLDOWN = 3000;     // 패들 연타 방지
    static final long SHIFT_ROUNDTRIP_MS = 10000; // 10초 안에 같은 단수를 오르내리는 왕복 변속은 제외
    static final int SHIFTS_PER_POINT = 10;      // 적정 회전수 변속 10회마다 +1
    static final long GEAR_HOLD_MS = 10 * 60 * 1000L;   // 적정 단수 10분 유지 +1
    static final long SMOOTH_D_MS = 30 * 60 * 1000L;    // D 모드: 급가속·급제동 없이 30분 +1
    static final double STAMINA_KM = 5.0;        // 누적 주행거리 5km마다 체력 +1
    static final long WEAVE_WINDOW = 10 * 60 * 1000L;   // 10분 안에
    static final int WEAVE_COUNT = 3;            // 차선변경 3회 이상이면 잦은 차선변경(칼치기 방지) → 점수 없음
    static final double LC_MIN_SPEED = 40;       // 차선변경 판정 최저 속도
    static final double LC_START_DEG = 3, LC_MAX_DEG = 15, LC_END_DEG = 1.5;
    static final long LC_MIN_MS = 3000, LC_MAX_MS = 8000;
    static final long TEN_MIN = 10 * 60 * 1000L;
    static final long TWO_HOURS = 2 * 60 * 60 * 1000L;
    static final long REST_RESET = 15 * 60 * 1000L;

    public static final class Trip {
        public final int[] earned = new int[Stat.values().length];
        public final List<DriveEvent> events = new ArrayList<>();
        public double distanceKm;
        public long durationMs;
        public double maxSpeed;
        public int harshAccel, harshBrake;
        public int laneChanges, signaledLaneChanges;
        public int scoredShifts, overRevs;
        public long nightMs, rainMs;
        public double newRoadKm;
        public boolean circuit;
        public double circuitTopSpeed;
        public double zeroTo100;    // 이번 주행 최고 기록(초)
        public int etaPlannedMin;
        public boolean conditionAlerted;
        public int total() { int s = 0; for (int e : earned) s += e; return s; }
    }

    private final CarProfile car;
    private final Listener listener;
    private Trip trip = new Trip();
    private DriveSample prev;

    // 가속도 계산용 1초 버퍼
    private final long[] bufT = new long[64];
    private final double[] bufV = new double[64];
    private int bufN, bufHead;
    private long lastHarshT = -100000;

    // 변속
    private long lastScoredShiftT = -100000;
    private long gearHoldMs, smoothDMs;
    private int smoothShiftCount;
    private long lastShiftT = -100000;
    private int lastShiftFrom, lastShiftTo;
    private final List<Long> laneChangeTimes = new ArrayList<>();

    // 차선변경
    private int lcState;          // 0 idle, 1 첫 조향, 2 되돌림
    private int lcDir;            // +1 좌, -1 우
    private long lcStart;
    private double lcMax;
    private long lcZeroSince = -1;
    private boolean lcBlocked;    // 큰 조향(회전) 후 핸들이 풀릴 때까지 판정 중지
    private long lastSignalLeft = -100000, lastSignalRight = -100000;

    // 누적
    private long nightAcc, rainAcc;
    private double newRoadAcc;
    private int staminaHundreds, staminaUnits;
    private final double staminaCarryKm;
    private long contMs, stopMs;

    // 서킷
    private boolean launchArmed;
    private long launchT;

    public JudgeEngine(CarProfile car, Listener listener) { this(car, listener, 0); }

    /** staminaCarryKm: 지난 주행에서 체력 점수로 바뀌지 않고 남은 거리(누적 주행거리 기준 체력). */
    public JudgeEngine(CarProfile car, Listener listener, double staminaCarryKm) {
        this.car = car;
        this.listener = listener;
        this.staminaCarryKm = staminaCarryKm;
    }

    /** 다음 주행으로 넘길 남은 거리. */
    public double staminaLeftoverKm() {
        double total = staminaCarryKm + trip.distanceKm;
        return total - Math.floor(total / STAMINA_KM) * STAMINA_KM;
    }

    public Trip trip() { return trip; }

    /** 연속 운전 시간 기반 컨디션 (1.0 = 최상). */
    public float condition() {
        double c = 1.0 - (contMs / (double) TWO_HOURS) * 0.8;
        return (float) Math.max(0.05, Math.min(1.0, c));
    }

    public long continuousMs() { return contMs; }

    public int rpmOf(DriveSample s) {
        if (s.rpm > 0) return s.rpm;
        return car.estimateRpm(s.speedKmh, s.gear);
    }

    private void emit(DriveEvent e) {
        trip.events.add(e);
        if (e.stat != null) trip.earned[e.stat.ordinal()] += e.points;
        if (listener != null) listener.onEvent(e);
    }

    // 컨디션 20% 이하 또는 내구도 0%이면 경험치 절반 (감점이 아니라 획득량 감소)
    static final float LOW_CONDITION = 0.2f;
    private boolean durabilityZero;
    private final double[] halfCarry = new double[Stat.values().length];

    public void setDurabilityZero(boolean z) { durabilityZero = z; }

    public boolean halved() { return durabilityZero || condition() <= LOW_CONDITION; }

    private void score(long t, Stat st, int pts, String title, String voice) {
        if (halved()) {
            int k = st.ordinal();
            halfCarry[k] += pts * 0.5;
            int eff = (int) Math.floor(halfCarry[k]);
            halfCarry[k] -= eff;
            String why = durabilityZero ? "내구도 0%" : "컨디션 낮음";
            if (eff <= 0) {
                emit(new DriveEvent(t, DriveEvent.INFO, null, 0, title + " (" + why + " · 경험치 절반)", null));
                return;
            }
            emit(new DriveEvent(t, DriveEvent.SCORE, st, eff, title + " (" + why + " · 경험치 절반)", voice));
            return;
        }
        emit(new DriveEvent(t, DriveEvent.SCORE, st, pts, title, voice));
    }

    public void process(DriveSample s) {
        if (prev == null) {
            prev = s.copy();
            trip.etaPlannedMin = s.etaPlannedMin;
            pushSpeed(s.tMs, s.speedKmh);
            return;
        }
        long dt = s.tMs - prev.tMs;
        if (dt <= 0) return;
        if (s.etaPlannedMin > 0) trip.etaPlannedMin = s.etaPlannedMin;

        double distDelta = s.speedKmh * dt / 3_600_000.0;
        trip.distanceKm += distDelta;
        trip.durationMs += dt;
        trip.maxSpeed = Math.max(trip.maxSpeed, s.speedKmh);

        pushSpeed(s.tMs, s.speedKmh);
        double accel = accelKmhPerSec(s.tMs);
        checkHarsh(s, accel);
        checkShift(s);
        checkGearHold(s, dt);
        checkSmoothD(s, dt);
        checkLaneChange(s);
        accumulate(s, dt, distDelta);
        checkCondition(s, dt);
        checkCircuit(s);

        prev = s.copy();
    }

    // ---------- 가속도 ----------
    private void pushSpeed(long t, double v) {
        bufT[bufHead] = t;
        bufV[bufHead] = v;
        bufHead = (bufHead + 1) % bufT.length;
        if (bufN < bufT.length) bufN++;
    }

    /** 약 1초 전 속도와 비교한 가속도 (km/h/s). */
    private double accelKmhPerSec(long now) {
        long bestT = -1;
        double bestV = 0;
        for (int i = 0; i < bufN; i++) {
            int idx = (bufHead - 1 - i + bufT.length) % bufT.length;
            if (now - bufT[idx] >= 1000) { bestT = bufT[idx]; bestV = bufV[idx]; break; }
        }
        if (bestT < 0) return 0;
        int last = (bufHead - 1 + bufT.length) % bufT.length;
        return (bufV[last] - bestV) / ((now - bestT) / 1000.0);
    }

    private void checkHarsh(DriveSample s, double a) {
        if (s.circuit) return;                     // 서킷에서는 급가속이 곧 실력
        if (s.tMs - lastHarshT < 3000) return;
        if (a >= HARSH_ACCEL && s.speedKmh >= 6) {
            lastHarshT = s.tMs;
            smoothDMs = 0;
            trip.harshAccel++;
            emit(new DriveEvent(s.tMs, DriveEvent.WARN, null, 0,
                    "급가속 감지 · 이번 주행 스피드 보너스 제외", null));
        } else if (a <= HARSH_DECEL && prev.speedKmh >= 10) {
            lastHarshT = s.tMs;
            smoothDMs = 0;
            trip.harshBrake++;
            emit(new DriveEvent(s.tMs, DriveEvent.WARN, null, 0,
                    "급제동 감지 · 이번 주행 스피드 보너스 제외", null));
        }
    }

    // ---------- 테크닉: 변속 ----------
    private void checkShift(DriveSample s) {
        if (s.gearSel != 'M' || prev.gearSel != 'M') return;
        if (s.gear == prev.gear || s.gear <= 0 || prev.gear <= 0) return;
        boolean up = s.gear > prev.gear;
        int before = rpmOf(prev);
        int after = car.estimateRpm(s.speedKmh, s.gear);
        if (s.rpm > 0) after = s.rpm;
        boolean recentHarsh = s.tMs - lastHarshT < 1500;
        boolean cooled = s.tMs - lastScoredShiftT >= SHIFT_COOLDOWN;
        // 점수를 노린 왕복 변속(예: 4→3→4를 10초 안에) 제외
        boolean roundTrip = s.tMs - lastShiftT < SHIFT_ROUNDTRIP_MS && prev.gear == lastShiftTo && s.gear == lastShiftFrom;
        lastShiftT = s.tMs; lastShiftFrom = prev.gear; lastShiftTo = s.gear;

        if (!up && after > car.redline - 200) {
            trip.overRevs++;
            emit(new DriveEvent(s.tMs, DriveEvent.WARN, null, 0,
                    "과회전 위험 다운시프트 · 점수 없음 (" + after + "rpm)", null));
        } else if (cooled && !recentHarsh && !roundTrip) {
            boolean ok = up ? (before >= 2800 && before <= car.redline && after >= 1500)
                            : (after >= 2000 && after <= car.redline - 200);
            if (ok) {
                lastScoredShiftT = s.tMs;
                trip.scoredShifts++;
                smoothShiftCount++;
                String name = up ? "적정 회전 업시프트 " + prev.gear + "→" + s.gear : "적정 회전 다운시프트 " + prev.gear + "→" + s.gear;
                if (smoothShiftCount % SHIFTS_PER_POINT == 0) {
                    score(s.tMs, Stat.TECH, 1, "적정 회전수 변속 " + SHIFTS_PER_POINT + "회 달성", null);
                } else {
                    emit(new DriveEvent(s.tMs, DriveEvent.INFO, null, 0,
                            name + " (" + (smoothShiftCount % SHIFTS_PER_POINT) + "/" + SHIFTS_PER_POINT + ")", null));
                }
            }
        }
    }

    /** 속도에 맞는 단수 선택: 수동 모드에서 적정 회전수(1,500~4,000rpm)를 10분 유지. */
    private void checkGearHold(DriveSample s, long dt) {
        if (s.gearSel != 'M' || s.speedKmh < 20) { gearHoldMs = 0; return; }
        int rpm = rpmOf(s);
        if (rpm >= 1500 && rpm <= 4000) gearHoldMs += dt; else gearHoldMs = 0;
        if (gearHoldMs >= GEAR_HOLD_MS) {
            gearHoldMs = 0;
            score(s.tMs, Stat.TECH, 1, "속도에 맞는 단수 10분 유지", null);
        }
    }

    /** D 모드 테크닉: 급가속·급제동 없이 30분 부드럽게 주행. */
    private void checkSmoothD(DriveSample s, long dt) {
        if (s.gearSel != 'D' || s.circuit || s.speedKmh < 1) return;
        smoothDMs += dt;
        if (smoothDMs >= SMOOTH_D_MS) {
            smoothDMs = 0;
            score(s.tMs, Stat.TECH, 1, "급가속·급제동 없이 30분 부드럽게", null);
        }
    }

    // ---------- 매너: 차선변경 ----------
    private void checkLaneChange(DriveSample s) {
        if (s.turnSignal == 1) lastSignalLeft = s.tMs;
        if (s.turnSignal == 2) lastSignalRight = s.tMs;
        double st = s.steerDeg, abs = Math.abs(st);

        if (lcBlocked) {                    // 교차로 회전·급커브: 핸들이 풀릴 때까지 판정 안 함
            if (abs < LC_END_DEG) lcBlocked = false;
            lcState = 0;
            return;
        }
        if (s.speedKmh < LC_MIN_SPEED) { lcState = 0; return; }
        if (abs > LC_MAX_DEG) { lcBlocked = true; lcState = 0; return; }

        switch (lcState) {
            case 0:
                if (abs >= LC_START_DEG) {
                    lcState = 1; lcDir = st > 0 ? 1 : -1; lcStart = s.tMs; lcMax = abs; lcZeroSince = -1;
                }
                break;
            case 1:
                lcMax = Math.max(lcMax, abs);
                if (s.tMs - lcStart > LC_MAX_MS) { lcState = 0; break; }       // 완만한 커브
                if (Math.signum(st) == -lcDir && abs >= 2) { lcState = 2; lcZeroSince = -1; }   // S자 되돌림
                else if (abs < 1.0) {                                              // 0° 통과 중이거나 한쪽 보정뿐
                    if (lcZeroSince < 0) lcZeroSince = s.tMs;
                    else if (s.tMs - lcZeroSince > 1200) lcState = 0;
                } else lcZeroSince = -1;
                break;
            case 2:
                if (s.tMs - lcStart > LC_MAX_MS) { lcState = 0; break; }
                if (abs < LC_END_DEG) {
                    long dur = s.tMs - lcStart;
                    lcState = 0;
                    if (dur >= LC_MIN_MS) onLaneChange(s.tMs, lcDir);
                }
                break;
        }
    }

    private void onLaneChange(long t, int dir) {
        trip.laneChanges++;
        long sig = dir > 0 ? lastSignalLeft : lastSignalRight;
        laneChangeTimes.add(t);
        int recent = 0;
        for (long x : laneChangeTimes) if (t - x <= WEAVE_WINDOW) recent++;
        if (sig >= lcStart - 3000 && recent >= WEAVE_COUNT) {
            trip.signaledLaneChanges++;
            emit(new DriveEvent(t, DriveEvent.INFO, null, 0,
                    "잦은 차선변경 구간 (10분 " + recent + "회) · 점수 없음", null));
        } else if (sig >= lcStart - 3000) {
            trip.signaledLaneChanges++;
            score(t, Stat.MANNER, 1, (dir > 0 ? "왼쪽" : "오른쪽") + " 깜빡이 차선변경", null);
        } else {
            emit(new DriveEvent(t, DriveEvent.INFO, null, 0,
                    (dir > 0 ? "왼쪽" : "오른쪽") + " 차선변경 (깜빡이 없음 · 감점 없음)", null));
        }
    }

    // ---------- 담대함·체력 누적 ----------
    private void accumulate(DriveSample s, long dt, double distDelta) {
        boolean moving = s.speedKmh > 0.5;
        if (s.night && moving) {
            trip.nightMs += dt; nightAcc += dt;
            while (nightAcc >= TEN_MIN) { nightAcc -= TEN_MIN; score(s.tMs, Stat.BOLD, 1, "야간 주행 10분", null); }
        }
        if (s.rain && moving) {
            trip.rainMs += dt; rainAcc += dt;
            while (rainAcc >= TEN_MIN) { rainAcc -= TEN_MIN; score(s.tMs, Stat.BOLD, 1, "빗길 주행 10분", null); }
        }
        if (s.newRoad) {
            trip.newRoadKm += distDelta; newRoadAcc += distDelta;
            while (newRoadAcc >= 2.0) { newRoadAcc -= 2.0; score(s.tMs, Stat.BOLD, 1, "처음 가는 길 2km", null); }
        }
        int units = (int) ((staminaCarryKm + trip.distanceKm) / STAMINA_KM);
        while (staminaUnits < units) {
            staminaUnits++;
            score(s.tMs, Stat.STAMINA, 1, "누적 주행 5km", null);
        }
        int hundreds = (int) (trip.distanceKm / 100);
        while (staminaHundreds < hundreds) {     // 점수 없는 장거리 안내
            staminaHundreds++;
            emit(new DriveEvent(s.tMs, DriveEvent.INFO, null, 0, "장거리 " + (staminaHundreds * 100) + "km 돌파",
                    "장거리 " + (staminaHundreds * 100) + "킬로미터 돌파"));
        }
    }

    // ---------- 컨디션 ----------
    private void checkCondition(DriveSample s, long dt) {
        if (s.speedKmh > 0.5) { stopMs = 0; contMs += dt; }
        else {
            stopMs += dt;
            if (stopMs < REST_RESET) contMs += dt;
            else if (contMs > 0) {
                contMs = 0;
                trip.conditionAlerted = false;
                emit(new DriveEvent(s.tMs, DriveEvent.INFO, null, 0, "충분히 쉬었어요 · 컨디션 회복", null));
            }
        }
        if (contMs >= TWO_HOURS && !trip.conditionAlerted) {
            trip.conditionAlerted = true;
            emit(new DriveEvent(s.tMs, DriveEvent.WARN, null, 0,
                    "2시간 연속 운전 · 쉬기 전까지 경험치 절반 · 다음 졸음쉼터에서 쉬어가기",
                    "두 시간 연속 운전했어요. 쉬기 전까지 경험치가 절반이에요. 다음 졸음쉼터에서 잠깐 쉬어 갈까요?"));
        }
    }

    // ---------- 서킷 ----------
    private void checkCircuit(DriveSample s) {
        if (!s.circuit) { launchArmed = false; return; }
        trip.circuit = true;
        trip.circuitTopSpeed = Math.max(trip.circuitTopSpeed, s.speedKmh);
        if (s.speedKmh < 1) { launchArmed = true; launchT = s.tMs; }
        else if (launchArmed && s.speedKmh >= 100) {
            launchArmed = false;
            double sec = (s.tMs - launchT) / 1000.0;
            if (trip.zeroTo100 == 0 || sec < trip.zeroTo100) trip.zeroTo100 = sec;
            emit(new DriveEvent(s.tMs, DriveEvent.INFO, null, 0,
                    String.format("제로백 %.1f초 측정", sec), null));
        }
    }

    /** 주행 종료: ETA·서킷 기록 같은 "결과" 보상을 정산한다. */
    public Trip finish(long t, int etaActualMin, Profile profile) {
        if (trip.circuit) {
            score(t, Stat.SPEED, 5, "트랙데이 완주", "트랙데이 완주");
            if (trip.circuitTopSpeed > profile.bestTopSpeed + 0.5) {
                emit(new DriveEvent(t, DriveEvent.RECORD, Stat.SPEED, 10,
                        String.format("내 차 최고 속도 신기록 %.0fkm/h", trip.circuitTopSpeed), "최고 속도 신기록"));
                profile.bestTopSpeed = trip.circuitTopSpeed;
            }
            if (trip.zeroTo100 > 0 && (profile.best0to100 == 0 || trip.zeroTo100 < profile.best0to100 - 0.05)) {
                emit(new DriveEvent(t, DriveEvent.RECORD, Stat.SPEED, 10,
                        String.format("제로백 신기록 %.1f초", trip.zeroTo100), "제로백 신기록"));
                profile.best0to100 = trip.zeroTo100;
            }
        } else if (trip.etaPlannedMin > 0 && etaActualMin > 0) {
            if (trip.harshAccel + trip.harshBrake > 0) {
                emit(new DriveEvent(t, DriveEvent.INFO, null, 0, "급가속·급제동이 있어 ETA 보너스 없음", null));
            } else if (etaActualMin <= trip.etaPlannedMin - 1 && profile.canEarnEtaToday()) {
                profile.etaCountToday++;
                score(t, Stat.SPEED, 1, "ETA보다 " + (trip.etaPlannedMin - etaActualMin) + "분 일찍 · 부드럽게 도착", null);
            }
        }
        return trip;
    }
}
