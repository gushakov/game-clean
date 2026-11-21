package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ItemTemplate} — its always-valid construction (non-blank descriptions; the spawn rule is
 * <em>optional</em>, authored absence for a contained-only item; the currency invariant), {@code spawnInto}, which
 * rolls the {@link SpawnRule}'s placements with a {@link ScriptedDice} and builds one instance per placement,
 * minting each instance's id from the <em>same</em> dice, and {@code spawnInside}, its containment counterpart (one
 * chance roll, a hit minting one instance inside the given container). The dice is the only collaborator (no
 * id supplier — the model mints its own ids), so per hit the script supplies a scene pick followed by the
 * id-body glyph picks, pinning the whole policy deterministically.
 *
 * <p>The template is built through its constructor-level {@code @Builder} — the {@link #templateBuilder()} helper
 * starts from the two descriptions and each test names only the facts it is about, so an added capability costs
 * this file no edit and a same-typed argument can never be quietly swapped.
 */
class ItemTemplateTest {

    private static final String SHORT = "A rusty dagger.";
    private static final String FULL = "A plain iron dagger, rusty but usable.";

    /** A template carrying the two descriptions and nothing else — every test adds the facts it is about. */
    private static ItemTemplate.ItemTemplateBuilder templateBuilder() {
        return ItemTemplate.builder().shortDescription(SHORT).fullDescription(FULL);
    }

    private static ItemTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::of).toList());
        return templateBuilder().spawnRule(rule).build();
    }

    private static SpawnRule alwaysInScn1() {
        return new SpawnRule(new Chance(1, 1), 1, List.of(SceneId.of("scn1")));
    }

    @Test
    void rejects_a_blank_short_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> ItemTemplate.builder().shortDescription("  ").fullDescription(FULL)
                        .spawnRule(alwaysInScn1()).build());
    }

    @Test
    void rejects_a_blank_full_description() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> ItemTemplate.builder().shortDescription(SHORT).fullDescription("  ")
                        .spawnRule(alwaysInScn1()).build());
    }

    @Test
    void a_template_without_a_spawn_rule_spawns_nothing_and_references_no_scenes() {
        // Authored absence, not invalid input: a contained-only item never spawns onto the ground. The empty
        // dice script proves no roll, no pick and no mint happen (an unscripted pull would throw).
        ItemTemplate template = templateBuilder().build();

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
        ItemTemplate chest = templateBuilder().container(true).spawnRule(alwaysInScn1()).build();
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(chest.spawnInto(dice)).allSatisfy(item -> assertThat(item.isContainer()).isTrue());
    }

    @Test
    void rejects_a_blank_opens_handle() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> templateBuilder().opens("  ").spawnRule(alwaysInScn1()).build());
    }

    @Test
    void instances_copy_the_opens_handle() {
        ItemTemplate key = templateBuilder().opens("guest-room").spawnRule(alwaysInScn1()).build();
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(key.spawnInto(dice)).singleElement()
                .satisfies(item -> assertThat(item.getOpens()).isEqualTo("guest-room"));
        assertThat(templateBuilder().spawnRule(alwaysInScn1()).build().getOpens()).isNull();
    }

    @Test
    void instances_copy_the_anchored_fact() {
        // The resolved fact arrives from the gate (authored `portable` inverted, kind defaults applied);
        // the template just copies it onto every instance, like the descriptions and the container flag.
        ItemTemplate anchoredChest = templateBuilder().container(true).anchored(true).spawnRule(alwaysInScn1()).build();
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(anchoredChest.spawnInto(dice)).allSatisfy(item -> assertThat(item.isAnchored()).isTrue());
    }

    @Test
    void rejects_a_currency_item_that_is_a_container() {
        // Money that holds other things is a purse, not a coin: the invariant is the template's, rejected as
        // authored authoring-shape nonsense rather than surviving to spawn an instance the Item gate would refuse.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> templateBuilder().currency(true).container(true).build());
    }

    @Test
    void rejects_a_currency_item_that_is_anchored() {
        // Money bolted to the ground cannot be paid.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> templateBuilder().currency(true).anchored(true).build());
    }

    @Test
    void instanceHeldBy_places_the_instance_in_the_holders_keeping_and_copies_the_instance_facts() {
        // The hands counterpart of spawnInto: the ware a purchase mints appears directly in the buyer's
        // keeping. The id is handed in (the use case mints it), and every instance-copied fact rides onto the
        // instance — here the opens handle and the currency flag, from the two wares the inn trades.
        ItemTemplate key = templateBuilder().shortDescription("A small brass key.")
                .opens("guest-room").build();
        Item keyInHand = key.instanceHeldBy(ItemId.of("itm6"), PlayerId.of("plr1"));
        assertThat(keyInHand.getLocation()).isEqualTo(new Location.HeldBy(PlayerId.of("plr1")));
        assertThat(keyInHand.getOpens()).isEqualTo("guest-room");
        assertThat(keyInHand.getShortDescription()).isEqualTo("A small brass key.");

        ItemTemplate coin = templateBuilder().shortDescription("A copper coin.")
                .currency(true).build();
        Item coinInHand = coin.instanceHeldBy(ItemId.of("itm3"), PlayerId.of("plr1"));
        assertThat(coinInHand.getLocation()).isEqualTo(new Location.HeldBy(PlayerId.of("plr1")));
        assertThat(coinInHand.isCurrency()).isTrue();
    }

    @Test
    void instances_copy_the_currency_flag() {
        // The plain coin: neither a container nor anchored, so the invariant holds and the flag rides onto
        // every instance like the container and anchored facts. Absence is the ordinary case: not money.
        ItemTemplate coin = templateBuilder().shortDescription("A copper coin.")
                .fullDescription("A single dull copper coin.")
                .currency(true)
                .spawnRule(alwaysInScn1())
                .build();
        ScriptedDice dice = new ScriptedDice().willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(coin.spawnInto(dice)).allSatisfy(item -> {
            assertThat(item.isCurrency()).isTrue();
            assertThat(item.isContainer()).isFalse();
            assertThat(item.isAnchored()).isFalse();
        });
        assertThat(templateBuilder().spawnRule(alwaysInScn1()).build().isCurrency()).isFalse();
    }

    @Test
    void spawnInside_mints_an_instance_inside_the_container_on_a_hit() {
        // A contained-only template (no spawn rule): the containment odds arrive as a value — they are the
        // container's authored fact — and a hit consumes one roll plus the id-body glyph picks.
        ItemTemplate template = templateBuilder().build();
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
        ItemTemplate template = templateBuilder().build();
        // A miss consumes only the roll — the empty pick script proves no id was minted.
        assertThat(template.spawnInside(new ScriptedDice().willRoll(false), new Chance(1, 45), ItemId.of("itm42")))
                .isEmpty();
    }

    @Test
    void spawnInside_rejects_null_collaborators() {
        ItemTemplate template = templateBuilder().build();
        // Caller bugs, not invalid domain construction — plain NullPointerExceptions, as for spawnInto.
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(null, new Chance(1, 45), ItemId.of("itm42")));
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(new ScriptedDice(), null, ItemId.of("itm42")));
        assertThatNullPointerException()
                .isThrownBy(() -> template.spawnInside(new ScriptedDice(), new Chance(1, 45), null));
    }
}
