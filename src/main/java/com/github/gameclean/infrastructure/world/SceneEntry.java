package com.github.gameclean.infrastructure.world;

import java.util.List;

/**
 * Parsed shape of a single authored scene, as read from the world seed YAML — a flat carrier of
 * primitives, deliberately holding no domain types. This is the "input crosses the boundary as
 * primitives / {@code *Entry} DTOs" rule made concrete: the YAML adapter produces {@code SceneEntry}
 * values; the {@link com.github.gameclean.core.model.scene.SceneId} and {@code Scene} value
 * objects are constructed <em>inside</em> the use case, never here.
 *
 * <p>Spike note: these records live in infrastructure for now. When the {@code ConstructWorld} use
 * case is built they will most likely migrate to its input-port contract, since they describe what
 * the use case accepts, not how the adapter parses it.
 */
public record SceneEntry(
        String id,
        String name,
        String shortDescription,
        String fullDescription,
        List<ExitEntry> exits) {
}
