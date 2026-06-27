/**
 * The <b>inventory</b> summary goal (Cockburn): interactions by which the player changes <em>what they
 * carry</em> — picking things up off the ground and (later) putting them back down. Distinct from
 * {@code explore} (perceive and navigate): exploring reads the world, inventory <em>moves</em> an item
 * between the ground and a holder.
 *
 * <p>The first user goal here is {@code Take} ({@link com.github.gameclean.core.usecase.inventory.TakeInputPort}),
 * which mirrors {@code examine}'s two-interaction designate-by-description / designate-by-choice shape but
 * <em>writes</em>: it resolves where the player stands (the {@code orient} subcase) and which ground item they
 * mean (the {@code select} subcase), then moves that item into the player's keeping in a narrow transaction.
 * It is the project's first select-then-mutate on a contested resource, so it carries optimistic-locking
 * handling (see the use case). {@code Drop} will join this package as the second instance, forcing the
 * {@code select} Template-Method base.
 */
package com.github.gameclean.core.usecase.inventory;
