package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.PlayerSeeder;
import com.github.gameclean.infrastructure.world.WorldSeeder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * The startup ordering is the whole point of {@link BootSequence}, so it gets a direct, framework-free
 * test: with every step mocked, {@code run} must seed the world, then the player, <em>before</em> it
 * starts the player session. This pins the invariant in one fast unit test rather than leaving it to
 * be inferred from Spring's runner-sorting behaviour.
 */
class BootSequenceTest {

    @Test
    void seedsTheWorldThenThePlayerBeforeStartingTheSession() throws Exception {
        WorldSeeder worldSeeder = mock(WorldSeeder.class);
        PlayerSeeder playerSeeder = mock(PlayerSeeder.class);
        ConsoleSession consoleSession = mock(ConsoleSession.class);

        new BootSequence(worldSeeder, playerSeeder, consoleSession).run(null);

        InOrder order = inOrder(worldSeeder, playerSeeder, consoleSession);
        order.verify(worldSeeder).seed();
        order.verify(playerSeeder).seed();
        order.verify(consoleSession).start();
    }
}
