package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DayPhaseSchedule} — its construction gate (phase hours must be distinct), the empty
 * schedule (valid — the world simply announces nothing), and the {@code phaseBeginningAt} lookup.
 */
class DayPhaseScheduleTest {

    private static DayPhase phase(String name, int hour) {
        return new DayPhase(name, hour, List.of(name + " message"));
    }

    @Test
    void an_empty_schedule_is_valid_and_finds_no_phase() {
        DayPhaseSchedule schedule = new DayPhaseSchedule(List.of());
        assertThat(schedule.getPhases()).isEmpty();
        assertThat(schedule.phaseBeginningAt(6)).isEmpty();
    }

    @Test
    void finds_the_phase_beginning_at_an_hour() {
        DayPhaseSchedule schedule = new DayPhaseSchedule(List.of(phase("Dawn", 6), phase("Dusk", 18)));
        assertThat(schedule.phaseBeginningAt(6)).contains(phase("Dawn", 6));
        assertThat(schedule.phaseBeginningAt(18)).contains(phase("Dusk", 18));
    }

    @Test
    void finds_no_phase_at_an_hour_none_begin_at() {
        DayPhaseSchedule schedule = new DayPhaseSchedule(List.of(phase("Dawn", 6)));
        assertThat(schedule.phaseBeginningAt(7)).isEmpty();
    }

    @Test
    void rejects_two_phases_at_the_same_hour() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhaseSchedule(List.of(phase("Dawn", 6), phase("Sunrise", 6))));
    }

    @Test
    void is_equal_by_value() {
        assertThat(new DayPhaseSchedule(List.of(phase("Dawn", 6))))
                .isEqualTo(new DayPhaseSchedule(List.of(phase("Dawn", 6))))
                .isNotEqualTo(new DayPhaseSchedule(List.of(phase("Dawn", 7))));
    }
}
