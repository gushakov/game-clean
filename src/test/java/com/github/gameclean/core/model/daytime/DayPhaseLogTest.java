package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DayPhaseLog} — the construction gate (watermark never below the {@code -1} sentinel, version
 * never negative), the {@code initial} factory, the {@code isPending} query, the monotonic
 * {@code announceThrough} advance (which carries the optimistic-locking version), and the deliberate exclusion
 * of that version from value equality.
 */
class DayPhaseLogTest {

    @Test
    void initial_has_nothing_announced_at_version_zero() {
        assertThat(DayPhaseLog.initial().getAnnouncedThroughHour()).isEqualTo(DayPhaseLog.NONE);
        assertThat(DayPhaseLog.initial().getVersion()).isZero();
    }

    @Test
    void rejects_a_watermark_below_the_sentinel() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhaseLog(-2, 0));
    }

    @Test
    void rejects_a_negative_version() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhaseLog(6, -1));
    }

    @Test
    void every_non_negative_hour_is_pending_for_a_fresh_log() {
        DayPhaseLog log = DayPhaseLog.initial();
        assertThat(log.isPending(0)).isTrue();
        assertThat(log.isPending(6)).isTrue();
    }

    @Test
    void an_announced_hour_is_no_longer_pending_but_a_later_one_is() {
        DayPhaseLog log = new DayPhaseLog(6, 3);
        assertThat(log.isPending(6)).isFalse();
        assertThat(log.isPending(5)).isFalse();
        assertThat(log.isPending(7)).isTrue();
    }

    @Test
    void isPending_rejects_a_negative_hour() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DayPhaseLog.initial().isPending(-1));
    }

    @Test
    void announceThrough_advances_the_watermark_and_carries_the_version() {
        DayPhaseLog advanced = new DayPhaseLog(6, 3).announceThrough(18);
        assertThat(advanced.getAnnouncedThroughHour()).isEqualTo(18);
        assertThat(advanced.getVersion()).isEqualTo(3);   // version is carried for the optimistic check
    }

    @Test
    void announceThrough_rejects_a_non_advancing_hour() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DayPhaseLog(6, 0).announceThrough(6));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DayPhaseLog(6, 0).announceThrough(5));
    }

    @Test
    void value_equality_ignores_the_version() {
        // The version is opaque concurrency metadata, not part of the aggregate's value.
        assertThat(new DayPhaseLog(6, 0)).isEqualTo(new DayPhaseLog(6, 7));
        assertThat(new DayPhaseLog(6, 0)).isNotEqualTo(new DayPhaseLog(7, 0));
    }
}
