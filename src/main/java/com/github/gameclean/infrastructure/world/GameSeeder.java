package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The boot-time driving (primary) adapter for the <em>system</em> actor: it fires
 * {@link InitializeGameInputPort#systemInitializesGame()} to bring a fresh game — world, player and items —
 * into being through the domain. It is the system-actor peer of {@code ConsoleSession} (the player-actor
 * driving adapter): one adapter, one inward interaction.
 *
 * <p>It carries <b>no logic of its own</b>. The seed is no longer read, parsed or assembled here — the use
 * case pulls it through {@code GameSeedSourceOperationsOutputPort}, so this adapter only triggers the
 * interaction. Everything else (idempotency guards, value-object construction, transactions, and now the
 * seed load) lives inside the use case, which makes {@link #seed()} safe to invoke unconditionally on every
 * boot — an already-initialized game is a no-op.
 *
 * <p>A fresh <b>prototype</b> use case is pulled from the {@link ApplicationContext} at invocation time —
 * the cargo-clean reference idiom (see design-notes). This couples the adapter to the container API, but the
 * coupling is confined to the infrastructure ring; the core never sees Spring. (A singleton must fetch the
 * prototype per interaction rather than hold one, or the prototype scope is silently defeated.)
 *
 * <p>No return of control is used: the use case is {@code void} and unidirectional — its outcome went to its
 * presenter, never back here. {@link #seed()} fires and returns nothing.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GameSeeder {

    ApplicationContext applicationContext;

    public void seed() {
        InitializeGameInputPort initializeGame = applicationContext.getBean(InitializeGameInputPort.class);
        initializeGame.systemInitializesGame();
    }
}
