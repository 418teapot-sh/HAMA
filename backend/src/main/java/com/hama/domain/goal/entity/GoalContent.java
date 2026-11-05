package com.hama.domain.goal.entity;

import java.time.LocalDate;

/**
 * 사용자가 입력·수정할 수 있는 목표 정보. 생성과 수정이 같은 검증을 타도록 한 묶음으로 넘깁니다.
 */
public record GoalContent(
        String title,
        String currentStatus,
        LocalDate startDate,
        LocalDate endDate
) {

    /**
     * 목표 제목·고정 일정 내용 검증용. 한글 음절이나 영문자가 2자 이상 있어야 합니다. "4", "기", "ㄱㄱ" 처럼
     * 뜻을 알 수 없는 입력으로는 AI 가 구체적인 할 일을 만들지 못하고 범용 문구로 채웁니다.
     */
    public static final String MEANINGFUL_TEXT = "(?s)(?:.*?[가-힣A-Za-z]){2}.*";
}
