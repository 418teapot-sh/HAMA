package com.hama.domain.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Be3TimeConfigTest {

    @Test
    void BE3_시계는_한국시간대로_오늘날짜를_계산한다() {
        Clock clock = new Be3TimeConfig().be3Clock();
        assertThat(clock.getZone()).isEqualTo(Be3Time.KST);
        Clock fixed = Clock.fixed(Instant.parse("2026-10-03T15:05:06Z"), clock.getZone());
        assertThat(LocalDate.now(fixed)).isEqualTo(LocalDate.of(2026, 10, 4));
    }
}
