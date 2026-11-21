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
 * a non-null move chance, the shape of its offers) and {@code spawnInto}, which rolls the {@link SpawnRule}'s
 * placements with a {@link ScriptedDice} and builds one instance per placement, minting each instance's id from
 * the <em>same</em> dice and copying the move chance onto it. The NPC twin of {@code ItemTemplateTest}.
 */
class NpcTemplateTest {

    private static final String SHORT = "A hooded wanderer.";
    private static final String FULL = "A figure in a travel-worn hooded cloak.";
    private static final Chance MOVE = new Chance(1, 4);
    private static final Chance ATTACK = new Chance(1, 3);
    private static final int MAX_HP = 10;

    /** The standing fixture every test varies: the two helpers below differ only in the authored option they add. */
    private static NpcTemplate template(SpawnRule rule, String corpseRef, List<String> sayings, List<Offer> offers) {
        return new NpcTemplate(SHORT, FULL, rule, MOVE, ATTACK, MAX_HP, corpseRef, sayings, offers);
    }

    private static NpcTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        return template(rule(numerator, denominator, maxTries, candidateScenes), null, null, null);
    }

    private static SpawnRule rule(int numerator, int denominator, int maxTries, String... candidateScenes) {
        return new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::of).toList());
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate("  ", FULL, rule(1, 1, 1, "scn1"), MOVE, ATTACK, MAX_HP,
                        null, null, null));
    }

    @Test
    void rejects_a_null_spawn_rule() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template((SpawnRule) null, null, null, null));
    }

    @Test
    void rejects_a_null_move_chance() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule(1, 1, 1, "scn1"), null, ATTACK, MAX_HP,
                        null, null, null));
    }

    @Test
    void rejects_a_null_attack_chance() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule(1, 1, 1, "scn1"), MOVE, null, MAX_HP,
                        null, null, null));
    }

    @Test
    void rejects_a_non_positive_max_hit_points() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new NpcTemplate(SHORT, FULL, rule(1, 1, 1, "scn1"), MOVE, ATTACK, 0,
                        null, null, null));
    }

    @Test
    void rejects_a_blank_corpse_ref_but_accepts_an_absent_one() {
        // Authored absence (null) is valid — the NPC leaves no corpse (covered by every other fixture here);
        // a present ref must carry content.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template(rule(1, 1, 1, "scn1"), "  ", null, null));
    }

    @Test
    void copies_the_corpse_ref_onto_every_spawned_instance() {
        NpcTemplate template = template(rule(1, 1, 1, "scn1"), "itm5", null, null);
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
        List<String> lines = List.of("Mind the steps.", "Fish? No fish.");
        NpcTemplate template = template(rule(1, 1, 2, "scn1"), null, lines, null);
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
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template(rule(1, 1, 1, "scn1"), null, List.of("A real line.", " "), null));
    }

    // --- offers (#117) ------------------------------------------------------------------------------

    private static Offer roomOffer() {
        return new Offer("room", "A room for the night.", 2, "itm6");
    }

    @Test
    void offers_are_copied_onto_every_spawned_instance_in_authored_order() {
        // The catalogue is authored prose in a meaningful order, so both the template and every instance
        // hold it as authored — the sayings rule applied to wares.
        List<Offer> wares = List.of(roomOffer(), new Offer("stew", "A bowl of yesterday's stew.", 1, "itm1"));
        NpcTemplate template = template(rule(1, 1, 2, "scn1"), null, null, wares);
        ScriptedDice dice = new ScriptedDice().willRoll(true, true).willPick(
                0, 0, 0, 0, 0, 0, 0, 0, 0,   // placement + id glyphs, first instance
                0, 0, 0, 0, 0, 0, 0, 0, 0);  // placement + id glyphs, second instance

        List<Npc> spawned = template.spawnInto(dice);

        assertThat(spawned).hasSize(2)
                .allSatisfy(npc -> assertThat(npc.getOffers()).containsExactlyElementsOf(wares));
        assertThat(template.getOffers()).containsExactlyElementsOf(wares);
    }

    @Test
    void unauthored_offers_are_the_empty_list_on_the_template_and_its_instances() {
        // Authored absence, not invalid input: an NPC that sells nothing is an ordinary NPC.
        NpcTemplate template = template(1, 1, 1, "scn1");
        assertThat(template.getOffers()).isEmpty();
        assertThat(template.instanceAt(NpcId.of("npc1"), SceneId.of("scn1")).getOffers()).isEmpty();
    }

    @Test
    void rejects_two_offers_sharing_a_handle_before_any_spawn_roll() {
        // Rejected at the template, independent of how the spawn rolls — the up-front-gate discipline: an invalid
        // template that no roll would ever exercise is still refused.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template(rule(1, 1, 1, "scn1"), null, null,
                        List.of(roomOffer(), new Offer("room", "A bowl of stew.", 1, "itm1"))))
                .withMessageContaining("room");
    }

    @Test
    void rejects_an_offer_element_the_value_object_would_not_accept() {
        // The element gate is the Offer's own; the template holds the list-wide rule. A null element is the
        // carrier-mapping bug the gate refuses rather than carrying into an instance.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template(rule(1, 1, 1, "scn1"), null, null,
                        List.of(new Offer("room", "A room for the night.", 0, "itm6"))));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> template(rule(1, 1, 1, "scn1"), null, null,
                        Arrays.asList(roomOffer(), (Offer) null)));
    }
}
