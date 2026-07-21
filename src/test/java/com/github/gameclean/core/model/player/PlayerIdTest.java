package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PlayerIdTest {

    @Test
    void constructs_from_a_well_formed_value() {
        assertThat(PlayerId.of("plr7Bf3kQ").asString()).isEqualTo("plr7Bf3kQ");
    }

    @Test
    void trims_surrounding_whitespace() {
        assertThat(PlayerId.of("  plr123  ").asString()).isEqualTo("plr123");
    }

    @Test
    void rejects_null() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> PlayerId.of(null));
    }

    @Test
    void rejects_blank() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> PlayerId.of("   "));
    }

    @Test
    void rejects_a_missing_prefix() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> PlayerId.of("abc123"));
    }

    @Test
    void rejects_a_prefix_with_an_empty_body() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> PlayerId.of("plr"));
    }

    @Test
    void accepts_a_punctuated_logical_key() {
        assertThat(PlayerId.of("plr-1_a").asString()).isEqualTo("plr-1_a");
    }

    @Test
    void rejects_internal_whitespace() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> PlayerId.of("plr 1"));
    }

    @Test
    void equals_by_value() {
        assertThat(PlayerId.of("plr1")).isEqualTo(PlayerId.of("plr1"));
        assertThat(PlayerId.of("plr1")).isNotEqualTo(PlayerId.of("plr2"));
    }
}
