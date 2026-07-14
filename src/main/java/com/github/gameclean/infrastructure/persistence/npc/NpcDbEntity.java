package com.github.gameclean.infrastructure.persistence.npc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code npc} table — the storage shape of the {@code Npc}
 * aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the domain {@code Npc}
 * is MapStruct's job (see {@link NpcDbEntityMapper}), so the schema can evolve independently of the model.
 *
 * <p>The current scene is stored as its raw id string, deliberately <em>not</em> a foreign key (cross-aggregate
 * references are resolved as a use-case rule, not by the database), mirroring {@code player.current_scene_id}.
 * The move chance is flattened to a {@code (move_chance_num, move_chance_den)} column pair.
 *
 * <p>Unlike {@code ItemDbEntity} there is <em>no</em> {@code @Version} column: NPCs are single-writer today
 * (only the autonomous-movement ticker writes them), so there is no optimistic-locking token to carry.
 */
@Data
@Table("npc")
public class NpcDbEntity {

    @Id
    private String id;

    @Column("current_scene_id")
    private String currentSceneId;

    @Column("short_description")
    private String shortDescription;

    @Column("full_description")
    private String fullDescription;

    @Column("move_chance_num")
    private int moveChanceNum;

    @Column("move_chance_den")
    private int moveChanceDen;
}
