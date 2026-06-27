package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for the {@link Item} aggregate: {@link Item#matches(String)} (the side-effect-free designation query),
 * the {@link Item#takenBy(PlayerId)} copy-on-write that moves an item from the ground into a player's keeping,
 * and the always-valid construction gate. Pinning these directly here (rather than only through the use-case
 * tests) is the testability dividend of keeping the behaviour on the model.
 */
class ItemTest {

    private static Item item(String shortDescription) {
        return Item.builder()
                .id(new ItemId("itm1"))
                .location(new Location.OnGround(new SceneId("scn1")))
                .shortDescription(shortDescription)
                .fullDescription("A longer description.")
                .build();
    }

    @Test
    void matches_a_case_insensitive_substring_of_the_short_description() {
        Item dagger = item("A rusty dagger.");
        assertThat(dagger.matches("rusty")).isTrue();
        assertThat(dagger.matches("RUSTY")).isTrue();
        assertThat(dagger.matches("dagger")).isTrue();
        assertThat(dagger.matches("  rusty  ")).isTrue();
    }

    @Test
    void does_not_match_a_fragment_absent_from_the_short_description() {
        assertThat(item("A rusty dagger.").matches("sword")).isFalse();
    }

    @Test
    void the_same_fragment_designates_several_look_alikes() {
        // The ambiguity examine must disambiguate: 'rusty' designates both.
        assertThat(item("A rusty dagger.").matches("rusty")).isTrue();
        assertThat(item("A rusty key.").matches("rusty")).isTrue();
    }

    @Test
    void a_null_fragment_is_a_caller_bug_not_invalid_input() {
        // A behaviour-method guard stays a plain NPE, unlike the construction gate's InvalidDomainObjectError.
        assertThatNullPointerException().isThrownBy(() -> item("A rusty dagger.").matches(null));
    }

    @Test
    void takenBy_moves_the_item_into_the_holders_keeping_preserving_id_and_version() {
        Item onGround = Item.builder()
                .id(new ItemId("itm1"))
                .location(new Location.OnGround(new SceneId("scn1")))
                .shortDescription("A rusty dagger.")
                .fullDescription("A longer description.")
                .version(3)
                .build();

        Item taken = onGround.takenBy(new PlayerId("plr1"));

        // Copy-on-write: a new instance, held by the player, same identity, version carried for the guarded write.
        assertThat(taken.getLocation()).isEqualTo(new Location.HeldBy(new PlayerId("plr1")));
        assertThat(taken.getId()).isEqualTo(new ItemId("itm1"));
        assertThat(taken.getVersion()).isEqualTo(3);
        assertThat(onGround.getLocation()).isEqualTo(new Location.OnGround(new SceneId("scn1")));   // original untouched
    }

    @Test
    void takenBy_a_null_holder_is_a_caller_bug() {
        // A null collaborator to a behaviour method is a plain NPE, not the construction gate's error.
        assertThatNullPointerException().isThrownBy(() -> item("A rusty dagger.").takenBy(null));
    }

    @Test
    void rejects_a_negative_version() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> Item.builder()
                .id(new ItemId("itm1"))
                .location(new Location.OnGround(new SceneId("scn1")))
                .shortDescription("A rusty dagger.")
                .fullDescription("A longer description.")
                .version(-1)
                .build());
    }

    @Test
    void equality_is_by_id_ignoring_location_and_version() {
        Item onGround = item("A rusty dagger.");
        Item held = onGround.takenBy(new PlayerId("plr1"));   // same id, different location
        assertThat(held).isEqualTo(onGround);
    }
}
