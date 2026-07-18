package com.github.gameclean.infrastructure.persistence.scene;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code scene_mini_game} table — one mini-game offered by the
 * owning scene. Like {@link ExitDbEntity} it carries no id of its own: the composite key is
 * {@code (scene_id, mini_game)}, and the {@code scene_id} back-reference is supplied by the parent's
 * {@code @MappedCollection}. Stores the domain {@code MiniGame} enum constant's name (e.g. {@code BLACKJACK});
 * the mapper reconstitutes it through the enum's own gate, so a corrupt stored name surfaces as a wrapped
 * integrity fault.
 */
@Data
@Table("scene_mini_game")
public class MiniGameDbEntity {

    @Column("mini_game")
    private String miniGame;
}
