package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PlayerTest {

    @Test
    void constructs_with_an_id_and_a_current_scene() {
        Player player = Player.builder()
                .id(new PlayerId("plr1"))
                .currentScene(new SceneId("scn1"))
                .build();

        assertThat(player.getId()).isEqualTo(new PlayerId("plr1"));
        assertThat(player.getCurrentScene()).isEqualTo(new SceneId("scn1"));
    }

    @Test
    void rejects_a_null_id() {
        assertThatNullPointerException().isThrownBy(() -> Player.builder()
                .id(null)
                .currentScene(new SceneId("scn1"))
                .build());
    }

    @Test
    void rejects_a_null_current_scene() {
        assertThatNullPointerException().isThrownBy(() -> Player.builder()
                .id(new PlayerId("plr1"))
                .currentScene(null)
                .build());
    }

    @Test
    void equals_by_identity_only() {
        Player atGate = Player.builder().id(new PlayerId("plr1")).currentScene(new SceneId("scn1")).build();
        Player movedOn = Player.builder().id(new PlayerId("plr1")).currentScene(new SceneId("scn2")).build();
        Player other = Player.builder().id(new PlayerId("plr2")).currentScene(new SceneId("scn1")).build();

        // Same id => same player, even at a different scene; different id => different player.
        assertThat(atGate).isEqualTo(movedOn);
        assertThat(atGate).isNotEqualTo(other);
    }
}
