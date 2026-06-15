package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.scene.Scene;
import lombok.Value;

import java.util.Objects;

/**
 * The {@link OrientPlayerSubcase orient} subcase's success result: the acting player and the scene they
 * currently stand in, returned to the parent use case ({@code move} mutates the player, {@code look}
 * renders the scene). A subcase returning a value is what distinguishes the guarded prologue from a purely
 * terminal subcase — see {@link OrientPlayerSubcase}.
 */
@Value
public class OrientPlayerResult {
    Player player;
    Scene scene;

    public OrientPlayerResult(Player player, Scene scene) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.scene = Objects.requireNonNull(scene, "scene must not be null");
    }
}
