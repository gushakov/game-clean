package com.github.gameclean.infrastructure.persistence.scene;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Set;

/**
 * Spring Data JDBC persistence entity for the {@code scene} table — the storage shape of the
 * {@code Scene} aggregate. It is a plain mutable data holder (no ORM, no proxies): the object
 * you get back is the object. Conversion to and from the domain {@code Scene} is MapStruct's job
 * (see {@link SceneDbEntityMapper}), keeping the schema free to evolve independently of the model.
 *
 * <p>Exits are owned children of the scene aggregate, so they are modelled as a
 * {@link MappedCollection}: Spring Data JDBC writes them to the {@code exit} table with a
 * {@code scene_id} back-reference and loads them eagerly with the scene.
 */
@Data
@Table("scene")
public class SceneDbEntity {

    @Id
    private String id;

    private String name;

    @Column("short_description")
    private String shortDescription;

    @Column("full_description")
    private String fullDescription;

    @MappedCollection(idColumn = "scene_id")
    private Set<ExitDbEntity> exits;
}
