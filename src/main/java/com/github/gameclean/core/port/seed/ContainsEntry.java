package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of one authored containment declaration on a container item — "this container may hold an
 * instance of item {@code item}, with the given odds" — a flat, immutable carrier of primitives holding no
 * domain types. Part of the {@link GameSeedSourceOperationsOutputPort} return contract, nested under
 * {@link ItemEntry#getContains()}.
 *
 * <p>The {@code item} is the contained template's <em>authoring handle</em> (the {@code id:} of another
 * {@code items:} entry); whether it resolves — and resolves to a non-container, since nesting is not
 * authored in this slice — is an inter-template rule the use-case gate checks, exactly like exit targets.
 * The seed-source adapter splits the {@code "1/45"} chance fraction syntactically; the resulting numbers may
 * still be <em>domain</em>-invalid (e.g. a zero denominator), which the gate rejects as a presented outcome
 * when it constructs the {@code Chance} value object.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class ContainsEntry {

    String item;
    int chanceNumerator;
    int chanceDenominator;
}
