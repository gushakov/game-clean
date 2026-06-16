package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SpawnRule} — its construction invariants and its side-effect-free functions
 * ({@code isHitBy}, {@code pickScene}, {@code candidateScenesNotIn}, {@code rollPlacements}). The resolution
 * check and the placement roll are both delegated here rather than driven by the initialization use case
 * reaching through the template into the rule; pinning them in isolation is exactly the testability win of
 * pushing the logic onto the value object.
 */
class SpawnRuleTest {

    private static SpawnRule rule(int numerator, int denominator, int maxTries, String... candidateScenes) {
        return new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::new).toList());
    }

    /** A draw source over a fixed sequence — throws (index out of bounds) if more draws are pulled than given. */
    private static DoubleSupplier draws(double... values) {
        int[] next = {0};
        return () -> values[next[0]++];
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

    @Test
    void rolls_one_placement_per_hit_in_attempt_order() {
        SpawnRule rule = rule(1, 1, 2, "scn1", "scn2"); // always hits
        // attempt 0: hit(0.0), pick 0.0 -> scn1; attempt 1: hit(0.0), pick 0.9 -> scn2
        assertThat(rule.rollPlacements(draws(0.0, 0.0, 0.0, 0.9)))
                .containsExactly(new SceneId("scn1"), new SceneId("scn2"));
    }

    @Test
    void consumes_one_draw_for_a_miss_and_a_second_only_for_a_hit() {
        SpawnRule rule = rule(1, 2, 3, "scn1", "scn2"); // probability 0.5
        // miss(0.7); hit(0.4) then pick(0.1)->scn1; miss(0.5). Exactly four draws are pulled — supplying
        // fewer or expecting more would index out of bounds, pinning the draw-ordering knowledge here.
        assertThat(rule.rollPlacements(draws(0.7, 0.4, 0.1, 0.5)))
                .containsExactly(new SceneId("scn1"));
    }

    @Test
    void rolls_no_placements_when_no_attempt_hits() {
        SpawnRule rule = rule(0, 1, 2, "scn1"); // never hits
        assertThat(rule.rollPlacements(draws(0.0, 0.9))).isEmpty();
    }

    @Test
    void rolls_no_placements_and_pulls_no_draw_when_max_tries_is_zero() {
        SpawnRule rule = rule(1, 1, 0, "scn1");
        assertThat(rule.rollPlacements(draws())).isEmpty(); // empty source never queried
    }

    @Test
    void rejects_a_null_draw_source() {
        assertThatNullPointerException().isThrownBy(() -> rule(1, 1, 1, "scn1").rollPlacements(null));
    }
}
