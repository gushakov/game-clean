package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExitTest {

    private static final SceneId TARGET = SceneId.of("scn2");

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
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Exit(null, TARGET));
    }

    @Test
    void rejects_a_blank_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Exit("   ", TARGET));
    }

    @Test
    void rejects_a_null_target() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Exit("east", null));
    }

    @Test
    void an_ordinary_exit_carries_no_lock() {
        Exit exit = new Exit("east", TARGET);
        assertThat(exit.getLock()).isNull();
        assertThat(exit.hasLock()).isFalse();
    }

    @Test
    void carries_its_lock() {
        Lock lock = new Lock("guest-room", null);
        Exit exit = new Exit("up", TARGET, lock);
        assertThat(exit.getLock()).isEqualTo(lock);
        assertThat(exit.hasLock()).isTrue();
    }

    @Test
    void equals_by_value() {
        assertThat(new Exit("east", TARGET)).isEqualTo(new Exit("east", TARGET));
        assertThat(new Exit("east", TARGET)).isNotEqualTo(new Exit("west", TARGET));
        assertThat(new Exit("east", TARGET)).isNotEqualTo(new Exit("east", SceneId.of("scn9")));
        assertThat(new Exit("east", TARGET)).isNotEqualTo(new Exit("east", TARGET, new Lock("guest-room", null)));
    }
}
