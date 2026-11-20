package com.github.gameclean.infrastructure.persistence.npc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code npc_offer} table — one thing the owning NPC offers for sale
 * (#117). Like a saying's {@link NpcSayingDbEntity} it carries no id of its own: the composite key is
 * {@code (npc_id, idx)}, where the {@code npc_id} back-reference and the {@code idx} list position are both
 * supplied by the parent's {@code @MappedCollection} (a {@code List}, so authored order is the key). The domain
 * side is an {@link com.github.gameclean.core.model.npc.Offer} value object; the mapper converts each way element
 * by element, re-running the offer's own gate on the way back so a stored row the domain would not accept is an
 * integrity fault of the read.
 *
 * <p>{@code ref} and {@code item_ref} are the AUTHORED HANDLES the domain {@code Offer} carries — the offer's
 * handle within its NPC, and the {@code items:} entry handed over on purchase. Neither is a foreign key: no row
 * behind them resolves them, exactly as {@code npc.corpse_ref} names a template that is not persisted. The
 * {@code (npc_id, ref)} uniqueness the domain gate holds is enforced structurally too, by the table's unique
 * constraint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("npc_offer")
public class NpcOfferDbEntity {

    @Column("ref")
    private String ref;

    @Column("name")
    private String name;

    @Column("price")
    private int price;

    @Column("item_ref")
    private String itemRef;
}
