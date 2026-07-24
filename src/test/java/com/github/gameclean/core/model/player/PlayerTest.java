package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the {@link Player} aggregate: the always-valid construction gate, {@link Player#moveTo(SceneId)}
 * copy-on-write (carrying hit points and version forward), and the {@link Player#takeDamage(int)} /
 * {@link Player#isDead()} combat behaviour an NPC counterstrike drives (#66 step 2).
 */
class PlayerTest {

    private static Player player(String currentScene) {
        return Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of(currentScene))
                .hitPoints(HitPoints.full(30))
                .version(1)
                .build();
    }

    @Test
    void constructs_with_an_id_a_current_scene_and_a_hit_point_pool() {
        Player player = player("scn1");

        assertThat(player.getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(player.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
        assertThat(player.getHitPoints()).isEqualTo(HitPoints.full(30));
    }

    @Test
    void rejects_a_null_id() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(null)
                .currentScene(SceneId.of("scn1"))
                .hitPoints(HitPoints.full(30))
                .build());
    }

    @Test
    void rejects_a_null_current_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(null)
                .hitPoints(HitPoints.full(30))
                .build());
    }

    @Test
    void rejects_a_null_hit_points() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of("scn1"))
                .hitPoints(null)
                .build());
    }

    @Test
    void rejects_a_negative_version() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of("scn1"))
                .hitPoints(HitPoints.full(30))
                .version(-1)
                .build());
    }

    @Test
    void moves_to_a_new_scene_keeping_identity_health_and_version_and_leaving_the_original_unchanged() {
        Player atGate = player("scn1");

        Player moved = atGate.moveTo(SceneId.of("scn2"));

        assertThat(moved.getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(moved.getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        assertThat(moved.getHitPoints()).isEqualTo(HitPoints.full(30));   // health carried forward
        assertThat(moved.getVersion()).isEqualTo(1);                      // version carried forward
        // Immutable: the original still stands where it was.
        assertThat(atGate.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
    }

    @Test
    void rejects_a_move_to_a_null_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> player("scn1").moveTo(null));
    }

    @Test
    void takeDamage_lowers_hit_points_and_carries_identity_and_version_forward() {
        Player player = Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of("scn1"))
                .hitPoints(new HitPoints(30, 30))
                .version(5)
                .build();

        Player struck = player.takeDamage(7);

        assertThat(struck.getHitPoints()).isEqualTo(new HitPoints(23, 30));
        assertThat(struck.getVersion()).isEqualTo(5);   // checked against what the use case read
        assertThat(struck.isDead()).isFalse();
        assertThat(player.getHitPoints()).isEqualTo(new HitPoints(30, 30));   // original untouched
    }

    @Test
    void takeDamage_that_meets_or_exceeds_current_kills_the_player() {
        Player player = Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of("scn1"))
                .hitPoints(new HitPoints(4, 30))
                .build();

        Player slain = player.takeDamage(9);   // overkill floors at zero (HitPoints' clamp)

        assertThat(slain.getHitPoints()).isEqualTo(new HitPoints(0, 30));
        assertThat(slain.isDead()).isTrue();
    }

    @Test
    void equals_by_identity_only() {
        Player atGate = player("scn1");
        Player movedOn = player("scn2");
        Player other = Player.builder()
                .id(PlayerId.of("plr2"))
                .currentScene(SceneId.of("scn1"))
                .hitPoints(HitPoints.full(30))
                .build();

        // Same id => same player, even at a different scene; different id => different player.
        assertThat(atGate).isEqualTo(movedOn);
        assertThat(atGate).isNotEqualTo(other);
    }
}
