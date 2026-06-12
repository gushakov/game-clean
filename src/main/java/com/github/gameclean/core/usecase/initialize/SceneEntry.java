package com.github.gameclean.core.usecase.initialize;

import lombok.Value;

import java.util.List;

/**
 * Parsed shape of a single authored scene — a flat, immutable carrier of primitives, deliberately
 * holding no domain types. This is the "input crosses the boundary as primitives / {@code *Entry}
 * DTOs" rule made concrete: a driving adapter (the YAML seed reader) produces {@code SceneEntry}
 * values; the {@link com.github.gameclean.core.model.scene.SceneId} and
 * {@link com.github.gameclean.core.model.scene.Scene} value objects are constructed <em>inside</em>
 * {@link ConstructWorldUseCase}, never by the adapter.
 *
 * <p>It lives here, beside the input port, because it <em>is</em> the input-port contract: it
 * describes what {@code ConstructWorld} accepts, not how any one adapter parses it. A
 * possibly-invalid carrier is required on the input side precisely because the always-valid
 * {@code Scene} cannot represent unvalidated authored data — the validity gate is the use case.
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
