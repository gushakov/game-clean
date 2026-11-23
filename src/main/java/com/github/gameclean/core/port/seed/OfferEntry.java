package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of a single authored {@code offers:} entry of an NPC — its handle, the line the offer reads to
 * the player, its price in coins, and the authoring handle of the item handed over on purchase — a flat,
 * immutable carrier of primitives holding no domain types. The NPC twin of {@link ContainsEntry}, part of the
 * {@link GameSeedSourceOperationsOutputPort} return contract; the
 * {@link com.github.gameclean.core.model.npc.Offer} value object is constructed inside the use case (the
 * validity gate).
 *
 * <p>{@code id} is the offer's <em>authoring handle</em> — unique within its NPC, and deliberately not an
 * identity of anything persisted. {@code item} is the authoring handle of an {@link ItemEntry} (the same
 * namespace {@link ContainsEntry#getItem()} and {@link NpcEntry#getCorpse()} resolve against); it is carried
 * as a raw string because the reader is not allowed to know the domain — that it resolves to a sellable item
 * at all is the use case's inter-template rule, not a parse-time concern.
 *
 * <p>Nothing here is defaulted: an entry the author wrote is an entry the author meant, so a missing {@code name}
 * or {@code item} arrives as {@code null} and a missing {@code price} fails the parse (a required scalar, the
 * {@code hitPoints} precedent). Blankness and a price below one coin are the {@code Offer} gate's, in the
 * {@link com.github.gameclean.core.model.npc.NpcTemplate} checkpoint.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class OfferEntry {

    String id;
    String name;
    int price;
    String item;
}
