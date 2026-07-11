package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for the sealed {@link Location} value object — the two always-valid cases ({@link Location.OnGround} /
 * {@link Location.HeldBy}), each rejecting a null reference at the construction gate, and value equality. The
 * XOR a nullable-holder design would have to police is here structurally impossible: a location is one case or
 * the other, never both, never neither.
 */
class LocationTest {

    @Test
    void onGround_carries_the_scene_and_compares_by_value() {
        Location.OnGround here = new Location.OnGround(new SceneId("scn1"));

        assertThat(here.getScene()).isEqualTo(new SceneId("scn1"));
        assertThat(here).isEqualTo(new Location.OnGround(new SceneId("scn1")));
    }

    @Test
    void heldBy_carries_the_holder_and_compares_by_value() {
        Location.HeldBy held = new Location.HeldBy(new PlayerId("plr1"));

        assertThat(held.getHolder()).isEqualTo(new PlayerId("plr1"));
        assertThat(held).isEqualTo(new Location.HeldBy(new PlayerId("plr1")));
    }

    @Test
    void the_two_cases_are_never_equal() {
        assertThat((Location) new Location.OnGround(new SceneId("scn1")))
                .isNotEqualTo(new Location.HeldBy(new PlayerId("plr1")));
    }

    @Test
    void onGround_rejects_a_null_scene() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Location.OnGround(null));
    }

    @Test
    void heldBy_rejects_a_null_holder() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Location.HeldBy(null));
    }
}
