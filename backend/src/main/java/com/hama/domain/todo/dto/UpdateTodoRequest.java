package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "투두 부분 수정. 생략한 필드는 유지하며 content의 null은 허용하지 않습니다.")
public class UpdateTodoRequest extends TodoTimePatch {

    @Pattern(regexp = "(?s).*\\S.*", message = "내용은 공백일 수 없습니다.")
    @Size(max = 255, message = "내용은 255자 이하입니다.")
    @Schema(example = "단어 60개 암기")
    @JsonDeserialize(using = TodoStringDeserializer.class)
    private String content;

    @JsonSetter(value = "content", nulls = Nulls.FAIL)
    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}
