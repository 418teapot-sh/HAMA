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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "metric_name", length = 50)
    private String metricName;

    @Column(length = 20)
    private String unit;

    @Column(name = "start_value", precision = 10, scale = 2)
    private BigDecimal startValue;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "weekly_available_hours", precision = 4, scale = 1)
    private BigDecimal weeklyAvailableHours;

    @Column(name = "current_level", columnDefinition = "TEXT")
    private String currentLevel;

    @Column(name = "reality_verdict", length = 20)
    private String realityVerdict;

    @Column(name = "reality_comment", columnDefinition = "TEXT")
    private String realityComment;

    @Column(name = "result_status", length = 20)
    private String resultStatus;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** PLANNING / IN_PROGRESS 만 저장됩니다. 응답에는 {@link #effectiveStatus} 를 쓰세요. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GoalStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** AI 없이 직접 입력한 목표. 바로 진행 중으로 시작합니다. */
    public static Goal createDirect(Long userId, GoalContent content, LocalDate today) {
        if (userId == null) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        validate(content, true, today);
        Goal goal = new Goal();
        goal.userId = userId;
        goal.status = GoalStatus.IN_PROGRESS;
        goal.apply(content);
        return goal;
    }

    /** AI 대화로 확정한 목표. 플랜을 고르기 전까지 PLANNING 입니다. */
    public static Goal createPlanning(Long userId, GoalContent content, String currentLevel,
            String realityVerdict, String realityComment, LocalDate today) {
        if (userId == null) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        validate(content, true, today);
        Goal goal = new Goal();
        goal.userId = userId;
        goal.status = GoalStatus.PLANNING;
        goal.apply(content);
        goal.currentLevel = currentLevel;
        goal.realityVerdict = realityVerdict;
        goal.realityComment = realityComment;
        return goal;
    }

    /** 저장하기 전 값(AI 초안 등)을 목표와 같은 규칙으로 검사합니다. 기간은 필수입니다. */
    public static void validateContent(GoalContent content, LocalDate today) {
        validate(content, true, today);
    }

    /** 수정 결과(기존 값과 병합한 전체 값)를 받습니다. 지난 목표는 409 입니다. */
    public void revise(GoalContent content, LocalDate today) {
        if (isPast(today)) {
            throw new BusinessException(GoalErrorCode.GOAL_NOT_EDITABLE);
        }
        validate(content, status == GoalStatus.IN_PROGRESS, today);
        apply(content);
    }

    /** 플랜을 적용해 진행 중으로 바꿉니다. 호출하는 쪽이 PLANNING 이고 기간이 있는지 먼저 확인합니다. */
    public void startPlan() {
        if (status != GoalStatus.PLANNING || startDate == null || endDate == null) {
            throw new IllegalStateException("PLANNING 이 아니거나 기간이 없는 목표는 플랜을 적용할 수 없습니다.");
        }
        this.status = GoalStatus.IN_PROGRESS;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 종료일이 오늘보다 이전이면 PAST. PLANNING(플랜 선택 전)은 종료일과 무관하게 PLANNING 입니다. */
    public GoalStatus effectiveStatus(LocalDate today) {
        return isPast(today) ? GoalStatus.PAST : status;
    }

    public boolean isPast(LocalDate today) {
        return status == GoalStatus.IN_PROGRESS && endDate != null && endDate.isBefore(today);
    }

    /** 진행 중인 목표만 삭제할 수 없습니다. PLANNING 은 AI 흐름을 중간에 그만둔 목표라 지울 수 있어야 합니다. */
    public boolean isDeletable(LocalDate today) {
        return status == GoalStatus.PLANNING || isPast(today);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public GoalContent content() {
        return new GoalContent(title, description, metricName, unit, startValue, targetValue,
                weeklyAvailableHours, startDate, endDate);
    }

    private void apply(GoalContent content) {
        this.title = content.title().strip();
        this.description = content.description();
        this.metricName = content.metricName();
        this.unit = content.unit();
        this.startValue = content.startValue();
        this.targetValue = content.targetValue();
        this.weeklyAvailableHours = content.weeklyAvailableHours();
        this.startDate = content.startDate();
        this.endDate = content.endDate();
    }

    private static void validate(GoalContent content, boolean datesRequired, LocalDate today) {
        if (content == null || content.title() == null || content.title().isBlank()
                || content.title().strip().length() > 100) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        if (content.description() != null
                && content.description().getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException(GoalErrorCode.GOAL_DESCRIPTION_TOO_LONG);
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
