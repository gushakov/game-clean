/**
 * The <b>inventory</b> summary goal (Cockburn): interactions about <em>what the player carries</em> —
 * picking things up off the ground, putting them back down, and reviewing the keeping. Distinct from
 * {@code explore} (perceive and navigate): exploring reads the world, inventory moves an item between the
 * ground and a holder (or reads the holder's keeping).
 *
 * <p>Two writing user goals are mirror images over the same {@code orient}+{@code select} opening — a perfect
 * criss-cross: {@code Take} ({@link com.github.gameclean.core.usecase.inventory.TakeInputPort}) selects by
 * scene and mutates by player, {@code Drop}
 * ({@link com.github.gameclean.core.usecase.inventory.DropInputPort}) selects by player and mutates by
 * scene. Both mirror {@code examine}'s two-interaction designate-by-description / designate-by-choice shape
 * but <em>write</em>: a narrow transaction moves the resolved item's location. Take is the project's first
 * select-then-mutate on a contested resource, so it carries optimistic-locking handling; drop targets the
 * player's own keeping (single-writer), so it deliberately does not (see each use case). Drop's inventory
 * provisioner is the second instance that extracted the {@code select} Template-Method base.
 *
 * <p>The third user goal is the summary goal's one <em>read</em>: {@code Inventory}
 * ({@link com.github.gameclean.core.usecase.inventory.InventoryInputPort}) lists the keeping — a single
 * interaction with no target, no {@code select}, no transaction, and no {@code orient} (it is grounded in
 * the player alone, so the player is resolved inline rather than coupled to the current scene).
 */
package com.github.gameclean.core.usecase.inventory;
