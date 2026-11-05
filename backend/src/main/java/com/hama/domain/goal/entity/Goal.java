package com.hama.domain.goal.entity;

import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.global.entity.BaseTimeEntity;
import com.hama.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "goal", indexes = @Index(name = "idx_goal_user_deleted", columnList = "user_id,deleted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal extends BaseTimeEntity {

    public static final int MAX_PERIOD_DAYS = 365;
    /** A/B 플랜 생성은 크레딧을 쓰지 않아 목표당 횟수로 AI 호출을 막습니다. */
    public static final int MAX_PLAN_GENERATIONS = 3;

    /** {@link #resultStatus} 에 저장하는 값. goals/ai 종료 리뷰(GoalAiReviewService)만 씁니다. */
    public static final String RESULT_ACHIEVED = "ACHIEVED";
    public static final String RESULT_MISSED = "MISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    /**
     * 목표를 만들 때의 상태(예: "토익 600점"). 생성 때 currentStatus 와 같은 값으로 저장하고 이후로 바꾸지 않습니다.
     * 종료 리뷰는 이 값을 시작점으로 비교합니다.
     */
    @Column(name = "initial_status", length = 200)
    private String initialStatus;

    /** 사용자가 직접 입력한 현재 상태(예: "토익 600점"). AI 없이 생성하므로 자유 텍스트로 받습니다. */
    @Column(name = "current_status", length = 200)
    private String currentStatus;

    @Column(name = "result_status", length = 20)
    private String resultStatus;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** PLANNING / IN_PROGRESS / COMPLETED 만 저장됩니다. 응답에는 {@link #effectiveStatus} 를 쓰세요. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GoalStatus status;

    /** COMPLETED 로 바꾼 시각. COMPLETED 가 아니면 null 입니다. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "auto_completed", nullable = false)
    private boolean autoCompleted;

    /** 성공한 A/B 플랜 생성 횟수. {@link #MAX_PLAN_GENERATIONS} 에 닿으면 더 만들 수 없습니다. */
    @Column(name = "plan_generation_count", nullable = false)
    private int planGenerationCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 목표를 직접 입력해 만듭니다. AI(goals/ai/plans)는 쓰지 않지만, 투두는 A/B 플랜을 골라야 생기므로
     * 플랜을 고르기 전까지는 PLANNING 입니다.
     */
    public static Goal create(Long userId, GoalContent content, LocalDate today) {
        if (userId == null) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        validate(content, true, today);
        Goal goal = new Goal();
        goal.userId = userId;
        goal.status = GoalStatus.PLANNING;
        goal.apply(content);
        goal.initialStatus = goal.currentStatus;
        return goal;
    }

    /** 수정 결과(기존 값과 병합한 전체 값)를 받습니다. 지난 목표와 완료한 목표는 409 입니다. */
    public void revise(GoalContent content, LocalDate today) {
        requireEditable(today);
        validate(content, status == GoalStatus.IN_PROGRESS, today);
        apply(content);
    }

    /** currentStatus 만 바꿉니다. 지난 목표와 완료한 목표는 revise() 와 같은 이유로 막습니다. */
    public void updateCurrentStatus(String currentStatus, LocalDate today) {
        requireEditable(today);
        this.currentStatus = normalizeStatus(currentStatus);
    }

    /** 진행 중(종료일 전)인 목표만 완료할 수 있습니다. 남은 AI 투두는 그대로 둡니다. */
    public void complete(LocalDateTime now) {
        if (status != GoalStatus.IN_PROGRESS || isPast(now.toLocalDate())) {
            throw new BusinessException(GoalErrorCode.GOAL_NOT_IN_PROGRESS);
        }
        this.status = GoalStatus.COMPLETED;
        this.completedAt = now;
        this.autoCompleted = false;
    }

    /** 모든 목표 투두 완료로 자동 종료한 경우만 완료 취소 시 다시 엽니다. */
    public void completeAutomatically(LocalDateTime now) {
        complete(now);
        this.autoCompleted = true;
    }

    public void reopenAutomaticallyCompleted() {
        if (status == GoalStatus.COMPLETED && autoCompleted) {
            status = GoalStatus.IN_PROGRESS;
            completedAt = null;
            autoCompleted = false;
        }
    }

    public boolean isCompleted() {
        return status == GoalStatus.COMPLETED;
    }

    /** 고정 일정을 보여줄 마지막 날. 완료한 목표는 종료일 전이라도 완료한 날까지입니다. */
    public LocalDate scheduleEndDate() {
        if (isCompleted() && completedAt != null && completedAt.toLocalDate().isBefore(endDate)) {
            return completedAt.toLocalDate();
        }
        return endDate;
    }

    private void requireEditable(LocalDate today) {
        if (isPast(today) || isCompleted()) {
            throw new BusinessException(GoalErrorCode.GOAL_NOT_EDITABLE);
        }
    }

    /** 플랜을 적용해 진행 중으로 바꿉니다. 호출하는 쪽이 PLANNING 이고 기간이 있는지 먼저 확인합니다. */
    public void startPlan() {
        if (status != GoalStatus.PLANNING || startDate == null || endDate == null) {
            throw new IllegalStateException("PLANNING 이 아니거나 기간이 없는 목표는 플랜을 적용할 수 없습니다.");
        }
        this.status = GoalStatus.IN_PROGRESS;
    }

    public boolean canGeneratePlan() {
        return planGenerationCount < MAX_PLAN_GENERATIONS;
    }

    public int remainingPlanGenerations() {
        return Math.max(MAX_PLAN_GENERATIONS - planGenerationCount, 0);
    }

    /** 플랜 생성이 성공해 저장할 때만 셉니다. AI 실패는 세지 않습니다. */
    public void countPlanGeneration() {
        this.planGenerationCount++;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 종료 리뷰(AI)가 판정한 달성 여부를 기록합니다. 다시 리뷰하면 덮어씁니다. */
    public void recordResult(boolean achieved) {
        this.resultStatus = achieved ? RESULT_ACHIEVED : RESULT_MISSED;
    }

    /** 종료일이 오늘보다 이전이면 PAST. PLANNING(플랜 선택 전)은 종료일과 무관하게 PLANNING 입니다. */
    public GoalStatus effectiveStatus(LocalDate today) {
        return isPast(today) ? GoalStatus.PAST : status;
    }

    public boolean isPast(LocalDate today) {
        return status == GoalStatus.IN_PROGRESS && endDate != null && endDate.isBefore(today);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public GoalContent content() {
        return new GoalContent(title, currentStatus, startDate, endDate);
    }

    private void apply(GoalContent content) {
        this.title = content.title().strip();
        this.currentStatus = normalizeStatus(content.currentStatus());
        this.startDate = content.startDate();
        this.endDate = content.endDate();
    }

    /** 공백만 보내면 "" 가 아니라 값이 없는 것(null)으로 저장합니다. */
    private static String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String stripped = status.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static void validate(GoalContent content, boolean datesRequired, LocalDate today) {
        if (content == null || content.title() == null || content.title().isBlank()
                || content.title().strip().length() > 100) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        LocalDate start = content.startDate();
        LocalDate end = content.endDate();
        if (!isStorableDate(start) || !isStorableDate(end)) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
        if (start == null || end == null) {
            if (datesRequired) {
                throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
            }
            return;
        }
        // 종료일을 과거로 두면 저장하는 순간 PAST 가 되어 다시는 고칠 수 없게 됩니다.
        if (start.isAfter(end) || end.isBefore(today)) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
        if (!withinMaxPeriod(start, end)) {
            throw new BusinessException(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
        }
    }

    /**
     * 목표 기간은 시작일 포함 최대 365일입니다. 플랜을 주 단위로 펼치므로 기간이 곧 메모리·저장량입니다.
     * 달력 1년(plusYears)은 윤일이 끼면 366일이 되어 시작일마다 상한이 달라지므로 일수로 셉니다.
     * 상한을 바꾸면 저장된 목표도 같은 기준으로 맞춰야 합니다(V13 마이그레이션 참고).
     */
    public static boolean withinMaxPeriod(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) < MAX_PERIOD_DAYS;
    }

    /** MySQL DATE 가 담을 수 있는 연도(1000~9999)만 받습니다. null 은 필수 여부를 따로 검사합니다. */
    private static boolean isStorableDate(LocalDate date) {
        return date == null || (date.getYear() >= 1000 && date.getYear() <= 9999);
    }
}
