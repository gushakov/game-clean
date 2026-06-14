package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.GameSeeder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * The startup ordering is the whole point of {@link BootSequence}, so it gets a direct, framework-free
 * test: with both steps mocked, {@code run} must seed the game <em>before</em> it starts the player
 * session. This pins the lifecycle invariant in one fast unit test rather than leaving it to be inferred
 * from Spring's runner-sorting behaviour.
 *
 * <p>The world→player business sequence now lives inside the {@code InitializeGame} use case, fired by
 * {@link GameSeeder} — so this test concerns itself only with the two driving adapters' lifecycle order,
 * not with how the world and player are sequenced within the seed.
 */
class BootSequenceTest {

    @Test
    void seedsTheGameBeforeStartingTheSession() throws Exception {
        GameSeeder gameSeeder = mock(GameSeeder.class);
        ConsoleSession consoleSession = mock(ConsoleSession.class);

        new BootSequence(gameSeeder, consoleSession).run(null);

        InOrder order = inOrder(gameSeeder, consoleSession);
        order.verify(gameSeeder).seed();
        order.verify(consoleSession).start();
    }
}
