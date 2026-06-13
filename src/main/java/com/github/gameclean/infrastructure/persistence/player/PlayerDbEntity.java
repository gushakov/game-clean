package com.github.gameclean.infrastructure.persistence.player;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
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
 */
@Data
@Table("player")
public class PlayerDbEntity {

    @Id
    private String id;

    @Column("current_scene_id")
    private String currentSceneId;
}
