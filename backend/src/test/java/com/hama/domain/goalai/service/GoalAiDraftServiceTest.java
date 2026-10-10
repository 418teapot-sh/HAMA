package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.goalai.dto.GoalDraft;
import com.hama.domain.goalai.dto.RealityResult;
import com.hama.domain.goalai.service.GoalAiDraftService.RealityAnswer;
import com.hama.domain.goalai.service.GoalAiDraftService.SuggestionAnswer;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalAiDraftServiceTest {

    private static final GoalDraft DRAFT = new GoalDraft("토익 850", "토익 점수", "점", new BigDecimal("700.00"),
            new BigDecimal("850.00"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 30),
            new BigDecimal("10.0"), null);

    @Test
    void 가용시간은_기간_일수를_주로_나눠_주간_가용시간을_곱한다() {
        assertThat(GoalAiDraftService.availableHours(DRAFT)).isEqualTo(130);
        assertThat(GoalAiDraftService.availableHours(new GoalDraft("토익", null, null, null, null,
                DRAFT.startDate(), DRAFT.endDate(), null, null))).isNull();
    }

    @Test
    void 부족한_시간은_서버가_계산하고_음수가_되지_않는다() {
        RealityResult short_ = GoalAiDraftService.toResult(
                new RealityAnswer("challenging", "빠듯해요.", 180, false, List.of()), DRAFT, 129);
        assertThat(short_.verdict()).isEqualTo("CHALLENGING");
        assertThat(short_.comparison()).isEqualTo(new RealityResult.Comparison(180, 129, 51));

        RealityResult enough = GoalAiDraftService.toResult(
                new RealityAnswer("FEASIBLE", "충분해요.", 100, null, null), DRAFT, 129);
        assertThat(enough.comparison().gapHours()).isZero();
        assertThat(enough.suggestions()).isEmpty();
    }

    @Test
    void 판정이나_코멘트가_없으면_502() {
        assertThatThrownBy(() -> GoalAiDraftService.toResult(
                new RealityAnswer("MAYBE", "글쎄요", 10, false, null), DRAFT, 129))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GoalAiDraftService.toResult(
                new RealityAnswer("FEASIBLE", " ", 10, false, null), DRAFT, 129))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 바로_적용할_수_없는_제안은_버린다() {
        List<RealityResult.Suggestion> suggestions = GoalAiDraftService.suggestions(List.of(
                new SuggestionAnswer("EXTEND_PERIOD", "기간을 1월 말까지", Map.of("endDate", "2027-01-31")),
                new SuggestionAnswer("EXTEND_PERIOD", "중복", Map.of("endDate", "2027-02-28")),
                new SuggestionAnswer("LOWER_TARGET", "목표를 800점으로", Map.of("targetValue", 800)),
                new SuggestionAnswer("MORE_TIME", "주 5시간으로 줄이기", Map.of("weeklyAvailableHours", 5)),
                new SuggestionAnswer("MORE_TIME", "주 14시간", Map.of("weeklyAvailableHours", "14", "endDate", "x")),
                new SuggestionAnswer("UNKNOWN", "?", Map.of("x", 1))), DRAFT);

        assertThat(suggestions).extracting(RealityResult.Suggestion::type)
                .containsExactly("EXTEND_PERIOD", "LOWER_TARGET", "MORE_TIME");
        assertThat(suggestions.get(0).patch()).isEqualTo(Map.of("endDate", "2027-01-31"));
        assertThat(suggestions.get(1).patch()).isEqualTo(Map.of("targetValue", new BigDecimal("800.00")));
        assertThat(suggestions.get(2).patch()).isEqualTo(Map.of("weeklyAvailableHours", new BigDecimal("14.0")));
    }

    @Test
    void 종료일을_당기거나_형식이_틀린_기간_연장은_버린다() {
        assertThat(GoalAiDraftService.suggestions(List.of(
                new SuggestionAnswer("EXTEND_PERIOD", "당기기", Map.of("endDate", "2026-12-01")),
                new SuggestionAnswer("EXTEND_PERIOD", "형식", Map.of("endDate", "내년")),
                new SuggestionAnswer("EXTEND_PERIOD", "1년 넘김", Map.of("endDate", "2027-10-01"))), DRAFT)).isEmpty();
    }
}
