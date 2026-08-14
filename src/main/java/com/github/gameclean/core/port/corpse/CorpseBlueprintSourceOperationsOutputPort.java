package com.github.gameclean.core.port.corpse;

/**
 * Driven (output) port that serves an authored corpse blueprint at death time — the first <em>runtime</em>
 * consumer of authored world data. The {@code OutputPort} suffix marks the hexagonal direction: the combat
 * use case is the caller (a slain NPC's {@code corpseRef} names the blueprint to mint), an infrastructure
 * adapter over the authored seed source the implementor.
 *
 * <p><b>This port returns a <em>valid</em> {@link CorpseBlueprint}, not an invalid-capable carrier</b> — the
 * calendar port's shape, not the seed port's. Authored-corpse validity is the initialization gate's business:
 * it re-checks every corpse ref on every boot (resolves to an authored container without a ground-spawn rule)
 * and presents authoring faults there. By the time a death pulls a blueprint the authoring is therefore valid
 * by provenance, and the seed file is assumed unedited after boot; anything that fails to assemble here —
 * a persisted {@code corpse_ref} the current seed no longer defines, a malformed document — is drift between
 * the persisted world and the seed, an integrity fault surfacing as the unchecked
 * {@link CorpseBlueprintSourceOperationsError}, which the use case lets propagate to its outermost
 * {@code catch → presentError} (the whole strike fails; no half-death).
 */
public interface CorpseBlueprintSourceOperationsOutputPort {

    /**
     * @param corpseRef the authored handle a slain NPC carries (never {@code null} — a corpse-less NPC
     *                  carries no ref and no blueprint is pulled)
     * @return the always-valid minting recipe for that corpse (never {@code null})
     * @throws CorpseBlueprintSourceOperationsError if the blueprint cannot be read, parsed, or assembled —
     *                                              including a ref the current seed does not define as an
     *                                              authored container without a ground-spawn rule
     */
    CorpseBlueprint loadCorpseBlueprint(String corpseRef);
}
