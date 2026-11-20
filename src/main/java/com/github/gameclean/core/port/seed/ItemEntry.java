package com.github.gameclean.core.port.seed;

import lombok.Value;

import java.util.List;

/**
 * Parsed shape of a single authored item — its descriptions, its container capability with any authored
 * containment declarations, and its {@link SpawnEntry spawn rule} — a flat, immutable carrier of primitives
 * holding no domain types. Part of the {@link GameSeedSourceOperationsOutputPort} return contract; the
 * {@link com.github.gameclean.core.model.item.ItemTemplate} value object is constructed inside the use
 * case (the validity gate).
 *
 * <p>The {@code id} is the <em>authoring handle</em> used in the seed file, in diagnostics (e.g. reporting
 * that an item spawns into an unknown scene) and as the target of another item's {@link ContainsEntry};
 * it is deliberately <em>not</em> an item instance id. One template spawns several instances, each minted a
 * fresh generated {@code ItemId}, so the authored handle never becomes a persisted instance id.
 *
 * <p>{@code opens} is the handle of the lock the item opens — {@code null} when it opens nothing. That the
 * handle names a lock declared on some authored exit is an inter-template gate rule, not a parse-time concern.
 *
 * <p>{@code contains}, {@code spawn} and {@code portable} may all be absent ({@code null}) — authored
 * absence, not invalid input: a plain item declares no containment, a <em>contained-only</em> item (one that
 * appears in the world exclusively inside containers) declares no ground-spawn rule, and an unauthored
 * {@code portable} means "use the kind-sensitive default" (a plain item is portable, a container is not) —
 * which is why it is a nullable {@code Boolean}, not a defaulted primitive: the gate must be able to tell
 * authored-false from unauthored. That {@code contains} requires {@code container: true} is a gate rule,
 * not a parse-time concern.
 *
 * <p>{@code currency} is the authored money flag — an unauthored key is {@code false}, an item is ordinary
 * property unless the seed says otherwise (the polarity is positive and defaulted, unlike {@code portable}'s
 * kind-sensitive default). That a currency item is neither a container nor anchored is a gate rule, not a
 * parse-time concern. It sits last in the carrier, after the optional {@code opens}: a positional carrier keeps
 * same-typed parameters apart, so a mis-ordered argument stays a compile error.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class ItemEntry {

    String id;
    String shortDescription;
    String fullDescription;
    boolean container;
    Boolean portable;
    List<ContainsEntry> contains;
    SpawnEntry spawn;
    String opens;

    /** Whether the item is money — one instance counts as one coin toward a price. Unauthored means false. */
    boolean currency;
}
