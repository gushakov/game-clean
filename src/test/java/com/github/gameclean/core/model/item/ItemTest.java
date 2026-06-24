package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for {@link Item#matches(String)} — the side-effect-free query the {@code examine} use case uses to
 * resolve a typed target against the things on the ground. Pinning it directly here (rather than only through
 * the use-case test) is the testability dividend of keeping the matching rule on the model.
 */
class ItemTest {

    private static Item item(String shortDescription) {
        return Item.builder()
                .id(new ItemId("itm1"))
                .location(new SceneId("scn1"))
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
}
