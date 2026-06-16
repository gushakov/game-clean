package com.github.gameclean.infrastructure.persistence.item;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code item} table — the storage shape of the {@code Item}
 * aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the domain
 * {@code Item} is MapStruct's job (see {@link ItemDbEntityMapper}), so the schema can evolve independently
 * of the model.
 *
 * <p>The item's location is stored as its raw scene id string. It is deliberately <em>not</em> a foreign
 * key to the {@code scene} table — an item references where it lies by identity, and whether that id
 * resolves to a known scene is an inter-aggregate concern, mirroring {@code player.current_scene_id} and
 * {@code exit.target_scene_id}.
 */
@Data
@Table("item")
public class ItemDbEntity {

    @Id
    private String id;

    @Column("short_description")
    private String shortDescription;

    @Column("full_description")
    private String fullDescription;

    @Column("scene_id")
    private String sceneId;
}
