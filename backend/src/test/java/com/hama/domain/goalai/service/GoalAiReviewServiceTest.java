package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.goalai.service.GoalAiReviewService.ReviewAnswer;
import com.hama.global.exception.BusinessException;
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
    void 앞뒤_공백은_지우고_500자가_넘으면_자른다() {
        assertThat(GoalAiReviewService.cut("  좋아요  ")).isEqualTo("좋아요");
        String tooLong = "가".repeat(600);
        String cut = GoalAiReviewService.cut(tooLong);
        assertThat(cut).hasSize(500);
        assertThat(cut).isEqualTo("가".repeat(500));
    }
}
