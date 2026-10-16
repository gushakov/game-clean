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
    private static final Chance ATTACK = new Chance(1, 3);
    private static final int MAX_HP = 10;

    private static NpcTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::of).toList());
        return new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, null, null);
    }

    @Test
    void rejects_a_blank_short_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate("  ", FULL, rule, MOVE, ATTACK, MAX_HP, null, null));
    }

    @Test
    void rejects_a_null_spawn_rule() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, null, MOVE, ATTACK, MAX_HP, null, null));
    }

    @Test
    void rejects_a_null_move_chance() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, null, ATTACK, MAX_HP, null, null));
    }

    @Test
    void rejects_a_null_attack_chance() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, MOVE, null, MAX_HP, null, null));
    }

    @Test
    void rejects_a_non_positive_max_hit_points() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, 0, null, null));
    }

    @Test
    void rejects_a_blank_corpse_ref_but_accepts_an_absent_one() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        // Authored absence (null) is valid — the NPC leaves no corpse (covered by every other fixture here);
        // a present ref must carry content.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, "  ", null));
    }

    @Test
    void copies_the_corpse_ref_onto_every_spawned_instance() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        NpcTemplate template = new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, "itm5", null);
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        List<Npc> spawned = template.spawnInto(dice);

        assertThat(spawned).singleElement()
                .satisfies(npc -> assertThat(npc.getCorpseRef()).isEqualTo("itm5"));
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
            assertThat(npc.getAttackChance()).isEqualTo(ATTACK);
            // Spawns at full health, non-hostile, and version 0 (a new, not-yet-persisted row).
            assertThat(npc.getHitPoints()).isEqualTo(HitPoints.full(MAX_HP));
            assertThat(npc.isHostile()).isFalse();
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

    // --- sayings (#104) ----------------------------------------------------------------------------

    @Test
    void sayings_are_copied_onto_every_spawned_instance_in_authored_order() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 2, List.of(SceneId.of("scn1")));
        List<String> lines = List.of("Mind the steps.", "Fish? No fish.");
        NpcTemplate template = new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, null, lines);
        ScriptedDice dice = new ScriptedDice().willRoll(true, true).willPick(
                0, 0, 0, 0, 0, 0, 0, 0, 0,   // placement + id glyphs, first instance
                0, 0, 0, 0, 0, 0, 0, 0, 0);  // placement + id glyphs, second instance

        List<Npc> spawned = template.spawnInto(dice);

        assertThat(spawned).hasSize(2)
                .allSatisfy(npc -> assertThat(npc.getSayings()).containsExactlyElementsOf(lines));
        assertThat(template.getSayings()).containsExactlyElementsOf(lines);
    }

    @Test
    void unauthored_sayings_are_the_empty_list_on_the_template_and_its_instances() {
        NpcTemplate template = template(1, 1, 1, "scn1");
        assertThat(template.getSayings()).isEmpty();
        assertThat(template.instanceAt(NpcId.of("npc1"), SceneId.of("scn1")).hasSayings()).isFalse();
    }

    @Test
    void a_blank_saying_is_rejected_at_the_template_before_any_spawn_roll() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, null,
                        List.of("A real line.", " ")));
    }
}
