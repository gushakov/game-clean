package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.infrastructure.persistence.common.HitPointsDbEntity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code player} table — the storage shape of the
 * {@code Player} aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from
 * the domain {@code Player} is MapStruct's job (see {@link PlayerDbEntityMapper}), so the schema can
 * evolve independently of the model.
 *
 * <p>The current scene is stored as its raw id string. It is deliberately <em>not</em> a foreign key
 * to the {@code scene} table (cross-aggregate references are resolved as a use-case rule, not by the
 * database), mirroring {@code exit.target_scene_id}.
 *
 * <p>Hit points embed as the shared {@link HitPointsDbEntity} over the {@code (hit_points, max_hit_points)}
 * column pair — the second consumer of that embeddable after {@code npc} — and the {@link #version} carries
 * Spring Data JDBC's {@link Version optimistic-locking} token (V12, #66 step 2): an NPC counterstrike and the
 * player's own {@code move} both write the player, so a {@code 0} version marks a new (insertable) row and each
 * write checks-and-increments it, exactly like {@code NpcDbEntity} / {@code ItemDbEntity}.
 */
@Data
@Table("player")
public class PlayerDbEntity {

    @Id
    private String id;

    @Version
    @Column("version")
    private long version;

    @Column("current_scene_id")
    private String currentSceneId;

    @Embedded.Nullable
    private HitPointsDbEntity hitPoints;
}
