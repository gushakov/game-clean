/**
 * Summary goal: <b>initialize the game</b>. The single interaction here brings the game into a playable
 * starting state — pull the authored seed, construct the authored world, place the single player, and
 * spawn the authored items — before any player or autonomous actor takes a turn.
 *
 * <p>Its one user goal is {@link com.github.gameclean.core.usecase.initialize.InitializeGameInputPort
 * InitializeGame}: the system, at startup, constructs the authored world <em>through the domain</em>
 * (aggregate construction is the validity gate) and seeds it once into an empty store, creates the single
 * configured player at its starting scene if and only if no player exists yet, and spawns the authored
 * items once. These are <em>phases</em> of one interaction, not separate use cases: the world→player and
 * world→items orders are domain preconditions (a player needs a scene to stand in, items need scenes to
 * spawn into), enforced inside the use case rather than sequenced by a caller.
 *
 * <p><b>The use case pulls its input.</b> The interaction takes no argument: the authored seed is fetched
 * as the use case's first checkpoint through
 * {@link com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort}, and the
 * {@link com.github.gameclean.core.port.seed.GameSeed} carrier it returns holds primitive {@code *Entry}
 * values — possibly invalid by design, since the always-valid model cannot represent unvalidated input —
 * from which the value objects are constructed here, at the single validity gate.
 */
package com.github.gameclean.core.usecase.initialize;
