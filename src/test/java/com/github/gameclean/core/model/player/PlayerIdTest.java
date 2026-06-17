package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PlayerIdTest {

    @Test
    void constructs_from_a_well_formed_value() {
        assertThat(new PlayerId("plr7Bf3kQ").getValue()).isEqualTo("plr7Bf3kQ");
    }

    @Test
    void trims_surrounding_whitespace() {
        assertThat(new PlayerId("  plr123  ").getValue()).isEqualTo("plr123");
    }

    @Test
    void rejects_null() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new PlayerId(null));
    }

    @Test
    void rejects_blank() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new PlayerId("   "));
    }

    @Test
    void rejects_a_missing_prefix() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new PlayerId("abc123"));
    }

    @Test
    void rejects_a_prefix_with_an_empty_body() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new PlayerId("plr"));
    }

    @Test
    void accepts_a_punctuated_logical_key() {
        assertThat(new PlayerId("plr-1_a").getValue()).isEqualTo("plr-1_a");
    }

    @Test
    void rejects_internal_whitespace() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new PlayerId("plr 1"));
    }

    @Test
    void equals_by_value() {
        assertThat(new PlayerId("plr1")).isEqualTo(new PlayerId("plr1"));
        assertThat(new PlayerId("plr1")).isNotEqualTo(new PlayerId("plr2"));
    }
}
