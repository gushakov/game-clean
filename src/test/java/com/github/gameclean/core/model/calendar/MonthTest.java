package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Tests for {@link Month} — the always-valid name + description construction gate. */
class MonthTest {

    @Test
    void rejects_a_null_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Month(null, "a description"));
    }

    @Test
    void rejects_a_blank_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Month("\t", "a description"));
    }

    @Test
    void rejects_a_null_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Month("Aelorin", null));
    }

    @Test
    void rejects_a_blank_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Month("Aelorin", "   "));
    }

    @Test
    void keeps_the_name_and_description_when_both_are_present() {
        Month month = new Month("Aelorin", "The Silver Wind Returns");
        assertThat(month.getName()).isEqualTo("Aelorin");
        assertThat(month.getDescription()).isEqualTo("The Silver Wind Returns");
    }
}
