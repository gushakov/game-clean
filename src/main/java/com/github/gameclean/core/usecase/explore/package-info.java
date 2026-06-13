/**
 * Summary goal: <b>explore the world</b>. The interactions here let the player observe and (in
 * later rounds) navigate the scene graph the world-construction goal has seeded.
 *
 * <p>Its first user goal is {@link com.github.gameclean.core.usecase.explore.LookInputPort Look}: the
 * player describes their current surroundings. It is the project's first read-only interaction —
 * reads through the persistence ports and presents, with no transaction. The acting player is
 * identified by a primitive id crossing the boundary; the {@code PlayerId} value object is
 * constructed inside the use case. Looking <em>at</em> a target and moving between scenes are
 * deferred to later user goals under this same summary goal.
 */
package com.github.gameclean.core.usecase.explore;
