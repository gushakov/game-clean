package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.dice.SeededDice;
import com.github.gameclean.core.model.id.Ids;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ItemId#mint(com.github.gameclean.core.model.dice.Dice) mint} — the model minting its own
 * identity by rolling a {@link com.github.gameclean.core.model.dice.Dice}, no infrastructure id port. The
 * prefix and composition are {@code ItemId}'s; the body encoding is {@link Ids}'.
 */
class ItemIdTest {

    @Test
    void mints_the_exact_id_the_dice_rolls() {
        // Body glyphs all at alphabet index 0 ('0'), prefixed with "itm".
        ScriptedDice dice = new ScriptedDice().willPick(0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(ItemId.mint(dice).getValue()).isEqualTo("itm00000000");
    }

    @Test
    void mints_a_structurally_valid_id_from_any_dice() {
        ItemId id = ItemId.mint(new SeededDice(7));
        assertThat(id.getValue())
                .startsWith(ItemId.PREFIX)
                .hasSize(ItemId.PREFIX.length() + Ids.BODY_LENGTH)
                .doesNotContainAnyWhitespaces();
    }

    @Test
    void rejects_a_null_dice() {
        assertThatNullPointerException().isThrownBy(() -> ItemId.mint(null));
    }
}
