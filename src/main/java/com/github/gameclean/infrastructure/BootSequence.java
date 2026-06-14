package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.GameSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The application's startup choreography, made explicit. This is the <em>sole</em>
 * {@link ApplicationRunner}, and its job is narrow: order the two boot-time <em>driving</em> adapters —
 * bring the game into being, <em>then</em> hand the main thread to the interactive console.
 *
 * <p>It deliberately does <b>not</b> sequence business interactions. The world→player precondition (a
 * player needs a scene to stand in) is a <em>domain</em> rule, so it lives inside a single
 * {@code InitializeGame} use case rather than being assembled here by calling two use cases in turn —
 * which is what an earlier shape did, and which was unsound: once a {@code void} use case is invoked,
 * control flows inward and the outcome goes to its presenter, so an orchestrator that then made a
 * second, outcome-dependent call was acting on knowledge the unidirectional-flow contract says it has
 * renounced. {@link GameSeeder} fires that one interaction; this runner only orders adapter lifecycle.
 *
 * <p>Boot-time ordering of driving adapters <em>is</em> a composition-root / infrastructure concern,
 * not an application-layer one: the clean core must never know about — let alone order — its driving
 * adapters (that would reverse the hexagon's "driving adapters call inward" rule). So the orchestrator
 * sits beside the composition root, in the infrastructure ring.
 *
 * <p>The steps are injected directly as the singletons they are. {@link GameSeeder} is unconditional
 * and its {@code seed()} is idempotent, so it is always available and always safe to run;
 * {@link ConsoleSession} and this orchestrator share the {@code game.terminal.enabled} guard, so either
 * both are present (the interactive application) or neither is (test slices, which therefore never
 * block on a console nor grab a system terminal).
 *
 * <p>Forward fit: when Phase 3 adds an independent clock and an outbox relay, their ordered start — and
 * the reverse-order shutdown the design notes call for ({@code Terminal.close()} must run last) — belong
 * here too, as further explicit lines rather than scattered {@code SmartLifecycle} phase numbers (which
 * would only be {@code @Order} magic by another name).
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootSequence implements ApplicationRunner {

    private final GameSeeder gameSeeder;
    private final ConsoleSession consoleSession;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        gameSeeder.seed();        // construct the world and place the player, through one use case ...

        // Pragmatic compromise: the use case is void and unidirectional — its outcome went to its
        // presenter, not back here. Control nonetheless returns to this orchestrator, and we use that
        // return for one thing only: lifecycle. We make no business decision on the seeding outcome
        // (every downstream interaction self-guards); we merely hand the main thread to the console.
        consoleSession.start();   // ... then open the seeded world to the player (blocks until 'bye').
    }
}
