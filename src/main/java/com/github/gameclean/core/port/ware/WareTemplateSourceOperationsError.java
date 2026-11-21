package com.github.gameclean.core.port.ware;

/**
 * Unchecked failure of a {@link WareTemplateSourceOperationsOutputPort} operation — the authored ware
 * template could not be read, parsed, or assembled into a valid {@link com.github.gameclean.core.model.item.ItemTemplate
 * ItemTemplate} (a missing or unreadable seed resource, a malformed document, or a ref the current seed does
 * not define as an authored, non-container, non-anchored item).
 *
 * <p>Unchecked on purpose, mirroring the other driven-port boundary errors. Because the initialization gate
 * re-validates every offer's item ref on every boot, this error at purchase time signals <em>drift</em> — an
 * authored {@code item_ref} the (since-edited) seed no longer honors — an integrity fault, not invalid player
 * input: the buy use case lets it propagate to the outermost {@code catch → presentError}, so the whole
 * purchase fails and nothing is written (no coins spent for nothing delivered).
 */
public class WareTemplateSourceOperationsError extends RuntimeException {

    public WareTemplateSourceOperationsError(String message) {
        super(message);
    }

    public WareTemplateSourceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
