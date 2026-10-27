package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * goal_plan.detail_json 의 모양. goalStartDate·goalEndDate 는 플랜을 만들 때의 목표 기간으로,
 * 그 뒤 목표 기간이 바뀌었으면 선택할 수 없습니다. planStartDate 는 todos 를 두기 시작한 날(목표 시작일과
 * 플랜을 만든 날 중 늦은 날)로, 선택하는 시점에 이미 지났으면 다시 만들어야 합니다.
 * tasks 는 플랜 목록 조회 때 보여줄 반복 할 일(마일스톤 없이 평평하게), todos 는 선택 때 실제로 만들 날짜별 투두입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanDetail(LocalDate goalStartDate, LocalDate goalEndDate, LocalDate planStartDate,
        List<TaskTemplate> tasks, List<TodoPlan> todos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskTemplate(String content, int timesPerWeek, int minutes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TodoPlan(String content, LocalDate date, int minutes) {
    }
}
