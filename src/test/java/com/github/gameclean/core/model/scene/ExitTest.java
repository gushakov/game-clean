package com.github.gameclean.core.model.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExitTest {

    private static final SceneId TARGET = new SceneId("scn2");

    @Test
    void constructs_from_a_name_and_a_target() {
        Exit exit = new Exit("east", TARGET);
        assertThat(exit.getName()).isEqualTo("east");
        assertThat(exit.getTarget()).isEqualTo(TARGET);
    }

    @Test
    void trims_the_name() {
        assertThat(new Exit("  east  ", TARGET).getName()).isEqualTo("east");
    }

    @Test
    void rejects_a_null_name() {
        assertThatNullPointerException().isThrownBy(() -> new Exit(null, TARGET));
    }

    @Test
    void rejects_a_blank_name() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Exit("   ", TARGET));
    }

    @Test
    void rejects_a_null_target() {
        assertThatNullPointerException().isThrownBy(() -> new Exit("east", null));
    }

    @Test
    void equals_by_value() {
        assertThat(new Exit("east", TARGET)).isEqualTo(new Exit("east", TARGET));
        assertThat(new Exit("east", TARGET)).isNotEqualTo(new Exit("west", TARGET));
        assertThat(new Exit("east", TARGET)).isNotEqualTo(new Exit("east", new SceneId("scn9")));
    }
}
