package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for the {@link Npc} aggregate: the always-valid construction gate and the {@link Npc#moveTo(SceneId)}
 * copy-on-write behaviour (the {@code Player.moveTo} twin). Pinning these directly here (rather than only
 * through the use-case tests) is the testability dividend of keeping the behaviour on the model.
 */
class NpcTest {

    private static Npc npc(String currentScene) {
        return Npc.builder()
                .id(new NpcId("npc1"))
                .currentScene(new SceneId(currentScene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A figure in a travel-worn hooded cloak.")
                .moveChance(new Chance(1, 4))
                .build();
    }

    @Test
    void moveTo_returns_a_copy_standing_in_the_target_preserving_identity() {
        Npc atGate = npc("scn1");

        Npc moved = atGate.moveTo(new SceneId("scn2"));

        // Copy-on-write: a new instance in the target scene, same identity; the original is untouched.
        assertThat(moved.getCurrentScene()).isEqualTo(new SceneId("scn2"));
        assertThat(moved.getId()).isEqualTo(new NpcId("npc1"));
        assertThat(moved.getMoveChance()).isEqualTo(new Chance(1, 4));
        assertThat(atGate.getCurrentScene()).isEqualTo(new SceneId("scn1"));
    }

    @Test
    void moveTo_a_null_target_is_a_caller_bug() {
        // A null collaborator to a behaviour method is a plain NPE, not the construction gate's error.
        assertThatNullPointerException().isThrownBy(() -> npc("scn1").moveTo(null));
    }

    @Test
    void rejects_a_null_id() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(null)
                .currentScene(new SceneId("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_null_current_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(new NpcId("npc1"))
                .currentScene(null)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(new NpcId("npc1"))
                .currentScene(new SceneId("scn1"))
                .shortDescription("   ")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_null_move_chance() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(new NpcId("npc1"))
                .currentScene(new SceneId("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(null)
                .build());
    }

    @Test
    void equality_is_by_id_ignoring_position() {
        Npc atGate = npc("scn1");
        Npc moved = atGate.moveTo(new SceneId("scn2"));   // same id, different scene
        assertThat(moved).isEqualTo(atGate);
    }
}
