package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Tests for {@link Weekday} — the always-valid name + description construction gate. */
class WeekdayTest {

    @Test
    void rejects_a_null_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Weekday(null, "a description"));
    }

    @Test
    void rejects_a_blank_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Weekday("   ", "a description"));
    }

    @Test
    void rejects_a_null_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Weekday("Elenya", null));
    }

    @Test
    void rejects_a_blank_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Weekday("Elenya", "  "));
    }

    @Test
    void keeps_the_name_and_description_when_both_are_present() {
        Weekday weekday = new Weekday("Elenya", "a day of guidance");
        assertThat(weekday.getName()).isEqualTo("Elenya");
        assertThat(weekday.getDescription()).isEqualTo("a day of guidance");
    }
}
