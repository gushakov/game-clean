/**
 * The <b>inventory</b> summary goal (Cockburn): interactions by which the player changes <em>what they
 * carry</em> — picking things up off the ground and (later) putting them back down. Distinct from
 * {@code explore} (perceive and navigate): exploring reads the world, inventory <em>moves</em> an item
 * between the ground and a holder.
 *
 * <p>Two user goals live here, mirror images over the same {@code orient}+{@code select} opening — a perfect
 * criss-cross: {@code Take} ({@link com.github.gameclean.core.usecase.inventory.TakeInputPort}) selects by
 * scene and mutates by player, {@code Drop}
 * ({@link com.github.gameclean.core.usecase.inventory.DropInputPort}) selects by player and mutates by
 * scene. Both mirror {@code examine}'s two-interaction designate-by-description / designate-by-choice shape
 * but <em>write</em>: a narrow transaction moves the resolved item's location. Take is the project's first
 * select-then-mutate on a contested resource, so it carries optimistic-locking handling; drop targets the
 * player's own keeping (single-writer), so it deliberately does not (see each use case). Drop's inventory
 * provisioner is the second instance that extracted the {@code select} Template-Method base.
 */
package com.github.gameclean.core.usecase.inventory;
