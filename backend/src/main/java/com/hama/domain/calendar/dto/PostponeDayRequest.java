package com.hama.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

public class PostponeDayRequest {

    @JsonDeserialize(using = StrictDeserializers.Text.class)
    @Pattern(regexp = "NEXT_FREE_SLOT|NEXT_DAY")
    @Schema(description = "생략 시 NEXT_FREE_SLOT. NEXT_DAY는 첫 탐색일 안에서만 배치합니다.",
            allowableValues = {"NEXT_FREE_SLOT", "NEXT_DAY"}, defaultValue = "NEXT_FREE_SLOT")
    private String strategy = "NEXT_FREE_SLOT";

    @JsonSetter(value = "strategy", nulls = Nulls.FAIL)
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategy() {
        return strategy;
    }
}
