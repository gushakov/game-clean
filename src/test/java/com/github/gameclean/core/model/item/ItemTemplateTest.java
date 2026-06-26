package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ItemTemplate} — its always-valid construction (non-blank descriptions, a non-null spawn
 * rule) and {@code spawnInto}, which rolls the {@link SpawnRule}'s placements with a {@link ScriptedDice} and
 * builds one instance per placement. The dice is a domain collaborator and the id source a plain supplier, so
 * the whole policy is exercised here deterministically; pinning it in isolation is the testability win of
 * keeping spawning on the model.
 */
class ItemTemplateTest {

    private static final String SHORT = "A rusty dagger.";
    private static final String FULL = "A plain iron dagger, rusty but usable.";

    private static ItemTemplate template(int numerator, int denominator, int maxTries, String... candidateScenes) {
        SpawnRule rule = new SpawnRule(new Chance(numerator, denominator), maxTries,
                Arrays.stream(candidateScenes).map(SceneId::new).toList());
        return new ItemTemplate(SHORT, FULL, rule);
    }

    /** An id source over a fixed sequence of full ids — throws if more ids are pulled than given. */
    private static Supplier<ItemId> ids(String... values) {
        int[] next = {0};
        return () -> new ItemId(values[next[0]++]);
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
    void spawns_one_instance_per_placement_with_fresh_ids_copied_descriptions_and_locations() {
        ItemTemplate template = template(1, 1, 2, "scn1", "scn2");
        // attempt 0: hit, pick index 0 -> scn1; attempt 1: hit, pick index 1 -> scn2
        ScriptedDice dice = new ScriptedDice().willRoll(true, true).willPick(0, 1);
        List<Item> spawned = template.spawnInto(ids("itmAAA", "itmBBB"), dice);

        assertThat(spawned).extracting(item -> item.getId().getValue()).containsExactly("itmAAA", "itmBBB");
        assertThat(spawned).extracting(item -> item.getLocation().getValue()).containsExactly("scn1", "scn2");
        assertThat(spawned).allSatisfy(item -> {
            assertThat(item.getShortDescription()).isEqualTo(SHORT);
            assertThat(item.getFullDescription()).isEqualTo(FULL);
        });
    }

    @Test
    void spawns_nothing_and_pulls_no_id_when_no_attempt_hits() {
        ItemTemplate template = template(0, 1, 2, "scn1");
        Supplier<ItemId> noIdExpected = () -> {
            throw new AssertionError("no id should be pulled for a missed attempt");
        };
        assertThat(template.spawnInto(noIdExpected, new ScriptedDice().willRoll(false, false))).isEmpty();
    }

    @Test
    void rejects_a_null_id_source() {
        ItemTemplate template = template(1, 1, 1, "scn1");
        // A null collaborator to a behaviour method is a caller bug, not invalid domain construction —
        // it stays a plain NullPointerException, unlike the value-object constructors above.
        assertThatNullPointerException().isThrownBy(() -> template.spawnInto(null, new ScriptedDice()));
    }
}
