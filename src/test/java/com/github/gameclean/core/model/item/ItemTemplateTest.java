package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ItemTemplate} — its always-valid construction (non-blank descriptions, a non-null spawn
 * rule) and {@code spawnInto}, which rolls the {@link SpawnRule}'s placements with a {@link ScriptedDice} and
 * builds one instance per placement, minting each instance's id from the <em>same</em> dice. The dice is the
 * only collaborator (no id supplier — the model mints its own ids), so per hit the script supplies a scene
 * pick followed by the id-body glyph picks, pinning the whole policy deterministically.
 */
class ItemTemplateTest {

    private static final String SHORT = "A rusty dagger.";
    private static final String FULL = "A plain iron dagger, rusty but usable.";

    private static ItemTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::new).toList());
        return new ItemTemplate(SHORT, FULL, rule);
    }

    @Test
    void rejects_a_blank_short_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(new SceneId("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new ItemTemplate("  ", FULL, rule));
    }

    @Test
    void rejects_a_blank_full_description() {
        SpawnRule rule = new SpawnRule(new Chance(1, 1), 1, List.of(new SceneId("scn1")));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new ItemTemplate(SHORT, "  ", rule));
    }

    @Test
    void rejects_a_null_spawn_rule() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new ItemTemplate(SHORT, FULL, null));
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

        assertThat(spawned).extracting(item -> item.getId().getValue()).containsExactly("itm00000000", "itm11111111");
        assertThat(spawned).extracting(item -> ((Location.OnGround) item.getLocation()).getScene().getValue())
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
}
