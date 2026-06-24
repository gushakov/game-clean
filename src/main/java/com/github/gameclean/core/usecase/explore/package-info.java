/**
 * Summary goal: <b>explore the world</b>. The interactions here let the player observe and (in
 * later rounds) navigate the scene graph the world-construction goal has seeded.
 *
 * <p>Three user goals live here, all grounded in the acting player's current scene (resolved by the
 * shared {@code orient} subcase, and the acting player is <em>ambient</em> — pulled through
 * {@code PlayerOperationsOutputPort}, never a parameter):
 * <ul>
 *   <li>{@link com.github.gameclean.core.usecase.explore.LookInputPort Look} — describe the current
 *       surroundings (the project's first read-only interaction, no transaction);</li>
 *   <li>{@link com.github.gameclean.core.usecase.explore.MoveInputPort Move} — step through a named exit
 *       into an adjacent scene (the first read-write interaction);</li>
 *   <li>{@link com.github.gameclean.core.usecase.explore.ExamineInputPort Examine} — inspect one specific
 *       thing, with disambiguation when a description is ambiguous (the first multi-interaction goal).</li>
 * </ul>
 */
package com.github.gameclean.core.usecase.explore;
