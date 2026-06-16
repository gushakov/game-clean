package com.github.gameclean.core.port.id;

import com.github.gameclean.core.model.item.ItemId;

/**
 * Driven (output) port that mints fresh, unique aggregate identities — the core asks for an id; an
 * infrastructure adapter (a NanoID generator) supplies it. The {@code OutputPort} suffix marks the
 * hexagonal direction.
 *
 * <p>This is the generator the {@code SceneId}/{@code PlayerId} javadocs have long referred to but that
 * never existed: scene and player ids are authored or configured, so nothing was generated at runtime.
 * Item instances are the first identities the system itself must mint (one authored template spawns
 * several instances, each needing a distinct id), so the port surfaces exactly one method today and grows
 * another only when a new aggregate's id genuinely becomes machine-generated.
 *
 * <p>Per the prefix/alphabet split: the adapter owns the body's <em>alphabet and length</em> as its
 * private concern, while the {@code itm} prefix and the composition stay in {@link ItemId} (via
 * {@link ItemId#fromGeneratedBody(String)}). The port therefore returns a valid {@link ItemId} model, not
 * a raw token — a driven port hands back domain types, as the persistence ports do.
 */
public interface IdGeneratorOperationsOutputPort {

    /**
     * @return a freshly generated, unique {@link ItemId}. Uniqueness is the adapter's contract (sufficient
     *         entropy, or a sequence — its private choice).
     */
    ItemId generateItemId();
}
