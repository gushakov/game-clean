package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for the {@link Npc} aggregate: the always-valid construction gate, the {@link Npc#moveTo(SceneId)}
 * copy-on-write behaviour (the {@code Player.moveTo} twin), the {@link Npc#takeDamage(int)} write-side twin of
 * {@code Item.takenBy} (carrying the version forward), and the {@code Designatable} designation facts. Pinning
 * these directly here (rather than only through the use-case tests) is the testability dividend of keeping the
 * behaviour on the model.
 */
class NpcTest {

    private static Npc npc(String currentScene) {
        return Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of(currentScene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A figure in a travel-worn hooded cloak.")
                .moveChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(10))
                .build();
    }

    @Test
    void moveTo_returns_a_copy_standing_in_the_target_preserving_identity() {
        Npc atGate = npc("scn1");

        Npc moved = atGate.moveTo(SceneId.of("scn2"));

        // Copy-on-write: a new instance in the target scene, same identity; the original is untouched.
        assertThat(moved.getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        assertThat(moved.getId()).isEqualTo(NpcId.of("npc1"));
        assertThat(moved.getMoveChance()).isEqualTo(new Chance(1, 4));
        assertThat(atGate.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
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
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_null_current_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(null)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("   ")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .build());
    }

    @Test
    void rejects_a_null_move_chance() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(null)
                .build());
    }

    @Test
    void rejects_a_null_hit_points() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(null)
                .build());
    }

    @Test
    void rejects_a_negative_version() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(10))
                .version(-1)
                .build());
    }

    @Test
    void equality_is_by_id_ignoring_position() {
        Npc atGate = npc("scn1");
        Npc moved = atGate.moveTo(SceneId.of("scn2"));   // same id, different scene
        assertThat(moved).isEqualTo(atGate);
    }

    @Test
    void takeDamage_lowers_hit_points_and_carries_the_version_forward() {
        Npc goblin = Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(new HitPoints(10, 10))
                .version(7)
                .build();

        Npc struck = goblin.takeDamage(4);

        // Copy-on-write: new hit points, same identity and version (checked against what the use case read).
        assertThat(struck.getHitPoints()).isEqualTo(new HitPoints(6, 10));
        assertThat(struck.getVersion()).isEqualTo(7);
        assertThat(struck.isDead()).isFalse();
        assertThat(goblin.getHitPoints()).isEqualTo(new HitPoints(10, 10));   // original untouched
    }

    @Test
    void takeDamage_that_meets_or_exceeds_current_kills_the_npc() {
        Npc goblin = Npc.builder()
                .id(NpcId.of("npc1"))
                .currentScene(SceneId.of("scn1"))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(new HitPoints(3, 10))
                .build();

        Npc slain = goblin.takeDamage(9);   // overkill floors at zero (HitPoints' clamp)

        assertThat(slain.getHitPoints()).isEqualTo(new HitPoints(0, 10));
        assertThat(slain.isDead()).isTrue();
    }

    @Test
    void matches_is_a_case_insensitive_substring_of_the_short_description() {
        Npc wanderer = npc("scn1");   // "A hooded wanderer."
        assertThat(wanderer.matches("hooded")).isTrue();
        assertThat(wanderer.matches("WANDERER")).isTrue();
        assertThat(wanderer.matches("goblin")).isFalse();
    }

    @Test
    void matches_a_null_fragment_is_a_caller_bug() {
        assertThatNullPointerException().isThrownBy(() -> npc("scn1").matches(null));
    }

    @Test
    void hasIdToken_compares_the_raw_id_flatten() {
        Npc wanderer = npc("scn1");
        assertThat(wanderer.hasIdToken("npc1")).isTrue();
        assertThat(wanderer.hasIdToken("npc2")).isFalse();
    }
}
