package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * goal_plan.detail_json 의 모양. goalStartDate·goalEndDate 는 플랜을 만들 때의 목표 기간으로,
 * 그 뒤 목표 기간이 바뀌었으면 선택할 수 없습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanDetail(LocalDate goalStartDate, LocalDate goalEndDate, List<MilestonePlan> milestones) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MilestonePlan(int seq, String title, LocalDate startDate, LocalDate endDate, List<WeekPlan> weeks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeekPlan(int seq, String title, LocalDate startDate, LocalDate endDate, List<TodoPlan> todos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TodoPlan(String content, LocalDate date, int minutes) {
    }
}
