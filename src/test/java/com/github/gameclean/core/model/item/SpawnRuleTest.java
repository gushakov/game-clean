package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SpawnRule} — its construction invariants and its side-effect-free functions
 * ({@code isHitBy}, {@code pickScene}, {@code candidateScenesNotIn}). The last is the resolution check the
 * initialization use case delegates here rather than reaching through the template into the rule; pinning it
 * in isolation is exactly the testability win of pushing the logic onto the value object.
 */
class SpawnRuleTest {

    private static SpawnRule rule(int numerator, int denominator, int maxTries, String... candidateScenes) {
        return new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::new).toList());
    }

    @Test
    void rejects_a_negative_max_tries() {
        assertThatIllegalArgumentException().isThrownBy(() -> rule(1, 2, -1, "scn1"));
    }

    @Test
    void rejects_an_empty_candidate_scene_list() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SpawnRule(new Chance(1, 2), 1, List.of()));
    }

    @Test
    void rejects_a_null_chance() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SpawnRule(null, 1, List.of(new SceneId("scn1"))));
    }

    @Test
    void a_draw_below_the_probability_hits_and_at_or_above_misses() {
        SpawnRule rule = rule(1, 2, 1, "scn1"); // probability 0.5
        assertThat(rule.isHitBy(0.49)).isTrue();
        assertThat(rule.isHitBy(0.5)).isFalse();
    }

    @Test
    void picks_a_candidate_scene_by_scaling_the_draw_across_the_candidates() {
        SpawnRule rule = rule(1, 1, 1, "scn1", "scn2", "scn3");
        assertThat(rule.pickScene(0.0)).isEqualTo(new SceneId("scn1"));
        assertThat(rule.pickScene(0.5)).isEqualTo(new SceneId("scn2"));
        assertThat(rule.pickScene(0.999)).isEqualTo(new SceneId("scn3"));
    }

    @Test
    void clamps_a_draw_at_the_top_of_the_range_to_the_last_candidate() {
        SpawnRule rule = rule(1, 1, 1, "scn1", "scn2");
        assertThat(rule.pickScene(1.0)).isEqualTo(new SceneId("scn2"));
    }

    @Test
    void reports_candidate_scenes_not_among_the_known_scenes() {
        SpawnRule rule = rule(1, 2, 1, "scn2", "scn9");
        List<SceneId> unresolved =
                rule.candidateScenesNotIn(Set.of(new SceneId("scn1"), new SceneId("scn2")));
        assertThat(unresolved).containsExactly(new SceneId("scn9"));
    }

    @Test
    void reports_no_unresolved_candidates_when_all_are_known() {
        SpawnRule rule = rule(1, 2, 1, "scn1", "scn2");
        assertThat(rule.candidateScenesNotIn(Set.of(new SceneId("scn1"), new SceneId("scn2")))).isEmpty();
    }
}
