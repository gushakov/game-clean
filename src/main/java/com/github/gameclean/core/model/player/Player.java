package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.scene.SceneId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * The player — the aggregate root of the player aggregate.
 *
 * <p>Immutable and always-valid: a {@code Player} cannot be constructed without an id or a current
 * scene. Its state is deliberately <em>minimal</em> — just where the player currently is. The
 * aggregate grows (inventory, stats, …) only when an interaction forces it to; {@code look} forces
 * only the position, so position is all there is.
 *
 * <p>The current scene is referenced <em>by identity</em> ({@link SceneId}), not by holding a
 * {@code Scene}: aggregates reference one another by id. Whether that id resolves to a real scene is
 * an inter-aggregate world-consistency rule checked by the use case that reads it, not an invariant
 * of this entity.
 *
 * <p>Equality is by identity (id) only.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Player {

    @EqualsAndHashCode.Include
    private final PlayerId id;
    private final SceneId currentScene;

    @Builder
    public Player(PlayerId id, SceneId currentScene) {
        this.id = Objects.requireNonNull(id, "player id must not be null");
        this.currentScene = Objects.requireNonNull(currentScene, "player current scene must not be null");
    }

    /**
     * Returns a copy of this player standing in {@code target} — the state change {@code move} records.
     * The aggregate is immutable, so moving yields a <em>new</em> instance with the same identity and the
     * new position; the original is untouched. This is the player's first behaviour beyond holding its
     * position, surfaced because {@code move} forced it — the same emergence discipline that kept the
     * aggregate to a single field until now. A null target is rejected by the constructor's validity gate.
     *
     * @param target the scene the player moves into
     * @return a new {@code Player} with this player's id and {@code target} as its current scene
     */
    public Player moveTo(SceneId target) {
        return new Player(id, target);
    }
}
