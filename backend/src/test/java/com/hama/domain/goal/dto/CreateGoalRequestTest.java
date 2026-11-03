package com.hama.domain.goal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateGoalRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "기", "ㄱㄱ", "!!", "365", "  가  "})
    void 뜻을_알_수_없는_제목과_고정_일정은_거절한다(String text) {
        assertThat(fields(request(text, "한국사 공부"))).contains("title");
        assertThat(fields(request("3개월 안에 토익 850점", text))).contains("fixedSchedules[0].content");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT", "알바", "토익 850", "3km 달리기", "365일 도전"})
    void 한글이나_영문_글자가_2자_이상이면_통과한다(String text) {
        assertThat(fields(request(text, text))).isEmpty();
    }

    @Test
    void 고정_일정이_없으면_제목만_본다() {
        CreateGoalRequest request = new CreateGoalRequest("매일 러닝 30분", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), null);
        assertThat(fields(request)).isEmpty();
    }

    private static CreateGoalRequest request(String title, String fixedContent) {
        return new CreateGoalRequest(title, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                List.of(new CreateGoalRequest.FixedSchedule(DayOfWeek.MONDAY, fixedContent)));
    }

    private static List<String> fields(CreateGoalRequest request) {
        Set<ConstraintViolation<CreateGoalRequest>> violations = VALIDATOR.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
    }
}
