package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PlayerTest {

    @Test
    void constructs_with_an_id_and_a_current_scene() {
        Player player = Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of("scn1"))
                .build();

        assertThat(player.getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(player.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
    }

    @Test
    void rejects_a_null_id() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(null)
                .currentScene(SceneId.of("scn1"))
                .build());
    }

    @Test
    void rejects_a_null_current_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(null)
                .build());
    }

    @Test
    void moves_to_a_new_scene_keeping_identity_and_leaving_the_original_unchanged() {
        Player atGate = Player.builder().id(PlayerId.of("plr1")).currentScene(SceneId.of("scn1")).build();

        Player moved = atGate.moveTo(SceneId.of("scn2"));

        assertThat(moved.getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(moved.getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        // Immutable: the original still stands where it was.
        assertThat(atGate.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
    }

    @Test
    void rejects_a_move_to_a_null_scene() {
        Player atGate = Player.builder().id(PlayerId.of("plr1")).currentScene(SceneId.of("scn1")).build();
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> atGate.moveTo(null));
    }

    @Test
    void equals_by_identity_only() {
        Player atGate = Player.builder().id(PlayerId.of("plr1")).currentScene(SceneId.of("scn1")).build();
        Player movedOn = Player.builder().id(PlayerId.of("plr1")).currentScene(SceneId.of("scn2")).build();
        Player other = Player.builder().id(PlayerId.of("plr2")).currentScene(SceneId.of("scn1")).build();

        // Same id => same player, even at a different scene; different id => different player.
        assertThat(atGate).isEqualTo(movedOn);
        assertThat(atGate).isNotEqualTo(other);
    }
}
