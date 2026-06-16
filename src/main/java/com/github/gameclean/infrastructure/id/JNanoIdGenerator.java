package com.github.gameclean.infrastructure.id;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.id.IdGeneratorOperationsOutputPort;
import org.springframework.stereotype.Component;

/**
 * NanoID-backed implementation of {@link IdGeneratorOperationsOutputPort} — the driven adapter that mints
 * fresh aggregate identities. It is the <em>sole knower of the id body's alphabet and length</em>: NanoID's
 * default produces a 21-character, URL-safe, collision-resistant token, and this adapter keeps that choice
 * private. The domain owns only the {@code itm} prefix and the composition (via
 * {@link ItemId#fromGeneratedBody(String)}); nothing here references the prefix, so the two concerns cannot
 * drift.
 */
@Component
public class JNanoIdGenerator implements IdGeneratorOperationsOutputPort {

    @Override
    public ItemId generateItemId() {
        return ItemId.fromGeneratedBody(NanoIdUtils.randomNanoId());
    }
}
