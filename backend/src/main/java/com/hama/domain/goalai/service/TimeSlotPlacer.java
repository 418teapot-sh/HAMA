package com.hama.domain.goalai.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 날짜별 바쁜 구간(분 단위, [start, end))을 들고 있다가 빈 시간에 투두를 하나씩 끼워 넣습니다.
 * 넣은 투두도 바쁜 구간이 되어 다음 투두와 겹치지 않습니다.
 */
public final class TimeSlotPlacer {

    static final int DAY_START = 9 * 60;
    static final int DAY_END = 22 * 60;
    static final int STEP = 10;

    public record Interval(int start, int end) {
    }

    public record Slot(LocalTime start, LocalTime end) {
    }

    private final Map<LocalDate, List<Interval>> busy;

    public TimeSlotPlacer(Map<LocalDate, List<Interval>> busy) {
        this.busy = new HashMap<>();
        busy.forEach((date, intervals) -> this.busy.put(date, new ArrayList<>(intervals)));
    }

    /** 09:00~22:00 안에서 minutes 가 들어가는 첫 빈 시간(10분 단위 시작). 없으면 null. */
    public Slot place(LocalDate date, int minutes) {
        List<Interval> intervals = busy.computeIfAbsent(date, d -> new ArrayList<>());
        intervals.sort(Comparator.comparingInt(Interval::start));
        int start = DAY_START;
        for (Interval interval : intervals) {
            if (start + minutes <= interval.start()) {
                break;
            }
            if (interval.end() > start) {
                start = ceilToStep(interval.end());
            }
        }
        if (start + minutes > DAY_END) {
            return null;
        }
        intervals.add(new Interval(start, start + minutes));
        return new Slot(LocalTime.of(start / 60, start % 60), LocalTime.of((start + minutes) / 60, (start + minutes) % 60));
    }

    /** 이미 정해진 시간을 그대로 쓸 수 있으면 차지하고 true, 다른 구간과 겹치면 false. */
    public boolean reserve(LocalDate date, LocalTime start, LocalTime end) {
        int s = start.getHour() * 60 + start.getMinute();
        int e = end.getHour() * 60 + end.getMinute();
        List<Interval> intervals = busy.computeIfAbsent(date, d -> new ArrayList<>());
        for (Interval interval : intervals) {
            if (interval.start() < e && s < interval.end()) {
                return false;
            }
        }
        intervals.add(new Interval(s, e));
        return true;
    }

    private static int ceilToStep(int minute) {
        return (minute + STEP - 1) / STEP * STEP;
    }
}
