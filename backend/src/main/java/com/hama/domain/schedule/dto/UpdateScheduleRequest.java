package com.hama.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import tools.jackson.databind.annotation.JsonDeserialize;

@Getter
@Schema(description = "시리즈 전체 부분 수정. 생략은 유지, repeatRule/memo의 null은 지우기. 나머지 필드의 null은 400.")
public class UpdateScheduleRequest {

    @Pattern(regexp = "FIXED|PERSONAL", message = "일정 타입은 FIXED 또는 PERSONAL입니다.")
    @JsonDeserialize(using = StrictDeserializers.Text.class)
    @Schema(allowableValues = {"FIXED", "PERSONAL"})
    private String type;

    @Pattern(regexp = "(?s).*\\S.*", message = "제목은 공백일 수 없습니다.")
    @Size(max = 100, message = "제목은 100자 이하입니다.")
    @JsonDeserialize(using = StrictDeserializers.Text.class)
    @Schema(example = "학원", maxLength = 100)
    private String title;

    @JsonDeserialize(using = StrictDeserializers.DateTime.class)
    @Schema(type = "string", example = "2026-10-05T19:00:00")
    private LocalDateTime startAt;

    @JsonDeserialize(using = StrictDeserializers.DateTime.class)
    @Schema(type = "string", example = "2026-10-05T21:00:00")
    private LocalDateTime endAt;

    @JsonDeserialize(using = StrictDeserializers.Bool.class)
    @Schema(description = "종일 일정은 양쪽 일시가 00:00, 종료는 마지막 날 다음 날입니다.")
    private Boolean allDay;

    @Size(max = 255, message = "반복 규칙은 255자 이하입니다.")
    @JsonDeserialize(using = StrictDeserializers.Text.class)
    @Schema(description = "생성 API와 동일한 RRULE 부분집합. 생략 시 유지, null이면 반복 해제.", nullable = true)
    private String repeatRule;

    @JsonDeserialize(using = StrictDeserializers.Text.class)
    @Schema(description = "생략 시 유지, null이면 지움. UTF-8 기준 최대 65535바이트.", nullable = true)
    private String memo;

    @Getter(lombok.AccessLevel.NONE)
    private boolean repeatRulePresent;
    @Getter(lombok.AccessLevel.NONE)
    private boolean memoPresent;

    @JsonSetter(value = "type", nulls = Nulls.FAIL)
    public void setType(String type) {
        this.type = type;
    }

    @JsonSetter(value = "title", nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter(value = "startAt", nulls = Nulls.FAIL)
    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    @JsonSetter(value = "endAt", nulls = Nulls.FAIL)
    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    @JsonSetter(value = "allDay", nulls = Nulls.FAIL)
    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
    }

    @JsonSetter("repeatRule")
    public void setRepeatRule(String repeatRule) {
        this.repeatRule = repeatRule;
        this.repeatRulePresent = true;
    }

    @JsonSetter("memo")
    public void setMemo(String memo) {
        this.memo = memo;
        this.memoPresent = true;
    }

    public String repeatRuleOr(String original) {
        return repeatRulePresent ? repeatRule : original;
    }

    public String memoOr(String original) {
        return memoPresent ? memo : original;
    }
}
