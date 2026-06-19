package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link English} — the pure grammar helper the calendar renderer composes. Ordinals (with the
 * 11th–13th exception) and counted nouns (singular vs plural).
 */
class EnglishTest {

    @Test
    void ordinalUsesTheRightSuffix() {
        assertThat(English.ordinal(1)).isEqualTo("1st");
        assertThat(English.ordinal(2)).isEqualTo("2nd");
        assertThat(English.ordinal(3)).isEqualTo("3rd");
        assertThat(English.ordinal(4)).isEqualTo("4th");
        assertThat(English.ordinal(21)).isEqualTo("21st");
        assertThat(English.ordinal(22)).isEqualTo("22nd");
        assertThat(English.ordinal(23)).isEqualTo("23rd");
    }

    @Test
    void ordinalHandlesTheElevenToThirteenException() {
        assertThat(English.ordinal(11)).isEqualTo("11th");
        assertThat(English.ordinal(12)).isEqualTo("12th");
        assertThat(English.ordinal(13)).isEqualTo("13th");
        assertThat(English.ordinal(111)).isEqualTo("111th");
        assertThat(English.ordinal(112)).isEqualTo("112th");
    }

    @Test
    void quantityIsSingularForOneAndPluralOtherwise() {
        assertThat(English.quantity(1, "hour")).isEqualTo("1 hour");
        assertThat(English.quantity(2, "hour")).isEqualTo("2 hours");
        assertThat(English.quantity(0, "second")).isEqualTo("0 seconds");
        assertThat(English.quantity(300, "second")).isEqualTo("300 seconds");
    }
}
