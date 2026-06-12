package com.github.gameclean.infrastructure.persistence.scene;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code exit} table — the storage shape of an
 * {@code Exit} value object. It carries no id of its own: an exit is an owned child of its
 * scene, identified within the aggregate by the composite key {@code (scene_id, name)}. The
 * {@code scene_id} back-reference column is supplied by the parent's
 * {@link org.springframework.data.relational.core.mapping.MappedCollection}, so it is not a
 * field here.
 */
@Data
@Table("exit")
public class ExitDbEntity {

    private String name;

    @Column("target_scene_id")
    private String targetSceneId;
}
