package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LockTest {

    @Test
    void constructs_from_a_ref_and_an_optional_description() {
        Lock lock = new Lock("guest-room", "The door is locked.");
        assertThat(lock.getRef()).isEqualTo("guest-room");
        assertThat(lock.getDescription()).isEqualTo("The door is locked.");
        assertThat(new Lock("guest-room", null).getDescription()).isNull();
    }

    @Test
    void rejects_a_null_or_blank_ref() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Lock(null, null));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Lock("  ", null));
    }

    @Test
    void rejects_a_blank_description_when_present() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Lock("guest-room", "   "));
    }

    @Test
    void equals_by_value() {
        assertThat(new Lock("guest-room", "Locked.")).isEqualTo(new Lock("guest-room", "Locked."));
        assertThat(new Lock("guest-room", "Locked.")).isNotEqualTo(new Lock("cellar", "Locked."));
        assertThat(new Lock("guest-room", "Locked.")).isNotEqualTo(new Lock("guest-room", null));
    }
}
