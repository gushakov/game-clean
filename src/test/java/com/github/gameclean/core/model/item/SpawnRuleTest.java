package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SpawnRule} — its construction invariants, the resolution check
 * ({@code candidateScenesNotIn}), and the placement roll ({@code rollPlacements}). The per-attempt mechanics
 * (interpret the odds, pick uniformly) now live on {@link Chance}/{@code Dice} and are tested there; this test
 * pins the rule's own knowledge — one roll per attempt, one pick per hit — by driving it with a scripted dice,
 * whose over-pull throws if the rule rolls or picks more than scripted.
 */
class SpawnRuleTest {

    private static SpawnRule rule(int numerator, int denominator, int maxTries, String... candidateScenes) {
        return new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::new).toList());
    }

    @Test
    void rejects_a_negative_max_tries() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> rule(1, 2, -1, "scn1"));
    }

    @Test
    void rejects_an_empty_candidate_scene_list() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new SpawnRule(new Chance(1, 2), 1, List.of()));
    }

    @Test
    void rejects_a_null_chance() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new SpawnRule(null, 1, List.of(new SceneId("scn1"))));
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
        SpawnRule rule = rule(1, 1, 2, "scn1", "scn2");
        // attempt 0: hit, pick index 0 -> scn1; attempt 1: hit, pick index 1 -> scn2
        ScriptedDice dice = new ScriptedDice().willRoll(true, true).willPick(0, 1);
        assertThat(rule.rollPlacements(dice)).containsExactly(new SceneId("scn1"), new SceneId("scn2"));
    }

    @Test
    void rolls_once_per_attempt_and_picks_only_on_a_hit() {
        SpawnRule rule = rule(1, 2, 3, "scn1", "scn2");
        // miss; hit then pick index 0 -> scn1; miss. Exactly three rolls and one pick are scripted — the rule
        // pulling more (or fewer) would over-pull the scripted dice and throw, pinning the draw-ordering here.
        ScriptedDice dice = new ScriptedDice().willRoll(false, true, false).willPick(0);
        assertThat(rule.rollPlacements(dice)).containsExactly(new SceneId("scn1"));
    }

    @Test
    void rolls_no_placements_when_no_attempt_hits() {
        SpawnRule rule = rule(0, 1, 2, "scn1");
        assertThat(rule.rollPlacements(new ScriptedDice().willRoll(false, false))).isEmpty();
    }

    @Test
    void rolls_no_placements_and_rolls_no_dice_when_max_tries_is_zero() {
        SpawnRule rule = rule(1, 1, 0, "scn1");
        assertThat(rule.rollPlacements(new ScriptedDice())).isEmpty(); // empty script never queried
    }

    @Test
    void rejects_a_null_dice() {
        // A null collaborator to a behaviour method is a caller bug, not invalid domain construction —
        // it stays a plain NullPointerException, unlike the constructor invariants above.
        assertThatNullPointerException().isThrownBy(() -> rule(1, 1, 1, "scn1").rollPlacements(null));
    }
}
