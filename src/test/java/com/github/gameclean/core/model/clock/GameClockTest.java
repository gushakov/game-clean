package com.github.gameclean.core.model.clock;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link GameClock} — the Model B banking arithmetic. The construction gate rejects a negative
 * banked total (an {@link InvalidDomainObjectError}); the behaviour methods reject a negative session-elapsed
 * (a plain {@code IllegalArgumentException}, the behaviour-guard convention), since the clock only ever moves
 * forward.
 */
class GameClockTest {

    @Test
    void initialClockHasZeroBankedSeconds() {
        assertThat(GameClock.initial().getAccumulatedGameSeconds()).isZero();
    }

    @Test
    void rejectsANegativeBankedTotal() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameClock(-1));
    }

    @Test
    void elapsedWithAddsTheSessionSecondsToTheBankedTotal() {
        GameClock clock = new GameClock(1_000);
        assertThat(clock.elapsedWith(350)).isEqualTo(1_350);
    }

    @Test
    void elapsedWithZeroSessionIsTheBankedTotal() {
        GameClock clock = new GameClock(1_000);
        assertThat(clock.elapsedWith(0)).isEqualTo(1_000);
    }

    @Test
    void elapsedWithRejectsNegativeSessionSeconds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameClock(1_000).elapsedWith(-1));
    }

    @Test
    void accumulateBanksTheSessionAndLeavesTheOriginalUntouched() {
        GameClock clock = new GameClock(1_000);
        GameClock banked = clock.accumulate(350);

        assertThat(banked.getAccumulatedGameSeconds()).isEqualTo(1_350);
        assertThat(clock.getAccumulatedGameSeconds()).isEqualTo(1_000);   // immutable — original unchanged
    }

    @Test
    void accumulateRejectsNegativeSessionSeconds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameClock(1_000).accumulate(-1));
    }

    @Test
    void equalsByValue() {
        assertThat(new GameClock(42)).isEqualTo(new GameClock(42));
        assertThat(new GameClock(42)).isNotEqualTo(new GameClock(43));
    }
}
