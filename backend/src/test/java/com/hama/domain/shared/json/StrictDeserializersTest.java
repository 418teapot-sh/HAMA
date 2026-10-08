package com.hama.domain.shared.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.schedule.dto.CreateScheduleRequest;
import com.hama.domain.schedule.dto.UpdateScheduleRequest;
import com.hama.domain.todo.dto.CreateTodoRequest;
import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.todo.dto.UpdateTodoNoteRequest;
import com.hama.domain.todo.dto.UpdateTodoRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class StrictDeserializersTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 명세의_문자열_날짜_시간_boolean_일시를_그대로_읽는다() {
        CreateTodoRequest todo = mapper.readValue("""
                {"category":"TASK","content":"책 읽기","todoDate":"2028-02-29",
                 "startTime":"00:00","endTime":"23:59"}
                """, CreateTodoRequest.class);
        assertThat(todo.category()).isEqualTo("TASK");
        assertThat(todo.content()).isEqualTo("책 읽기");
        assertThat(todo.todoDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(todo.startTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(todo.endTime()).isEqualTo(LocalTime.of(23, 59));

        CreateScheduleRequest schedule = mapper.readValue("""
                {"type":"PERSONAL","title":"휴일","startAt":"2028-02-29T00:00:00",
                 "endAt":"2028-03-01T00:00:00","allDay":true}
                """, CreateScheduleRequest.class);
        assertThat(schedule.getStartAt()).isEqualTo(LocalDateTime.of(2028, 2, 29, 0, 0));
        assertThat(schedule.getEndAt()).isEqualTo(LocalDateTime.of(2028, 3, 1, 0, 0));
        assertThat(schedule.getAllDay()).isTrue();
        assertThat(mapper.readValue("{}", CreateScheduleRequest.class).getAllDay()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"content\":123}|문자열을 입력해야 합니다.",
        "{\"content\":true}|문자열을 입력해야 합니다.",
        "{\"content\":[]}|문자열을 입력해야 합니다.",
        "{\"content\":{}}|문자열을 입력해야 합니다.",
        "{\"todoDate\":20261003}|날짜는 YYYY-MM-DD 문자열이어야 합니다.",
        "{\"todoDate\":\"2026-2-03\"}|날짜는 YYYY-MM-DD 형식이어야 합니다.",
        "{\"todoDate\":\"2026-02-29\"}|유효한 YYYY-MM-DD 날짜여야 합니다.",
        "{\"startTime\":[9,0]}|시간은 HH:mm 문자열이어야 합니다.",
        "{\"startTime\":\"9:00\"}|시간은 HH:mm 형식이어야 합니다.",
        "{\"startTime\":\"09:00:00\"}|시간은 HH:mm 형식이어야 합니다.",
        "{\"startTime\":\"２３:００\"}|시간은 HH:mm 형식이어야 합니다.",
        "{\"startTime\":\"24:00\"}|유효한 HH:mm 시간이어야 합니다.",
        "{\"startTime\":\"12:60\"}|유효한 HH:mm 시간이어야 합니다."
    })
    void 투두의_잘못된_토큰과_형식은_기존_오류문구로_거절한다(String json, String message) {
        assertThatThrownBy(() -> mapper.readValue(json, CreateTodoRequest.class))
                .isInstanceOf(JacksonException.class).hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"title\":123}|문자열을 입력해야 합니다.",
        "{\"allDay\":\"false\"}|true 또는 false를 입력해야 합니다.",
        "{\"allDay\":0}|true 또는 false를 입력해야 합니다.",
        "{\"startAt\":[]}|일시는 YYYY-MM-DDTHH:mm:ss 문자열이어야 합니다.",
        "{\"startAt\":\"2026-10-03T09:00\"}|일시는 YYYY-MM-DDTHH:mm:ss 형식이어야 합니다.",
        "{\"startAt\":\"2026-10-03T09:00:00Z\"}|일시는 YYYY-MM-DDTHH:mm:ss 형식이어야 합니다.",
        "{\"startAt\":\"2026-10-03T09:00:00.123\"}|일시는 YYYY-MM-DDTHH:mm:ss 형식이어야 합니다.",
        "{\"startAt\":\"2026-02-29T09:00:00\"}|유효한 YYYY-MM-DDTHH:mm:ss 일시여야 합니다.",
        "{\"startAt\":\"2026-10-03T24:00:00\"}|유효한 YYYY-MM-DDTHH:mm:ss 일시여야 합니다."
    })
    void 일정의_자동형변환과_명세밖_일시는_기존_오류문구로_거절한다(String json, String message) {
        assertThatThrownBy(() -> mapper.readValue(json, CreateScheduleRequest.class))
                .isInstanceOf(JacksonException.class).hasMessageContaining(message);
    }

    @Test
    void 투두_PATCH의_생략_유지와_선택_null_삭제를_보존한다() {
        LocalTime original = LocalTime.of(9, 0);
        UpdateTodoRequest omitted = mapper.readValue("{}", UpdateTodoRequest.class);
        UpdateTodoRequest cleared = mapper.readValue("{\"startTime\":null}", UpdateTodoRequest.class);
        assertThat(omitted.startOr(original)).isEqualTo(original);
        assertThat(cleared.startOr(original)).isNull();
        assertThat(cleared.endOr(original)).isEqualTo(original);

        UpdateTodoNoteRequest noteOmitted = mapper.readValue("{}", UpdateTodoNoteRequest.class);
        UpdateTodoNoteRequest noteCleared = mapper.readValue("{\"statusNote\":null}", UpdateTodoNoteRequest.class);
        assertThat(noteOmitted.hasStatusNote()).isFalse();
        assertThat(noteCleared.hasStatusNote()).isTrue();
        assertThat(noteCleared.getStatusNote()).isNull();
        assertThatThrownBy(() -> mapper.readValue("{\"content\":null}", UpdateTodoRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"targetDate\":null}", PostponeTodoRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void 일정_PATCH의_생략_유지와_선택_null_삭제를_보존한다() {
        UpdateScheduleRequest omitted = mapper.readValue("{}", UpdateScheduleRequest.class);
        UpdateScheduleRequest cleared = mapper.readValue("{\"repeatRule\":null,\"memo\":null}", UpdateScheduleRequest.class);
        assertThat(omitted.repeatRuleOr("FREQ=DAILY")).isEqualTo("FREQ=DAILY");
        assertThat(omitted.memoOr("기존 메모")).isEqualTo("기존 메모");
        assertThat(cleared.repeatRuleOr("FREQ=DAILY")).isNull();
        assertThat(cleared.memoOr("기존 메모")).isNull();
        for (String requiredField : new String[]{"type", "title", "startAt", "endAt", "allDay"}) {
            assertThatThrownBy(() -> mapper.readValue("{\"" + requiredField + "\":null}", UpdateScheduleRequest.class))
                    .isInstanceOf(JacksonException.class);
        }
        assertThatThrownBy(() -> mapper.readValue("{\"allDay\":null}", CreateScheduleRequest.class))
                .isInstanceOf(JacksonException.class);
    }
}
