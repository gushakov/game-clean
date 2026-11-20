package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A lock on an {@link Exit} — a Value Object owned by the exit it bars.
 *
 * <p>A lock carries a {@code ref}: the authored handle an item names when it opens this lock (the item's
 * {@code opens} fact). The handle is opaque — not an identity, not resolvable in this package — so it stays a
 * plain {@code String}, the {@code Npc.corpseRef} precedent. It may also carry a {@code description}: the line
 * shown to a mover refused at the exit; {@code null} when none is authored.
 *
 * <p>The lock holds <b>no state</b>. There is no locked/unlocked flag and no verb that turns it: whether an exit
 * is passable is derived, each time, from what the mover carries — "locked" is relative to the mover, never a
 * fact of the scene. A {@code Scene} therefore stays write-once after seeding.
 *
 * <p>Always-valid: the ref is non-null and non-blank; a present description is non-blank. Equality is by value.
 */
@Value
public class Lock {

    String ref;

    /** The line shown when a move through the locked exit is refused — {@code null} when none is authored. */
    String description;

    public Lock(String ref, String description) {
        if (ref == null || ref.strip().isEmpty()) {
            throw new InvalidDomainObjectError("lock ref must not be blank");
        }
        // Authored absence is a valid state (a generic refusal is rendered); a present description must carry content.
        if (description != null && description.strip().isEmpty()) {
            throw new InvalidDomainObjectError("lock description must not be blank when present");
        }
        this.ref = ref;
        this.description = description;
    }
}
