package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MiniGameTest {

    @Test
    void resolves_an_authored_name_case_insensitively() {
        assertThat(MiniGame.fromAuthoredName("blackjack")).isEqualTo(MiniGame.BLACKJACK);
        assertThat(MiniGame.fromAuthoredName("  Blackjack ")).isEqualTo(MiniGame.BLACKJACK);
        assertThat(MiniGame.fromAuthoredName("BLACKJACK")).isEqualTo(MiniGame.BLACKJACK);
    }

    @Test
    void rejects_an_unknown_or_blank_name_at_the_construction_gate() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> MiniGame.fromAuthoredName("poker"));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> MiniGame.fromAuthoredName("  "));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> MiniGame.fromAuthoredName(null));
    }
}
