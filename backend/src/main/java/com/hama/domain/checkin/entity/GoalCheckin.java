package com.hama.domain.checkin.entity;

import com.hama.domain.checkin.exception.CheckinErrorCode;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "goal_checkin", indexes = @Index(name = "idx_checkin_goal_time", columnList = "goal_id,checked_at,checkin_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalCheckin extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkin_id")
    private Long id;
    @Column(name = "goal_id", nullable = false)
    private Long goalId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CheckinType type;
    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;
    @Column(columnDefinition = "TEXT")
    private String note;
    private Boolean achieved;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    public static GoalCheckin create(Long goalId, CheckinType type, BigDecimal value, String note,
            Boolean achieved, LocalDateTime checkedAt, LocalDateTime now) {
        if (goalId == null || goalId <= 0 || type == null || (type != CheckinType.END && achieved != null)
                || (value != null && (value.scale() > 2 || value.abs().compareTo(new BigDecimal("99999999.99")) > 0))
                || (note != null && note.getBytes(StandardCharsets.UTF_8).length > 65535)) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_INVALID_INPUT);
        }
        LocalDateTime time = checkedAt == null ? now : checkedAt;
        if (time == null || now == null || time.getYear() < 1000 || time.getYear() > 9999 || time.getNano() != 0 || time.isAfter(now)) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_INVALID_TIME);
        }
        GoalCheckin checkin = new GoalCheckin();
        checkin.goalId = goalId;
        checkin.type = type;
        checkin.value = value;
        checkin.note = note;
        checkin.achieved = achieved;
        checkin.checkedAt = time;
        return checkin;
    }

    public void updateAchievement(Boolean achieved) {
        if (type != CheckinType.END) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_NOT_END);
        }
        if (achieved == null) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_INVALID_INPUT);
        }
        this.achieved = achieved;
    }
}
