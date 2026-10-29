package com.hama.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.hama.domain.goal.entity.GoalContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;

@Schema(description = """
        목표 부분 수정 (제목·기간만). 생략한 필드는 기존 값을 유지하고, null 을 보내면 지웁니다. title 은 null 이어도 유지합니다.
        currentStatus 는 여기서 바꿀 수 없습니다. PATCH /api/v1/goals/{goalId}/current-status 를 쓰세요.
        """)
public class UpdateGoalRequest {

    @Getter
    @Pattern(regexp = "(?s).*\\S.*", message = "목표 제목은 공백일 수 없습니다.")
    @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
    @Schema(example = "3개월 안에 토익 850점", maxLength = 100, description = "생략하거나 null 이면 유지")
    private String title;

    @Getter
    @Schema(description = "생략 시 유지, null 이면 지움(진행 중인 목표는 400)")
    private LocalDate startDate;

    @Getter
    @Schema(example = "2026-12-31", description = "오늘(KST) 이후여야 합니다. 생략 시 유지, null 이면 지움(진행 중인 목표는 400)")
    private LocalDate endDate;

    private boolean startDatePresent;
    private boolean endDatePresent;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDatePresent = true;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    public GoalContent mergeInto(GoalContent current) {
        return new GoalContent(
                title == null ? current.title() : title,
                current.currentStatus(),
                startDatePresent ? startDate : current.startDate(),
                endDatePresent ? endDate : current.endDate());
    }
}
