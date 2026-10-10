package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "투두 메모. 생략 시 유지하며 빈 문자열 또는 null이면 삭제합니다.")
public class UpdateTodoNoteRequest {

    @Schema(example = "오늘은 집중이 잘 됐어요.")
    @JsonDeserialize(using = StrictDeserializers.Text.class)
    private String statusNote;
    private boolean statusNotePresent;

    @JsonSetter("statusNote")
    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote;
        this.statusNotePresent = true;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public boolean hasStatusNote() {
        return statusNotePresent;
    }
}
