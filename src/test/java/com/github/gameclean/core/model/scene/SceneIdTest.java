package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SceneIdTest {

    @Test
    void constructs_from_a_well_formed_value() {
        assertThat(new SceneId("scn7Bf3kQ").getValue()).isEqualTo("scn7Bf3kQ");
    }

    @Test
    void trims_surrounding_whitespace() {
        assertThat(new SceneId("  scn123  ").getValue()).isEqualTo("scn123");
    }

    @Test
    void rejects_null() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new SceneId(null));
    }

    @Test
    void rejects_blank() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new SceneId("   "));
    }

    @Test
    void rejects_a_missing_prefix() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new SceneId("abc123"));
    }

    @Test
    void rejects_a_prefix_with_an_empty_body() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new SceneId("scn"));
    }

    @Test
    void accepts_a_punctuated_logical_key() {
        assertThat(new SceneId("scn-1_a").getValue()).isEqualTo("scn-1_a");
    }

    @Test
    void rejects_internal_whitespace() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new SceneId("scn 1"));
    }

    @Test
    void equals_by_value() {
        assertThat(new SceneId("scn1")).isEqualTo(new SceneId("scn1"));
        assertThat(new SceneId("scn1")).isNotEqualTo(new SceneId("scn2"));
    }
}
