package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SceneIdTest {

    @Test
    void constructs_from_a_well_formed_value() {
        assertThat(SceneId.of("scn7Bf3kQ").asString()).isEqualTo("scn7Bf3kQ");
    }

    @Test
    void trims_surrounding_whitespace() {
        assertThat(SceneId.of("  scn123  ").asString()).isEqualTo("scn123");
    }

    @Test
    void rejects_null() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> SceneId.of(null));
    }

    @Test
    void rejects_blank() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> SceneId.of("   "));
    }

    @Test
    void rejects_a_missing_prefix() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> SceneId.of("abc123"));
    }

    @Test
    void rejects_a_prefix_with_an_empty_body() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> SceneId.of("scn"));
    }

    @Test
    void accepts_a_punctuated_logical_key() {
        assertThat(SceneId.of("scn-1_a").asString()).isEqualTo("scn-1_a");
    }

    @Test
    void rejects_internal_whitespace() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> SceneId.of("scn 1"));
    }

    @Test
    void equals_by_value() {
        assertThat(SceneId.of("scn1")).isEqualTo(SceneId.of("scn1"));
        assertThat(SceneId.of("scn1")).isNotEqualTo(SceneId.of("scn2"));
    }
}
