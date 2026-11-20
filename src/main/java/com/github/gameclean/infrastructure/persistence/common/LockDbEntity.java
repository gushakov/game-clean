package com.github.gameclean.infrastructure.persistence.common;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;

/**
 * Embeddable persistence shape of the domain {@code Lock} on an exit — declared {@link Embedded.Nullable} on
 * {@code ExitDbEntity}, so its two fields land as nullable columns of the {@code exit} table (V17's
 * {@code lock_ref}/{@code locked_description}). Both columns null reads back as no lock: an ordinary exit.
 * Conversion to and from the domain VO is the shared {@link CompositeDbConverter}'s job, which runs the
 * {@code Lock}'s validating constructor — so a corrupt stored lock (a blank ref, or a description without a
 * ref) fails reconstitution and the reading adapter wraps it as an integrity fault.
 */
@Data
public class LockDbEntity {

    @Column("lock_ref")
    private String ref;

    @Column("locked_description")
    private String description;
}
