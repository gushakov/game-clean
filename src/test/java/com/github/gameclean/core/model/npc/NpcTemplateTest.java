package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NpcTemplate} — its always-valid construction (non-blank descriptions, a non-null spawn rule,
 * a non-null move chance) and {@code spawnInto}, which rolls the {@link SpawnRule}'s placements with a
 * {@link ScriptedDice} and builds one instance per placement, minting each instance's id from the <em>same</em>
 * dice and copying the move chance onto it. The NPC twin of {@code ItemTemplateTest}.
 */
class NpcTemplateTest {

    private static final String SHORT = "A hooded wanderer.";
    private static final String FULL = "A figure in a travel-worn hooded cloak.";
    private static final Chance MOVE = new Chance(1, 4);
    private static final int MAX_HP = 10;

    private static NpcTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::of).toList());
        return new NpcTemplate(SHORT, FULL, rule, MOVE, MAX_HP);
    }

    @Test
    void rejects_a_blank_short_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate("  ", FULL, rule, MOVE, MAX_HP));
    }

    @Test
    void rejects_a_null_spawn_rule() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, null, MOVE, MAX_HP));
    }

    @Test
    void rejects_a_null_move_chance() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, null, MAX_HP));
    }

    @Test
    void rejects_a_non_positive_max_hit_points() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, MOVE, 0));
    }

    @Test
    void spawns_one_instance_per_placement_with_minted_ids_copied_state_and_scenes() {
        NpcTemplate template = template(1, 1, 2, "scn1", "scn2");
        // rollPlacements resolves both placements first (both rolls + both scene picks), THEN spawnInto mints
        // each id in a second loop — so the picks are: scene 0 -> scn1, scene 1 -> scn2, then the first id's 8
        // glyphs (index 0 -> "npc00000000"), then the second id's 8 glyphs (index 1 -> "npc11111111").
        ScriptedDice dice = new ScriptedDice()
                .willRoll(true, true)
                .willPick(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1);
        List<Npc> spawned = template.spawnInto(dice);

        assertThat(spawned).extracting(npc -> npc.getId().asString()).containsExactly("npc00000000", "npc11111111");
        assertThat(spawned).extracting(npc -> npc.getCurrentScene().asString()).containsExactly("scn1", "scn2");
        assertThat(spawned).allSatisfy(npc -> {
            assertThat(npc.getShortDescription()).isEqualTo(SHORT);
            assertThat(npc.getFullDescription()).isEqualTo(FULL);
            assertThat(npc.getMoveChance()).isEqualTo(MOVE);
            // Spawns at full health and version 0 (a new, not-yet-persisted row).
            assertThat(npc.getHitPoints()).isEqualTo(HitPoints.full(MAX_HP));
            assertThat(npc.getVersion()).isZero();
        });
    }

    @Test
    void spawns_nothing_and_mints_no_id_when_no_attempt_hits() {
        NpcTemplate template = template(0, 1, 2, "scn1");
        // No hit, so no scene is picked and no id is minted — the empty pick script is never queried.
        assertThat(template.spawnInto(new ScriptedDice().willRoll(false, false))).isEmpty();
    }

    @Test
    void rejects_a_null_dice() {
        NpcTemplate template = template(1, 1, 1, "scn1");
        assertThatNullPointerException().isThrownBy(() -> template.spawnInto(null));
    }
}
