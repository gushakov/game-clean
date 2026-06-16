package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.GameSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The startup ordering is the whole point of {@link BootSequence}, so it gets a direct, framework-free
 * test. {@code BootSequence} no longer holds an imperative {@code seed(); start();} body — it declares two
 * {@code @Order}ed {@link org.springframework.boot.ApplicationRunner} beans — so the test pins two things:
 * each runner fires its own driving adapter (and nothing else), and the {@code @Order} values place seeding
 * before the console. There is deliberately no "control returns from seeding" assertion to make: the two
 * fires are independent, which is the unidirectional point of the new shape.
 *
 * <p>The world→player business sequence lives inside the {@code InitializeGame} use case, fired by
 * {@link GameSeeder} — so this test concerns itself only with the two driving adapters' lifecycle order.
 */
class BootSequenceTest {

    private final BootSequence bootSequence = new BootSequence();

    @Test
    void seedRunnerFiresOnlyTheSeeder() throws Exception {
        GameSeeder gameSeeder = mock(GameSeeder.class);

        bootSequence.seedTheGame(gameSeeder).run(null);

        verify(gameSeeder).seed();
    }

    @Test
    void consoleRunnerFiresOnlyTheConsole() throws Exception {
        ConsoleSession consoleSession = mock(ConsoleSession.class);

        bootSequence.openTheConsole(consoleSession).run(null);

        verify(consoleSession).start();
    }

    @Test
    void seedingIsOrderedBeforeTheConsole() throws Exception {
        int seedOrder = BootSequence.class
                .getDeclaredMethod("seedTheGame", GameSeeder.class).getAnnotation(Order.class).value();
        int consoleOrder = BootSequence.class
                .getDeclaredMethod("openTheConsole", ConsoleSession.class).getAnnotation(Order.class).value();

        assertThat(seedOrder).isLessThan(consoleOrder);
    }
}
