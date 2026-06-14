/**
 * Summary goal: <b>initialize the game world</b>. The interactions here bring the world into a
 * playable starting state before any player or autonomous actor takes a turn.
 *
 * <p>Its first user goal is {@link
 * com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort ConstructWorld}: the system,
 * at startup, constructs the authored world <em>through the domain</em> (aggregate construction is
 * the validity gate) and seeds it once into an empty store. Authored input arrives as the
 * primitive {@link com.github.gameclean.core.usecase.initialize.SceneEntry} / {@link
 * com.github.gameclean.core.usecase.initialize.ExitEntry} carriers — possibly invalid by design,
 * since the always-valid model cannot represent unvalidated input — and value objects are
 * constructed inside the use case.
 *
 * <p>Its second goal, {@link com.github.gameclean.core.usecase.initialize.CreatePlayerInputPort
 * CreatePlayer}, has the same system-at-startup actor and the same shape: it creates the single
 * player at a configured starting scene and persists it once, if and only if no player exists yet.
 * The two are the initialization pair — construct the world, then put a player in it.
 */
package com.github.gameclean.core.usecase.initialize;
