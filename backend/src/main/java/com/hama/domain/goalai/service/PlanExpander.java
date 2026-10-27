package com.hama.domain.goalai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goalai.dto.PlanDetail;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 가 준 작은 플랜(반복 할 일 목록, 마일스톤 없음)을 날짜별 투두로 펼칩니다. 7일씩 끊어 그 주 안에 횟수만큼
 * 고르게 흩어 둡니다. AI 출력 토큰 상한 때문에 날짜 계산과 반복은 서버가 합니다.
 */
final class PlanExpander {

    static final int MAX_TASKS = 5;
    static final int MAX_TODOS = 2000;
    static final int MIN_MINUTES = 10;
    static final int MAX_MINUTES = 240;

    private PlanExpander() {
    }

    record Expanded(String title, String summary, PlanDetail detail, int totalTodos, int avgDailyMinutes) {
    }

    /**
     * @param planStart 투두를 두기 시작할 날(목표 시작일과 오늘 중 늦은 날)
     * @param planEnd   목표 종료일
     * @return 펼칠 할 일이 하나도 없으면 null
     */
    static Expanded expand(AiPlan plan, String defaultTitle, LocalDate goalStart, LocalDate planStart,
            LocalDate planEnd) {
        if (plan == null || plan.tasks() == null) {
            return null;
        }
        List<AiTask> source = plan.tasks().stream()
                .filter(t -> t != null && t.content() != null && !t.content().isBlank())
                .limit(MAX_TASKS).toList();
        if (source.isEmpty()) {
            return null;
        }
        List<PlanDetail.TaskTemplate> templates = source.stream()
                .map(t -> new PlanDetail.TaskTemplate(text(t.content(), 255, defaultTitle),
                        Math.clamp(t.timesPerWeek() == null ? 1 : t.timesPerWeek(), 1, 7),
                        Math.clamp(t.minutes() == null ? 30 : t.minutes(), MIN_MINUTES, MAX_MINUTES)))
                .toList();

        List<PlanDetail.TodoPlan> todos = new ArrayList<>();
        int[] budget = {MAX_TODOS};
        for (LocalDate weekStart = planStart; !weekStart.isAfter(planEnd) && budget[0] > 0;
                weekStart = weekStart.plusDays(7)) {
            LocalDate weekEnd = weekStart.plusDays(6).isAfter(planEnd) ? planEnd : weekStart.plusDays(6);
            int length = (int) ChronoUnit.DAYS.between(weekStart, weekEnd) + 1;
            for (int t = 0; t < templates.size(); t++) {
                PlanDetail.TaskTemplate task = templates.get(t);
                // 7일보다 짧은 주(목표 끝)는 길이에 비례해 횟수를 줄입니다.
                int sessions = Math.clamp(Math.round(task.timesPerWeek() * length / 7f), 1, length);
                for (int s = 0; s < sessions && budget[0] > 0; s++, budget[0]--) {
                    // 할 일마다 시작 요일을 하나씩 밀어서 모든 할 일이 첫날에 몰리지 않게 합니다.
                    int day = (s * length / sessions + t) % length;
                    todos.add(new PlanDetail.TodoPlan(task.content(), weekStart.plusDays(day), task.minutes()));
                }
            }
        }
        if (todos.isEmpty()) {
            return null;
        }
        todos.sort(Comparator.comparing(PlanDetail.TodoPlan::date));

        int total = todos.size();
        int minutesSum = todos.stream().mapToInt(PlanDetail.TodoPlan::minutes).sum();
        Set<LocalDate> days = new HashSet<>();
        todos.forEach(todo -> days.add(todo.date()));

        return new Expanded(text(plan.title(), 50, defaultTitle), text(plan.summary(), 255, defaultTitle),
                new PlanDetail(goalStart, planEnd, planStart, templates, todos), total,
                days.isEmpty() ? 0 : Math.round((float) minutesSum / days.size()));
    }

    private static String text(String value, int max, String fallback) {
        return value == null || value.isBlank() ? cut(fallback, max) : cut(value.strip(), max);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiPlans(List<AiPlan> plans) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiPlan(String variant, String title, String summary, List<AiTask> tasks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiTask(String content, Integer timesPerWeek, Integer minutes) {
    }
}
