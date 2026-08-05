package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.infrastructure.terminal.render.Console;
import org.jline.utils.AttributedStringBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for the session-lifetime end-of-game latch: nothing is requested until {@link GameLifecycle#endGame()}
 * fires, and that single step both announces the game-over line above the live prompt (an async
 * {@link Console#printAbove}, since it is written from a background ticker thread) and raises the latch the
 * console loop reads. A pure holder + one bundled step — see {@link GameLifecycle} for why it is kept separate
 * from {@link AffordanceContext}.
 */
class GameLifecycleTest {

    private final Console console = mock(Console.class);
    private final GameLifecycle gameLifecycle = new GameLifecycle(console);

    @Test
    void nothing_is_requested_and_nothing_is_written_before_the_game_ends() {
        assertThat(gameLifecycle.endRequested()).isFalse();
        verifyNoInteractions(console);
    }

    @Test
    void ending_the_game_latches_the_request() {
        gameLifecycle.endGame();

        assertThat(gameLifecycle.endRequested()).isTrue();
    }

    @Test
    void ending_the_game_announces_the_game_over_line_above_the_live_prompt() {
        gameLifecycle.endGame();

        ArgumentCaptor<AttributedStringBuilder> captor = ArgumentCaptor.forClass(AttributedStringBuilder.class);
        verify(console).printAbove(captor.capture());
        assertThat(captor.getValue().toString()).isEqualTo("Game is over. Press Enter to quit.");
    }
}
