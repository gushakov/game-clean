package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SceneTest {

    private static Scene.SceneBuilder validScene() {
        return Scene.builder()
                .id(SceneId.of("scn1"))
                .name("Mossy Clearing")
                .shortDescription("A quiet clearing.")
                .fullDescription("A quiet clearing ringed by ancient, moss-draped oaks.")
                .exits(List.of(new Exit("east", SceneId.of("scn2"))));
    }

    @Test
    void builds_a_valid_scene() {
        Scene scene = validScene().build();
        assertThat(scene.getId()).isEqualTo(SceneId.of("scn1"));
        assertThat(scene.getExits()).hasSize(1);
    }

    @Test
    void allows_a_dead_end_with_no_exits() {
        assertThat(validScene().exits(List.of()).build().getExits()).isEmpty();
    }

    @Test
    void rejects_a_null_id() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().id(null).build());
    }

    @Test
    void rejects_a_blank_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().name("  ").build());
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().shortDescription("").build());
    }

    @Test
    void rejects_a_blank_full_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().fullDescription("").build());
    }

    @Test
    void rejects_null_exits() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().exits(null).build());
    }

    @Test
    void rejects_duplicate_exit_names_within_the_scene() {
        List<Exit> exits = List.of(
                new Exit("east", SceneId.of("scn2")),
                new Exit("east", SceneId.of("scn3")));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> validScene().exits(exits).build());
    }

    @Test
    void defensively_copies_the_exits_and_exposes_them_immutably() {
        List<Exit> source = new ArrayList<>(List.of(new Exit("east", SceneId.of("scn2"))));
        Scene scene = validScene().exits(source).build();

        source.clear();
        assertThat(scene.getExits()).hasSize(1);
        assertThatThrownBy(() -> scene.getExits().add(new Exit("west", SceneId.of("scn3"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void finds_an_exit_by_name() {
        Scene scene = validScene().build();
        assertThat(scene.exitNamed("east")).map(Exit::getTarget).contains(SceneId.of("scn2"));
    }

    @Test
    void matches_an_exit_name_case_insensitively_and_ignoring_whitespace() {
        Scene scene = validScene().build();
        assertThat(scene.exitNamed("  EaSt  ")).map(Exit::getName).contains("east");
    }

    @Test
    void returns_empty_for_an_unknown_or_null_exit_name() {
        Scene scene = validScene().build();
        assertThat(scene.exitNamed("west")).isEmpty();
        assertThat(scene.exitNamed(null)).isEmpty();
    }

    @Test
    void reports_exits_whose_target_is_not_among_the_known_scenes() {
        Scene scene = validScene().exits(List.of(
                new Exit("east", SceneId.of("scn2")),
                new Exit("north", SceneId.of("scn9")))).build();

        // scn2 resolves, scn9 does not.
        List<Exit> dangling = scene.exitsWithTargetNotIn(Set.of(SceneId.of("scn1"), SceneId.of("scn2")));

        assertThat(dangling).extracting(Exit::getName).containsExactly("north");
    }

    @Test
    void reports_no_dangling_exits_when_every_target_resolves() {
        Scene scene = validScene().build(); // single east -> scn2
        assertThat(scene.exitsWithTargetNotIn(Set.of(SceneId.of("scn2")))).isEmpty();
    }

    @Test
    void offers_a_mini_game_only_when_authored() {
        Scene table = validScene().miniGames(Set.of(MiniGame.BLACKJACK)).build();
        Scene plain = validScene().build();

        assertThat(table.offers(MiniGame.BLACKJACK)).isTrue();
        assertThat(plain.offers(MiniGame.BLACKJACK)).isFalse();
    }

    @Test
    void normalizes_absent_mini_games_to_none() {
        // Authored absence arrives as null as naturally as an empty set — both mean "no games here".
        assertThat(validScene().miniGames(null).build().getMiniGames()).isEmpty();
        assertThat(validScene().miniGames(Set.of()).build().getMiniGames()).isEmpty();
    }

    @Test
    void equals_by_id_only() {
        Scene a = validScene().build();
        Scene sameIdDifferentFields = validScene().name("Renamed").build();
        Scene differentId = validScene().id(SceneId.of("scn99")).build();

        assertThat(a).isEqualTo(sameIdDifferentFields);
        assertThat(a).isNotEqualTo(differentId);
    }
}
