package com.github.gameclean.core.model.scene;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SceneTest {

    private static Scene.SceneBuilder validScene() {
        return Scene.builder()
                .id(new SceneId("scn1"))
                .name("Mossy Clearing")
                .shortDescription("A quiet clearing.")
                .fullDescription("A quiet clearing ringed by ancient, moss-draped oaks.")
                .exits(List.of(new Exit("east", new SceneId("scn2"))));
    }

    @Test
    void builds_a_valid_scene() {
        Scene scene = validScene().build();
        assertThat(scene.getId()).isEqualTo(new SceneId("scn1"));
        assertThat(scene.getExits()).hasSize(1);
    }

    @Test
    void allows_a_dead_end_with_no_exits() {
        assertThat(validScene().exits(List.of()).build().getExits()).isEmpty();
    }

    @Test
    void rejects_a_null_id() {
        assertThatNullPointerException().isThrownBy(() -> validScene().id(null).build());
    }

    @Test
    void rejects_a_blank_name() {
        assertThatIllegalArgumentException().isThrownBy(() -> validScene().name("  ").build());
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatIllegalArgumentException().isThrownBy(() -> validScene().shortDescription("").build());
    }

    @Test
    void rejects_a_blank_full_description() {
        assertThatIllegalArgumentException().isThrownBy(() -> validScene().fullDescription("").build());
    }

    @Test
    void rejects_null_exits() {
        assertThatNullPointerException().isThrownBy(() -> validScene().exits(null).build());
    }

    @Test
    void rejects_duplicate_exit_names_within_the_scene() {
        List<Exit> exits = List.of(
                new Exit("east", new SceneId("scn2")),
                new Exit("east", new SceneId("scn3")));
        assertThatIllegalArgumentException().isThrownBy(() -> validScene().exits(exits).build());
    }

    @Test
    void defensively_copies_the_exits_and_exposes_them_immutably() {
        List<Exit> source = new ArrayList<>(List.of(new Exit("east", new SceneId("scn2"))));
        Scene scene = validScene().exits(source).build();

        source.clear();
        assertThat(scene.getExits()).hasSize(1);
        assertThatThrownBy(() -> scene.getExits().add(new Exit("west", new SceneId("scn3"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void finds_an_exit_by_name() {
        Scene scene = validScene().build();
        assertThat(scene.exitNamed("east")).map(Exit::getTarget).contains(new SceneId("scn2"));
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
    void equals_by_id_only() {
        Scene a = validScene().build();
        Scene sameIdDifferentFields = validScene().name("Renamed").build();
        Scene differentId = validScene().id(new SceneId("scn99")).build();

        assertThat(a).isEqualTo(sameIdDifferentFields);
        assertThat(a).isNotEqualTo(differentId);
    }
}
