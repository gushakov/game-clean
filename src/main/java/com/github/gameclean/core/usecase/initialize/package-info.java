/**
 * Summary goal: <b>initialize the game world</b>. The interactions here bring the world into a
 * playable starting state before any player or autonomous actor takes a turn.
 *
 * <p>Its first (and currently only) user goal is {@link
 * com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort ConstructWorld}: the system,
 * at startup, constructs the authored world <em>through the domain</em> (aggregate construction is
 * the validity gate) and seeds it once into an empty store. Authored input arrives as the
 * primitive {@link com.github.gameclean.core.usecase.initialize.SceneEntry} / {@link
 * com.github.gameclean.core.usecase.initialize.ExitEntry} carriers — possibly invalid by design,
 * since the always-valid model cannot represent unvalidated input — and value objects are
 * constructed inside the use case.
 */
package com.github.gameclean.core.usecase.initialize;
