package com.hama.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 목표를 만들 때 함께 받는 "이미 바쁜 요일" 맥락입니다(예: 월요일 - 한국사 공부). 시간 정보가 없어 실제 캘린더(Schedule)를
 * 막지는 않지만 해당 목표의 플랜과 자동 이동·재배치에서 제외합니다. 목표가 지워지면 goalId 로만 이어져 있어 함께 조회되지
 * 않을 뿐, 별도로 지우지 않아도 됩니다(탈퇴 때는 goal_id 로 직접 지웁니다).
 */
@Entity
@Table(name = "goal_fixed_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalFixedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false, length = 100)
    private String content;

    public static GoalFixedSchedule create(Long goalId, DayOfWeek dayOfWeek, String content) {
        GoalFixedSchedule schedule = new GoalFixedSchedule();
        schedule.goalId = goalId;
        schedule.dayOfWeek = dayOfWeek;
        schedule.content = content.strip();
        return schedule;
    }
}
