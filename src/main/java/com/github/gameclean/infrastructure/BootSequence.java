package com.github.gameclean.infrastructure;

import com.github.gameclean.infrastructure.terminal.ConsoleSession;
import com.github.gameclean.infrastructure.world.PlayerSeeder;
import com.github.gameclean.infrastructure.world.WorldSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The application's startup choreography, made explicit. The order in which the driving adapters come
 * up at boot — construct the world, <em>then</em> open it to the player — is a real application
 * invariant. Expressing it as two {@code @Order}-annotated {@code ApplicationRunner}s left that
 * sequence implicit: a property emergent from Spring discovering all runners and sorting them by
 * magic-constant precedence, reconstructable only by reading two files and knowing the SPI. Here it
 * is a single readable statement instead.
 *
 * <p>This is the <em>sole</em> {@link ApplicationRunner}. It is the right home for the ordering
 * because boot-time sequencing of <em>driving</em> adapters is a composition-root / infrastructure
 * concern, not an application-layer one: the clean core must never know about — let alone order — its
 * driving adapters (that would reverse the hexagon's "driving adapters call inward" rule). So the
 * orchestrator sits beside the composition root, in the infrastructure ring.
 *
 * <p>The steps are injected directly as the singletons they are. {@link WorldSeeder} and
 * {@link PlayerSeeder} are unconditional and their {@code seed()} is idempotent, so they are always
 * available and always safe to run; {@link ConsoleSession} and this orchestrator share the
 * {@code game.terminal.enabled} guard, so either both are present (the interactive application) or
 * neither is (test slices, which therefore never block on a console nor grab a system terminal).
 *
 * <p>Forward fit: when Phase 3 adds an independent clock and an outbox relay, their ordered start —
 * and the reverse-order shutdown the design notes call for ({@code Terminal.close()} must run last) —
 * belong here too, as further explicit lines rather than scattered {@code SmartLifecycle} phase
 * numbers (which would only be {@code @Order} magic by another name).
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootSequence implements ApplicationRunner {

    private final WorldSeeder worldSeeder;
    private final PlayerSeeder playerSeeder;
    private final ConsoleSession consoleSession;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        worldSeeder.seed();        // the world must exist ...
        playerSeeder.seed();       // ... then a player to stand in it ...
        consoleSession.start();    // ... before that player can act (blocks until 'bye').
    }
}
