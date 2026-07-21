package com.github.gameclean.core.model.dice;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Chance} — its always-valid construction (a positive denominator and a numerator within
 * {@code [0, denominator]}) and the {@code isHitBy} rule that interprets a random draw. The draw comes from a
 * {@link Dice} in production, so pinning the interpretation here keeps the probability logic deterministic and
 * independent of any real randomness.
 */
class ChanceTest {

    @Test
    void builds_a_valid_chance() {
        Chance chance = new Chance(12, 50);
        assertThat(chance.getNumerator()).isEqualTo(12);
        assertThat(chance.getDenominator()).isEqualTo(50);
    }

    @Test
    void rejects_a_zero_denominator() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Chance(1, 0));
    }

    @Test
    void rejects_a_negative_denominator() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Chance(1, -2));
    }

    @Test
    void rejects_a_negative_numerator() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Chance(-1, 2));
    }

    @Test
    void rejects_a_numerator_greater_than_the_denominator() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Chance(3, 2));
    }

    @Test
    void a_draw_below_the_probability_hits_and_at_or_above_misses() {
        Chance even = new Chance(1, 2); // 0.5
        assertThat(even.isHitBy(0.49)).isTrue();
        assertThat(even.isHitBy(0.5)).isFalse();
    }

    @Test
    void a_zero_numerator_never_hits() {
        Chance never = new Chance(0, 1);
        assertThat(never.isHitBy(0.0)).isFalse();
        assertThat(never.isHitBy(0.5)).isFalse();
    }

    @Test
    void a_numerator_equal_to_the_denominator_always_hits() {
        Chance certain = new Chance(1, 1);
        assertThat(certain.isHitBy(0.0)).isTrue();
        assertThat(certain.isHitBy(0.999)).isTrue();
    }

    @Test
    void equals_by_value() {
        assertThat(new Chance(12, 50)).isEqualTo(new Chance(12, 50));
        assertThat(new Chance(12, 50)).isNotEqualTo(new Chance(13, 50));
    }

    @Test
    void projects_the_canonical_num_den_text_form() {
        assertThat(new Chance(12, 50).asString()).isEqualTo("12/50");
    }

    @Test
    void reconstitutes_from_the_canonical_text_form() {
        assertThat(Chance.of("12/50")).isEqualTo(new Chance(12, 50));
        assertThat(Chance.of(" 1 / 40 ")).isEqualTo(new Chance(1, 40));
    }

    @Test
    void round_trips_through_the_text_form() {
        Chance chance = new Chance(3, 7);
        assertThat(Chance.of(chance.asString())).isEqualTo(chance);
    }

    @Test
    void rejects_a_null_text_form() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of(null));
    }

    @Test
    void rejects_a_malformed_text_form() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("12"));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("12/50/2"));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("a/b"));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("12/"));
    }

    @Test
    void text_reconstitution_reruns_the_validity_gate() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("1/0"));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Chance.of("3/2"));
    }
}
