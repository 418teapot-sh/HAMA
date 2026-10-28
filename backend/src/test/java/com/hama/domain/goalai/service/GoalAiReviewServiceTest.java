package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.domain.goalai.service.GoalAiReviewService.ReviewAnswer;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalAiReviewServiceTest {

    @Test
    void achieved가_판정한_그대로_돌아온다() {
        assertThat(GoalAiReviewService.validate(new ReviewAnswer(true, "700에서 860으로.", "축하해요!"))).isTrue();
        assertThat(GoalAiReviewService.validate(new ReviewAnswer(false, "700에서 780으로.", "힘내요!"))).isFalse();
    }

    @Test
    void achieved_comparison_message_중_하나라도_없으면_502() {
        assertThatThrownBy(() -> GoalAiReviewService.validate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GoalAiReviewService.validate(new ReviewAnswer(null, "비교", "멘트")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GoalAiReviewService.validate(new ReviewAnswer(true, " ", "멘트")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GoalAiReviewService.validate(new ReviewAnswer(true, "비교", "")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 멘트는_줄바꿈_없는_한_줄로_만들고_40자가_넘으면_자른다() {
        assertThat(GoalAiReviewService.oneLine("  좋아요  ")).isEqualTo("좋아요");
        assertThat(GoalAiReviewService.oneLine("처음보다\n100점   올랐어요!\r\n")).isEqualTo("처음보다 100점 올랐어요!");
        assertThat(GoalAiReviewService.oneLine("가".repeat(60))).isEqualTo("가".repeat(40));
        assertThat(GoalAiReviewService.oneLine("가".repeat(39) + " 나다")).isEqualTo("가".repeat(39));
    }

    @Test
    void 이모지는_반으로_잘리지_않는다() {
        String cut = GoalAiReviewService.oneLine("가".repeat(39) + "🎉🎉");
        assertThat(cut).isEqualTo("가".repeat(39) + "🎉");
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(40);
    }

    @Test
    void MID_가_많으면_최근_N개만_남기고_START_END_는_항상_남긴다() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 0, 0);
        List<GoalCheckin> all = new ArrayList<>();
        all.add(checkin(CheckinType.START, base.minusDays(30)));
        for (int i = 0; i < 25; i++) {
            all.add(checkin(CheckinType.MID, base.minusDays(29 - i)));
        }
        all.add(checkin(CheckinType.END, base));

        List<GoalCheckin> limited = GoalAiReviewService.limitMid(all, 20);

        assertThat(limited).hasSize(22);
        assertThat(limited.get(0).getType()).isEqualTo(CheckinType.START);
        assertThat(limited.get(limited.size() - 1).getType()).isEqualTo(CheckinType.END);
        assertThat(limited).filteredOn(c -> c.getType() == CheckinType.MID).hasSize(20)
                .containsExactlyElementsOf(all.subList(6, 26));
    }

    @Test
    void MID_가_제한_이하면_그대로_둔다() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 0, 0);
        List<GoalCheckin> all = List.of(checkin(CheckinType.START, base.minusDays(1)), checkin(CheckinType.MID, base));
        assertThat(GoalAiReviewService.limitMid(all, 20)).isEqualTo(all);
    }

    private static GoalCheckin checkin(CheckinType type, LocalDateTime checkedAt) {
        return GoalCheckin.create(1L, type, null, null, null, checkedAt, LocalDateTime.of(2026, 10, 2, 0, 0));
    }
}
