package com.github.gameclean.core.port.seed;

import lombok.Value;

import java.util.List;

/**
 * Parsed shape of a single authored scene — a flat, immutable carrier of primitives, deliberately
 * holding no domain types. This is the "an invalid-capable carrier crosses the boundary, never a domain
 * model" rule made concrete: the seed-source adapter produces {@code SceneEntry} values; the
 * {@link com.github.gameclean.core.model.scene.SceneId} and
 * {@link com.github.gameclean.core.model.scene.Scene} value objects are constructed <em>inside</em>
 * {@code InitializeGameUseCase}, never by the adapter.
 *
 * <p>It lives beside {@link GameSeedSourceOperationsOutputPort} because it <em>is</em> that port's return
 * contract: it describes what the seed source yields, not how any one adapter parses it. A possibly-invalid
 * carrier is required precisely because the always-valid {@code Scene} cannot represent unvalidated authored
 * data — the validity gate is the use case.
 *
 * <p>Lombok {@code @Value} (not a Java record): immutable, all-args constructor, getters,
 * equals/hashCode/toString — matching the Lombok shape used throughout the codebase.
 */
@Value
public class SceneEntry {

    String id;
    String name;
    String shortDescription;
    String fullDescription;
    List<ExitEntry> exits;
}
