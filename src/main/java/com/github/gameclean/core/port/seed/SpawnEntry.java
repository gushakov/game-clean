package com.github.gameclean.core.port.seed;

import lombok.Value;

import java.util.List;

/**
 * Parsed shape of an authored item's spawn rule — a flat carrier of primitives, holding no domain types:
 * the candidate scene ids, the chance as a numerator/denominator pair, and the maximum number of spawn
 * attempts. The seed-source adapter parses the authoring syntax — a {@code "scn2, scn3"} list into
 * {@code scenes}, a {@code "12/50"} fraction into {@code chanceNumerator}/{@code chanceDenominator} — and
 * this carrier holds the result, possibly <em>domain</em>-invalid (e.g. a zero denominator, a malformed
 * scene id) so the use-case gate can reject it as a presented outcome. The
 * {@link com.github.gameclean.core.model.item.Chance} / {@link com.github.gameclean.core.model.item.SpawnRule}
 * value objects are constructed inside {@code InitializeGameUseCase}.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class SpawnEntry {

    List<String> scenes;
    int chanceNumerator;
    int chanceDenominator;
    int max;
}
