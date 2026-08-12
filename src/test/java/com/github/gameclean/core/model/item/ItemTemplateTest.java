package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ItemTemplate} — its always-valid construction (non-blank descriptions; the spawn rule is
 * <em>optional</em>, authored absence for a contained-only item), {@code spawnInto}, which rolls the
 * {@link SpawnRule}'s placements with a {@link ScriptedDice} and builds one instance per placement, minting
 * each instance's id from the <em>same</em> dice, and {@code spawnInside}, its containment counterpart (one
 * chance roll, a hit minting one instance inside the given container). The dice is the only collaborator (no
 * id supplier — the model mints its own ids), so per hit the script supplies a scene pick followed by the
 * id-body glyph picks, pinning the whole policy deterministically.
 */
class ItemTemplateTest {

    private static final String SHORT = "A rusty dagger.";
    private static final String FULL = "A plain iron dagger, rusty but usable.";

    private static ItemTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::of).toList());
        return new ItemTemplate(SHORT, FULL, false, false, rule);
    }

    @Test
    void rejects_a_blank_short_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new ItemTemplate("  ", FULL, false, false, rule));
    }

    @Test
    void rejects_a_blank_full_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new ItemTemplate(SHORT, "  ", false, false, rule));
    }

    @Test
    void a_template_without_a_spawn_rule_spawns_nothing_and_references_no_scenes() {
        // Authored absence, not invalid input: a contained-only item never spawns onto the ground. The empty
        // dice script proves no roll, no pick and no mint happen (an unscripted pull would throw).
        ItemTemplate template = new ItemTemplate(SHORT, FULL, false, false, null);

        assertThat(template.spawnInto(new ScriptedDice())).isEmpty();
        assertThat(template.candidateScenesNotIn(Set.of())).isEmpty();
    }

    @Test
    void spawns_one_instance_per_placement_with_minted_ids_copied_descriptions_and_locations() {
        ItemTemplate template = template(1, 1, 2, "scn1", "scn2");
        // rollPlacements resolves ALL placements first (both rolls + both scene picks), THEN spawnInto mints
        // each id in a second loop — so the picks are consumed as: scene 0 -> scn1, scene 1 -> scn2, then the
        // first id's 8 glyphs (index 0 -> "itm00000000"), then the second id's 8 glyphs (index 1 -> "itm11111111").
        ScriptedDice dice = new ScriptedDice()
                .willRoll(true, true)
                .willPick(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1);
        List<Item> spawned = template.spawnInto(dice);

        assertThat(spawned).extracting(item -> item.getId().asString()).containsExactly("itm00000000", "itm11111111");
        assertThat(spawned).extracting(item -> ((Location.OnGround) item.getLocation()).getScene().asString())
                .containsExactly("scn1", "scn2");
        assertThat(spawned).allSatisfy(item -> {
            assertThat(item.getShortDescription()).isEqualTo(SHORT);
            assertThat(item.getFullDescription()).isEqualTo(FULL);
        });
    }

    @Test
    void spawns_nothing_and_mints_no_id_when_no_attempt_hits() {
        ItemTemplate template = template(0, 1, 2, "scn1");
        // No hit, so no scene is picked and no id is minted — the empty pick script is never queried (it would
        // throw on an unscripted pull), so an empty result also proves nothing was minted.
        assertThat(template.spawnInto(new ScriptedDice().willRoll(false, false))).isEmpty();
    }

    @Test
    void rejects_a_null_dice() {
        ItemTemplate template = template(1, 1, 1, "scn1");
        // A null collaborator to a behaviour method is a caller bug, not invalid domain construction —
        // it stays a plain NullPointerException, unlike the value-object constructors above.
        assertThatNullPointerException().isThrownBy(() -> template.spawnInto(null));
    }

    @Test
    void instances_copy_the_container_capability() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        ItemTemplate chest = new ItemTemplate(SHORT, FULL, true, false, rule);
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(chest.spawnInto(dice)).allSatisfy(item -> assertThat(item.isContainer()).isTrue());
    }

    @Test
    void instances_copy_the_anchored_fact() {
        // The resolved fact arrives from the gate (authored `portable` inverted, kind defaults applied);
        // the template just copies it onto every instance, like the descriptions and the container flag.
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
        ItemTemplate anchoredChest = new ItemTemplate(SHORT, FULL, true, true, rule);
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(anchoredChest.spawnInto(dice)).allSatisfy(item -> assertThat(item.isAnchored()).isTrue());
    }

    @Test
    void spawnInside_mints_an_instance_inside_the_container_on_a_hit() {
        // A contained-only template (no spawn rule): the containment odds arrive as a value — they are the
        // container's authored fact — and a hit consumes one roll plus the id-body glyph picks.
        ItemTemplate template = new ItemTemplate(SHORT, FULL, false, false, null);
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(template.spawnInside(dice, new Chance(1, 45), ItemId.of("itm42"))).hasValueSatisfying(item -> {
            assertThat(item.getId().asString()).isEqualTo("itm00000000");
            assertThat(item.getLocation()).isEqualTo(new Location.Inside(ItemId.of("itm42")));
            assertThat(item.getShortDescription()).isEqualTo(SHORT);
            assertThat(item.getFullDescription()).isEqualTo(FULL);
            assertThat(item.isContainer()).isFalse();
        });
    }

    @Test
    void spawnInside_spawns_nothing_and_mints_no_id_on_a_miss() {
        ItemTemplate template = new ItemTemplate(SHORT, FULL, false, false, null);
        // A miss consumes only the roll — the empty pick script proves no id was minted.
        assertThat(template.spawnInside(new ScriptedDice().willRoll(false), new Chance(1, 45), ItemId.of("itm42")))
                .isEmpty();
    }

    @Test
    void spawnInside_rejects_null_collaborators() {
        ItemTemplate template = new ItemTemplate(SHORT, FULL, false, false, null);
        // Caller bugs, not invalid domain construction — plain NullPointerExceptions, as for spawnInto.
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(null, new Chance(1, 45), ItemId.of("itm42")));
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(new ScriptedDice(), null, ItemId.of("itm42")));
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(new ScriptedDice(), new Chance(1, 45), null));
    }
}
