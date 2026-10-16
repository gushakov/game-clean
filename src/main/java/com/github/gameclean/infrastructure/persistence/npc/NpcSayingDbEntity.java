package com.github.gameclean.infrastructure.persistence.npc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code npc_saying} table — one authored line the owning NPC may
 * answer with when talked to (#104). Like a scene's {@code ExitDbEntity} it carries no id of its own: the
 * composite key is {@code (npc_id, idx)}, where the {@code npc_id} back-reference and the {@code idx} list
 * position are both supplied by the parent's {@code @MappedCollection} (a {@code List}, so authored order is
 * the key). The domain side is a plain {@code String} in the NPC's {@code sayings} list; the mapper converts
 * each way element by element.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("npc_saying")
public class NpcSayingDbEntity {

    @Column("text")
    private String text;
}
