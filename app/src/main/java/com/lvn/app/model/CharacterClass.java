package com.lvn.app.model;

/** 상위 두 스탯 조합으로 정해지는 캐릭터. */
public final class CharacterClass {
    public final String name;
    public final String desc;

    private CharacterClass(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public static CharacterClass of(Stat first, Stat second) {
        Stat a = first, b = second;
        if (a.ordinal() > b.ordinal()) { Stat t = a; a = b; b = t; }
        if (a == Stat.TECH) {
            if (b == Stat.SPEED) return new CharacterClass("그리드의 제왕", "변속과 속도, 모두를 지배하는 챔피언 레이서");
            if (b == Stat.BOLD) return new CharacterClass("새벽 고개의 전설", "처음 보는 산길도 리듬으로 타는 야간의 은둔 고수");
            return new CharacterClass("마에스트로", "기어 하나, 깜빡이 하나까지 지휘하듯 다루는 운전 장인");
        }
        if (a == Stat.SPEED) {
            if (b == Stat.BOLD) return new CharacterClass("스트리트 패밀리 보스", "어떤 길이든 크루를 이끄는 언더그라운드 레이서");
            if (b == Stat.MANNER) return new CharacterClass("스트리트 패밀리 보스", "빠르지만 매너로 존중받는 크루의 리더");
            return new CharacterClass("스트리트 패밀리 보스", "지치지 않는 엔진, 크루를 이끄는 리더");
        }
        if (a == Stat.BOLD) return new CharacterClass("지도 밖의 개척자", "아직 아무도 칠하지 않은 길을 찾아 떠나는 탐험가");
        return new CharacterClass("철인 크루저", "먼 길도 여유롭게, 안전하게 완주하는 장거리의 강자");
    }
}
