package com.hama.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.domain.checkin.exception.CheckinErrorCode;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GoalCheckinTest {
    @Test
    void DTO_검증없이_호출해도_null_수정은_거절하고_기존값을_보존한다() {
        GoalCheckin checkin = GoalCheckin.create(1L, CheckinType.END, null, null, false, null,
                LocalDateTime.of(2026, 10, 11, 12, 0));

        assertThatThrownBy(() -> checkin.updateAchievement(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CheckinErrorCode.CHECKIN_INVALID_INPUT));
        assertThat(checkin.getAchieved()).isFalse();
    }
}
