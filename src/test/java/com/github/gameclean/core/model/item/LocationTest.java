package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for the sealed {@link Location} value object — the three always-valid cases ({@link Location.OnGround} /
 * {@link Location.HeldBy} / {@link Location.Inside}), each rejecting a null reference at the construction gate,
 * and value equality. The XOR a nullable-fields design would have to police is here structurally impossible: a
 * location is exactly one case, never two, never none.
 */
class LocationTest {

    @Test
    void onGround_carries_the_scene_and_compares_by_value() {
        Location.OnGround here = new Location.OnGround(SceneId.of("scn1"));

        assertThat(here.getScene()).isEqualTo(SceneId.of("scn1"));
        assertThat(here).isEqualTo(new Location.OnGround(SceneId.of("scn1")));
    }

    @Test
    void heldBy_carries_the_holder_and_compares_by_value() {
        Location.HeldBy held = new Location.HeldBy(PlayerId.of("plr1"));

        assertThat(held.getHolder()).isEqualTo(PlayerId.of("plr1"));
        assertThat(held).isEqualTo(new Location.HeldBy(PlayerId.of("plr1")));
    }

    @Test
    void inside_carries_the_container_and_compares_by_value() {
        Location.Inside inside = new Location.Inside(ItemId.of("itm42"));

        assertThat(inside.getContainer()).isEqualTo(ItemId.of("itm42"));
        assertThat(inside).isEqualTo(new Location.Inside(ItemId.of("itm42")));
    }

    @Test
    void distinct_cases_are_never_equal() {
        assertThat((Location) new Location.OnGround(SceneId.of("scn1")))
                .isNotEqualTo(new Location.HeldBy(PlayerId.of("plr1")))
                .isNotEqualTo(new Location.Inside(ItemId.of("itm42")));
        assertThat((Location) new Location.HeldBy(PlayerId.of("plr1")))
                .isNotEqualTo(new Location.Inside(ItemId.of("itm42")));
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

    @Test
    void inside_rejects_a_null_container() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Location.Inside(null));
    }
}
