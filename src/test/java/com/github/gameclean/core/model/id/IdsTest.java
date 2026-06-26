package com.github.gameclean.core.model.id;

import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.dice.SeededDice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Ids} — the model's single knower of the generated-id-body encoding. The alphabet/length
 * mechanics are pinned here: a scripted dice produces an exact body (proving "{@link Ids#BODY_LENGTH} glyphs,
 * each {@code dice.pick(ALPHABET)}"), a seeded dice produces a well-formed and reproducible one (the
 * determinism the deleted randomness/id ports existed for, now from a domain abstraction).
 */
class IdsTest {

    @Test
    void rolls_a_body_of_the_configured_length_from_the_alphabet() {
        String body = Ids.randomBody(new SeededDice(1));
        assertThat(body).hasSize(Ids.BODY_LENGTH);
        for (char glyph : body.toCharArray()) {
            assertThat(Ids.ALPHABET).contains(glyph);
        }
    }

    @Test
    void rolls_the_exact_body_the_dice_picks() {
        // Pick alphabet indices 0..7 -> "01234567" (digits lead the [0-9A-Za-z] alphabet).
        ScriptedDice dice = new ScriptedDice().willPick(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(Ids.randomBody(dice)).isEqualTo("01234567");
    }

    @Test
    void is_reproducible_for_a_given_seed() {
        assertThat(Ids.randomBody(new SeededDice(42))).isEqualTo(Ids.randomBody(new SeededDice(42)));
    }

    @Test
    void rejects_a_null_dice() {
        assertThatNullPointerException().isThrownBy(() -> Ids.randomBody(null));
    }
}
