package com.lvn.app.engine;

/** 한 시점의 차량 상태. 실차(CarPropertyManager)든 시나리오 재생기든 같은 형식으로 판정 엔진에 들어간다. */
public final class DriveSample {
    public long tMs;            // 주행 시작 기준 시간
    public double speedKmh;
    public char gearSel = 'P';  // P R N D M(수동/패들)
    public int gear;            // 현재 단수 (0 = 알 수 없음)
    public int turnSignal;      // 0 없음, 1 좌, 2 우
    public double steerDeg;     // 왼쪽 + / 오른쪽 -
    public boolean brake;
    public boolean night;
    public boolean rain;
    public boolean newRoad;     // 처음 지나는 100m 격자
    public boolean circuit;     // 등록된 서킷 안
    public int rpm;             // 0 = 차량에서 못 받음 → 추정
    public String label;        // 시나리오 구간 이름 (시연 패널 표시용)
    public int etaPlannedMin;   // 시나리오: 출발 시 ETA (분, 0 = 없음)

    public DriveSample copy() {
        DriveSample s = new DriveSample();
        s.tMs = tMs; s.speedKmh = speedKmh; s.gearSel = gearSel; s.gear = gear; s.turnSignal = turnSignal;
        s.steerDeg = steerDeg; s.brake = brake; s.night = night; s.rain = rain; s.newRoad = newRoad;
        s.circuit = circuit; s.rpm = rpm; s.label = label; s.etaPlannedMin = etaPlannedMin;
        return s;
    }
}
