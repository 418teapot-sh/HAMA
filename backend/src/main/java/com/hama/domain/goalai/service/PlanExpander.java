package com.hama.domain.goalai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goalai.dto.PlanDetail;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 가 준 작은 플랜(마일스톤별 "매주 할 일")을 주간 목표와 날짜별 투두로 펼칩니다.
 * AI 출력 토큰 상한 때문에 날짜 계산과 반복은 서버가 합니다.
 */
final class PlanExpander {

    static final int MAX_MILESTONES = 6;
    static final int MAX_WEEKLY_TASKS = 4;
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
     * @return 펼칠 마일스톤이 하나도 없으면 null
     */
    static Expanded expand(AiPlan plan, String defaultTitle, LocalDate goalStart, LocalDate planStart,
            LocalDate planEnd) {
        if (plan == null || plan.milestones() == null) {
            return null;
        }
        List<AiMilestone> source = plan.milestones().stream()
                .filter(m -> m != null && hasTasks(m)).limit(MAX_MILESTONES).toList();
        List<PlanDetail.MilestonePlan> milestones = new ArrayList<>();
        int[] budget = {MAX_TODOS};
        LocalDate cursor = planStart;
        for (int i = 0; i < source.size() && !cursor.isAfter(planEnd); i++) {
            AiMilestone milestone = source.get(i);
            LocalDate end = i == source.size() - 1 ? planEnd : clamp(parse(milestone.endDate()), cursor, planEnd);
            String title = text(milestone.title(), 100, "단계 " + (i + 1));
            milestones.add(new PlanDetail.MilestonePlan(i + 1, title, cursor, end,
                    weeks(title, cursor, end, tasks(milestone), budget)));
            cursor = end.plusDays(1);
        }
        if (milestones.isEmpty()) {
            return null;
        }
        int total = 0;
        int minutes = 0;
        Set<LocalDate> days = new HashSet<>();
        for (PlanDetail.MilestonePlan milestone : milestones) {
            for (PlanDetail.WeekPlan week : milestone.weeks()) {
                for (PlanDetail.TodoPlan todo : week.todos()) {
                    total++;
                    minutes += todo.minutes();
                    days.add(todo.date());
                }
            }
        }
        return new Expanded(text(plan.title(), 50, defaultTitle), text(plan.summary(), 255, defaultTitle),
                new PlanDetail(goalStart, planEnd, milestones), total,
                days.isEmpty() ? 0 : Math.round((float) minutes / days.size()));
    }

    /** 마일스톤 기간을 7일씩 끊어 주간 목표로 만들고, 할 일마다 주 횟수만큼 그 주 안에 고르게 흩어 둡니다. */
    private static List<PlanDetail.WeekPlan> weeks(String milestoneTitle, LocalDate start, LocalDate end,
            List<AiTask> tasks, int[] budget) {
        List<PlanDetail.WeekPlan> weeks = new ArrayList<>();
        int seq = 1;
        for (LocalDate weekStart = start; !weekStart.isAfter(end); weekStart = weekStart.plusDays(7), seq++) {
            LocalDate weekEnd = weekStart.plusDays(6).isAfter(end) ? end : weekStart.plusDays(6);
            int length = (int) ChronoUnit.DAYS.between(weekStart, weekEnd) + 1;
            List<PlanDetail.TodoPlan> todos = new ArrayList<>();
            for (int t = 0; t < tasks.size(); t++) {
                AiTask task = tasks.get(t);
                int perWeek = Math.clamp(task.sessionsPerWeek() == null ? 1 : task.sessionsPerWeek(), 1, 7);
                // 7일보다 짧은 주(마일스톤 끝)는 길이에 비례해 횟수를 줄입니다.
                int sessions = Math.clamp(Math.round(perWeek * length / 7f), 1, length);
                int minutes = Math.clamp(task.minutes() == null ? 30 : task.minutes(), MIN_MINUTES, MAX_MINUTES);
                for (int s = 0; s < sessions && budget[0] > 0; s++, budget[0]--) {
                    // 할 일마다 시작 요일을 하나씩 밀어서 모든 할 일이 첫날에 몰리지 않게 합니다.
                    int day = (s * length / sessions + t) % length;
                    todos.add(new PlanDetail.TodoPlan(text(task.content(), 255, milestoneTitle),
                            weekStart.plusDays(day), minutes));
                }
            }
            todos.sort((a, b) -> a.date().compareTo(b.date()));
            String title = cut(milestoneTitle + " " + seq + "주차", 100);
            weeks.add(new PlanDetail.WeekPlan(seq, title, weekStart, weekEnd, todos));
        }
        return weeks;
    }

    private static boolean hasTasks(AiMilestone milestone) {
        return !tasks(milestone).isEmpty();
    }

    private static List<AiTask> tasks(AiMilestone milestone) {
        if (milestone.weeklyTasks() == null) {
            return List.of();
        }
        return milestone.weeklyTasks().stream()
                .filter(t -> t != null && t.content() != null && !t.content().isBlank())
                .limit(MAX_WEEKLY_TASKS).toList();
    }

    private static LocalDate parse(String date) {
        try {
            return date == null ? null : LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** AI 가 날짜를 안 줬거나 범위를 벗어나면 남은 기간 안으로 맞춥니다. 안 준 경우는 한 주로 둡니다. */
    private static LocalDate clamp(LocalDate date, LocalDate min, LocalDate max) {
        LocalDate value = date == null ? min.plusDays(6) : date;
        if (value.isBefore(min)) {
            return min;
        }
        return value.isAfter(max) ? max : value;
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
    record AiPlan(String variant, String title, String summary, List<AiMilestone> milestones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiMilestone(String title, String startDate, String endDate, List<AiTask> weeklyTasks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiTask(String content, Integer sessionsPerWeek, Integer minutes) {
    }
}
