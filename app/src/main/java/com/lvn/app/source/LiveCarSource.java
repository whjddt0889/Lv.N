package com.lvn.app.source;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.lvn.app.engine.DriveSample;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 실차/에뮬레이터 차량 신호 수신.
 * Android Automotive 표준 CarPropertyManager 를 리플렉션으로 연결한다(차량 라이브러리가 없는 기기에서도 앱이 죽지 않게).
 * Pleos Vehicle SDK 정식 배포 후에는 이 클래스만 SDK 호출로 바꾸면 되도록 판정 엔진과 분리했다.
 * Virtual Console 에서 기어·속도·방향지시등·조향각을 바꾸면 그대로 들어온다.
 */
public final class LiveCarSource {
    private static final String TAG = "LvN.Live";

    // VHAL 표준 프로퍼티 ID
    static final int PERF_VEHICLE_SPEED = 0x11600207;
    static final int GEAR_SELECTION = 0x11400400;
    static final int CURRENT_GEAR = 0x11400401;
    static final int TURN_SIGNAL_STATE = 0x11400408;
    static final int NIGHT_MODE = 0x11200407;
    static final int PERF_STEERING_ANGLE = 0x11600209;
    static final int ENGINE_RPM = 0x11600305;
    static final int PARKING_BRAKE_ON = 0x11200402;

    public interface Sink { void onSample(DriveSample s); }

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Object car, mgr, callback;
    private Method unregister;
    private Sink sink;
    private long t0;
    private final DriveSample cur = new DriveSample();
    private String status = "연결 전";
    private int propsOk;

    public LiveCarSource(Context ctx) { this.ctx = ctx; }

    public String status() { return status; }

    public boolean start(Sink sink) {
        this.sink = sink;
        t0 = SystemClock.elapsedRealtime();
        cur.gearSel = 'P';
        cur.label = "실차 신호";
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            car = carCls.getMethod("createCar", Context.class).invoke(null, ctx);
            mgr = carCls.getMethod("getCarManager", String.class).invoke(car, "property");
            Class<?> cbCls = Class.forName("android.car.hardware.property.CarPropertyManager$CarPropertyEventCallback");
            InvocationHandler handler = (proxy, m, args) -> {
                String n = m.getName();
                if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                if ("equals".equals(n)) return proxy == args[0];
                if ("toString".equals(n)) return "LvNCallback";
                if ("onChangeEvent".equals(n) && args != null && args.length > 0) onValue(args[0]);
                return null;
            };
            callback = Proxy.newProxyInstance(cbCls.getClassLoader(), new Class<?>[]{cbCls}, handler);
            Method reg = mgr.getClass().getMethod("registerCallback", cbCls, int.class, float.class);
            unregister = mgr.getClass().getMethod("unregisterCallback", cbCls);
            int[] ids = {PERF_VEHICLE_SPEED, GEAR_SELECTION, CURRENT_GEAR, TURN_SIGNAL_STATE,
                    NIGHT_MODE, PERF_STEERING_ANGLE, ENGINE_RPM, PARKING_BRAKE_ON};
            propsOk = 0;
            for (int id : ids) {
                try {
                    Object ok = reg.invoke(mgr, callback, id, 10f);
                    if (Boolean.TRUE.equals(ok)) propsOk++;
                } catch (Throwable e) {
                    Log.w(TAG, "property 0x" + Integer.toHexString(id) + " 사용 불가: " + e.getCause());
                }
            }
            status = "차량 신호 연결됨 (" + propsOk + "/" + ids.length + " 항목)";
        } catch (Throwable e) {
            Log.w(TAG, "차량 API 연결 실패", e);
            status = "차량 API 없음 — 시나리오 재생으로 시연하세요";
            return false;
        }
        tick.run();
        return propsOk > 0;
    }

    private final Runnable tick = this::tickStep;

    private void tickStep() {
        if (sink == null) return;
        DriveSample s = cur.copy();
        s.tMs = SystemClock.elapsedRealtime() - t0;
        sink.onSample(s);
        h.postDelayed(tick, 100);
    }

    private void onValue(Object v) {
        try {
            int id = (Integer) v.getClass().getMethod("getPropertyId").invoke(v);
            Object val = v.getClass().getMethod("getValue").invoke(v);
            switch (id) {
                case PERF_VEHICLE_SPEED: cur.speedKmh = Math.abs(((Number) val).doubleValue()) * 3.6; break;
                case GEAR_SELECTION: applyGearSelection(((Number) val).intValue()); break;
                case CURRENT_GEAR: {
                    int g = gearNumber(((Number) val).intValue());
                    if (g > 0) cur.gear = g;
                    break;
                }
                case TURN_SIGNAL_STATE: {
                    int ts = ((Number) val).intValue();     // 0 NONE, 1 RIGHT, 2 LEFT
                    cur.turnSignal = ts == 2 ? 1 : ts == 1 ? 2 : 0;
                    break;
                }
                case NIGHT_MODE: cur.night = Boolean.TRUE.equals(val); break;
                case PERF_STEERING_ANGLE: cur.steerDeg = -((Number) val).doubleValue(); break; // VHAL: 왼쪽 음수
                case ENGINE_RPM: cur.rpm = (int) ((Number) val).floatValue(); break;
                case PARKING_BRAKE_ON: if (Boolean.TRUE.equals(val) && cur.speedKmh < 1) cur.gearSel = 'P'; break;
            }
        } catch (Throwable e) {
            Log.w(TAG, "값 해석 실패", e);
        }
    }

    /** VehicleGear: N=1, R=2, P=4, D=8, 1단=16, 2단=32 ... */
    private void applyGearSelection(int g) {
        if (g == 4) { cur.gearSel = 'P'; cur.gear = 0; }
        else if (g == 2) { cur.gearSel = 'R'; cur.gear = 0; }
        else if (g == 1) { cur.gearSel = 'N'; cur.gear = 0; }
        else if (g == 8) { cur.gearSel = 'D'; }
        else {
            int n = gearNumber(g);
            if (n > 0) { cur.gearSel = 'M'; cur.gear = n; }
        }
    }

    private static int gearNumber(int g) {
        for (int i = 1; i <= 9; i++) if (g == (16 << (i - 1))) return i;
        return 0;
    }

    public void stop() {
        sink = null;
        h.removeCallbacks(tick);
        try {
            if (mgr != null && callback != null) unregister.invoke(mgr, callback);
            if (car != null) car.getClass().getMethod("disconnect").invoke(car);
        } catch (Throwable ignored) { }
        car = null; mgr = null; callback = null;
    }
}
