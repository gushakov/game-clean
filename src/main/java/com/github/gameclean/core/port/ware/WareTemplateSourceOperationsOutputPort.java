package com.github.gameclean.core.port.ware;

import com.github.gameclean.core.model.item.ItemTemplate;

/**
 * Driven (output) port that serves an authored ware template at purchase time — the second <em>runtime</em>
 * consumer of authored world data, the ware twin of the
 * {@link com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsOutputPort corpse port}. The
 * {@code OutputPort} suffix marks the hexagonal direction: the buy use case is the caller (a chosen offer's
 * {@code itemRef} names the template to mint into the buyer's hands), an infrastructure adapter over the
 * authored seed source the implementor.
 *
 * <p><b>This port returns a <em>valid</em> {@link ItemTemplate}, not an invalid-capable carrier</b> — the
 * corpse port's shape, not the seed port's. Authored-ware validity is the initialization gate's business: it
 * re-checks every offer's item ref on every boot (resolves to an authored, non-container item) and presents
 * authoring faults there. By the time a purchase pulls a template the authoring is therefore valid by
 * provenance, and the seed file is assumed unedited after boot; anything that fails to assemble here — an
 * offer's persisted {@code item_ref} the current seed no longer defines, a ref the gate would reject (a
 * container, an anchored item), a malformed document — is drift between the persisted world and the seed, an
 * integrity fault surfacing as the unchecked {@link WareTemplateSourceOperationsError}, which the use case
 * lets propagate to its outermost {@code catch → presentError} (the whole purchase fails; no coins spent).
 */
public interface WareTemplateSourceOperationsOutputPort {

    /**
     * @param itemRef the authored handle an offer carries (never {@code null} — every offer names a ware)
     * @return the always-valid template to mint the ware from (never {@code null})
     * @throws WareTemplateSourceOperationsError if the template cannot be read, parsed, or assembled —
     *                                           including a ref the current seed does not define as an
     *                                           authored, non-container, non-anchored item
     */
    ItemTemplate loadWareTemplate(String itemRef);
}
