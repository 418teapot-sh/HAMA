package com.hama.domain.checkin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CheckinResponses {
    private CheckinResponses() {}
    public record Created(Long checkinId) {}
    public record History(String unit, PageResponse<Item> checkins) {}
    public record Item(Long checkinId, CheckinType type, BigDecimal value, String note, Boolean achieved,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime checkedAt) {
        public static Item from(GoalCheckin checkin) {
            return new Item(checkin.getId(), checkin.getType(), checkin.getValue(), checkin.getNote(),
                    checkin.getAchieved(), checkin.getCheckedAt());
        }
    }
}
