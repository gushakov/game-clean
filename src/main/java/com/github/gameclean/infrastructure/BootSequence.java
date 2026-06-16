package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.GameSeeder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * The application's startup choreography, made explicit — and made <em>unidirectional</em>. Two boot-time
 * driving adapters must come up in order: bring the game into being, <em>then</em> hand the main thread to
 * the interactive console. This declares that order as two {@code @Order}ed {@link ApplicationRunner}
 * beans, co-located here so the sequence is readable in one place.
 *
 * <p><b>Why not one runner with a {@code seed(); start();} body.</b> An imperative body of two inward
 * fire-and-forget calls reads as a <em>consequence chain</em> — "start the console <em>because</em> seeding
 * returned" — and is a place where control visibly returns, so it is a place where an outcome-dependent
 * branch on the seeding result could be (wrongly) inserted. A {@code void}, unidirectional use case has
 * sent its outcome to its presenter, not back to a caller; the caller has renounced the right to decide
 * anything on that outcome. The earlier shape kept the body and forbade the misuse by comment. This shape
 * removes the <em>affordance</em>: there is no body, no return-of-control site, no place to branch — each
 * runner is an independent inward fire, and the framework, not our code, sequences them. That is exactly
 * where driving-adapter lifecycle ordering belongs (the composition-root / infrastructure ring); our code
 * only <em>declares</em> the order.
 *
 * <p>The order is co-located and explicit (small {@code @Order} integers, not magic precedence constants),
 * so the cost the design notes (§6) charged against {@code @Order} runners — "two files, reconstructable
 * only via SPI" — is paid down to its irreducible minimum: a reader need only know that Spring runs
 * {@link ApplicationRunner}s in {@code @Order} order. The trade the notes made (imperative visibility over
 * declarative ordering) is reversed here, because unidirectional flow outranks imperative visibility and
 * visibility is recovered by co-location.
 *
 * <p>This does <b>not</b> sequence business interactions. The world→player→items preconditions are
 * <em>domain</em> rules, enforced inside the single {@code InitializeGame} use case — never assembled here
 * by chaining calls. {@link GameSeeder} fires that one interaction; these runners only order adapter
 * lifecycle.
 *
 * <p>The config and both runners share the {@code game.terminal.enabled} guard with {@link ConsoleSession},
 * so either the interactive application boots (seed, then console) or a test slice gets neither runner —
 * never seeding implicitly, blocking on a console, or grabbing a system terminal.
 *
 * <p>Forward fit: when Phase 3 adds an independent clock and an outbox relay, their ordered start — and the
 * reverse-order shutdown the design notes call for ({@code Terminal.close()} must run last) — belong here
 * too, as further {@code @Order}ed runner beans (or their lifecycle equivalent), not scattered
 * {@code SmartLifecycle} phase numbers.
 */
@Configuration
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
public class BootSequence {

    /** Step 1 — fire the system-actor driving adapter. Fire-and-forget: the use case presents its own outcome. */
    @Bean
    @Order(1)
    ApplicationRunner seedTheGame(GameSeeder gameSeeder) {
        return args -> gameSeeder.seed();
    }

    /** Step 2 — fire the player-actor driving adapter. Independent of step 1; it observes no result from it. */
    @Bean
    @Order(2)
    ApplicationRunner openTheConsole(ConsoleSession consoleSession) {
        return args -> consoleSession.start();
    }
}
