package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GameDate} — the positional value's own construction gate (non-negativity of every field)
 * and value equality. Range checks against a calendar are the factory's job ({@link GameCalendar#placeInstant})
 * and are exercised in {@link GameCalendarTest}, not here.
 */
class GameDateTest {

    @Test
    void rejects_a_negative_year() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameDate(-1, 0, 0, 0, 0));
    }

    @Test
    void rejects_a_negative_month_index() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameDate(1000, -1, 0, 0, 0));
    }

    @Test
    void rejects_a_negative_day_index() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameDate(1000, 0, -1, 0, 0));
    }

    @Test
    void rejects_a_negative_hour_index() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameDate(1000, 0, 0, -1, 0));
    }

    @Test
    void rejects_a_negative_second_of_hour() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameDate(1000, 0, 0, 0, -1));
    }

    @Test
    void exposes_its_fields() {
        GameDate date = new GameDate(1000, 4, 3, 12, 150);
        assertThat(date.getYear()).isEqualTo(1000);
        assertThat(date.getMonthIndex()).isEqualTo(4);
        assertThat(date.getDayIndex()).isEqualTo(3);
        assertThat(date.getHourIndex()).isEqualTo(12);
        assertThat(date.getSecondOfHour()).isEqualTo(150);
    }

    @Test
    void is_equal_by_value() {
        assertThat(new GameDate(1000, 4, 3, 12, 150))
                .isEqualTo(new GameDate(1000, 4, 3, 12, 150))
                .isNotEqualTo(new GameDate(1000, 4, 3, 12, 0));
    }
}
