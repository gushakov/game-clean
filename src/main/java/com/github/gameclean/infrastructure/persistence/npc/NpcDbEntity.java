package com.github.gameclean.infrastructure.persistence.npc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code npc} table — the storage shape of the {@code Npc}
 * aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the domain {@code Npc}
 * is MapStruct's job (see {@link NpcDbEntityMapper}), so the schema can evolve independently of the model.
 *
 * <p>The current scene is stored as its raw id string, deliberately <em>not</em> a foreign key (cross-aggregate
 * references are resolved as a use-case rule, not by the database), mirroring {@code player.current_scene_id}.
 * The move chance is stored as its canonical {@code num/den} text in a single {@code move_chance} column
 * (chance arithmetic never happens in SQL, and the fraction reads directly in query results); the hit points
 * flatten to a {@code (hit_points, max_hit_points)} pair (current out of max), which stays two int columns
 * because current/max are plausibly SQL-comparable — queryability decides column shape.
 *
 * <p>The {@link #version} carries Spring Data JDBC's {@link Version optimistic-locking} token, exactly like
 * {@code ItemDbEntity}: a {@code 0} version marks a new (insertable) row, and each write checks-and-increments
 * it, so the player's {@code hit} and the wandering ticker cannot both win a race to write the same NPC. It
 * crosses the boundary onto the domain {@code Npc}, which carries it through {@code takeDamage}/{@code moveTo}
 * so the guarded write is checked against the version the use case read.
 */
@Data
@Table("npc")
public class NpcDbEntity {

    @Id
    private String id;

    @Version
    @Column("version")
    private long version;

    @Column("current_scene_id")
    private String currentSceneId;

    @Column("short_description")
    private String shortDescription;

    @Column("full_description")
    private String fullDescription;

    @Column("move_chance")
    private String moveChance;

    @Column("hit_points")
    private int currentHitPoints;

    @Column("max_hit_points")
    private int maxHitPoints;
}
