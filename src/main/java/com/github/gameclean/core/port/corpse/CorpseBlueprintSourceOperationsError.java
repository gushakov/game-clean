package com.github.gameclean.core.port.corpse;

/**
 * Unchecked failure of a {@link CorpseBlueprintSourceOperationsOutputPort} operation — the authored corpse
 * blueprint could not be read, parsed, or assembled into a valid {@link CorpseBlueprint} (a missing or
 * unreadable seed resource, a malformed document, or a ref the current seed does not define as an authored
 * container without a ground-spawn rule).
 *
 * <p>Unchecked on purpose, mirroring the other driven-port boundary errors. Because the initialization gate
 * re-validates every authored corpse ref on every boot, this error at death time signals <em>drift</em> —
 * a persisted {@code corpse_ref} the (since-edited) seed no longer honors — an integrity fault, not invalid
 * player input: the combat use case lets it propagate to the outermost {@code catch → presentError}, so the
 * whole strike fails and nothing is written (no half-death).
 */
public class CorpseBlueprintSourceOperationsError extends RuntimeException {

    public CorpseBlueprintSourceOperationsError(String message) {
        super(message);
    }

    public CorpseBlueprintSourceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
