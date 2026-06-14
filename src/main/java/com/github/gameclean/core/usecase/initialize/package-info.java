/**
 * Summary goal: <b>initialize the game</b>. The single interaction here brings the game into a playable
 * starting state — construct the authored world, then place the single player in it — before any player
 * or autonomous actor takes a turn.
 *
 * <p>Its one user goal is {@link com.github.gameclean.core.usecase.initialize.InitializeGameInputPort
 * InitializeGame}: the system, at startup, constructs the authored world <em>through the domain</em>
 * (aggregate construction is the validity gate) and seeds it once into an empty store, then creates the
 * single configured player at its starting scene, once, if and only if no player exists yet. The two are
 * <em>phases</em> of one interaction, not separate use cases: the world→player order is a domain
 * precondition (a player needs a scene to stand in), enforced inside the use case rather than sequenced
 * by a caller.
 *
 * <p>Authored input arrives as the primitive {@link
 * com.github.gameclean.core.usecase.initialize.SceneEntry} / {@link
 * com.github.gameclean.core.usecase.initialize.ExitEntry} carriers and a bare starting-scene id —
 * possibly invalid by design, since the always-valid model cannot represent unvalidated input — and
 * value objects are constructed inside the use case.
 */
package com.github.gameclean.core.usecase.initialize;
