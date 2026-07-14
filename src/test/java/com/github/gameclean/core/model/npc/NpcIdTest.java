package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.dice.SeededDice;
import com.github.gameclean.core.model.id.Ids;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NpcId} — the reconstitution gate and {@link NpcId#mint(com.github.gameclean.core.model.dice.Dice)
 * mint}, the model minting its own identity by rolling a {@link com.github.gameclean.core.model.dice.Dice}, no
 * infrastructure id port. The prefix and composition are {@code NpcId}'s; the body encoding is {@link Ids}'.
 * The NPC twin of {@code ItemIdTest}.
 */
class NpcIdTest {

    @Test
    void mints_the_exact_id_the_dice_rolls() {
        // Body glyphs all at alphabet index 0 ('0'), prefixed with "npc".
        ScriptedDice dice = new ScriptedDice().willPick(0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(NpcId.mint(dice).getValue()).isEqualTo("npc00000000");
    }

    @Test
    void mints_a_structurally_valid_id_from_any_dice() {
        NpcId id = NpcId.mint(new SeededDice(7));
        assertThat(id.getValue())
                .startsWith(NpcId.PREFIX)
                .hasSize(NpcId.PREFIX.length() + Ids.BODY_LENGTH)
                .doesNotContainAnyWhitespaces();
    }

    @Test
    void rejects_a_null_dice() {
        assertThatNullPointerException().isThrownBy(() -> NpcId.mint(null));
    }

    @Test
    void rejects_a_body_without_the_prefix() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new NpcId("bogus"));
    }

    @Test
    void rejects_a_bare_prefix_with_no_body() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new NpcId("npc"));
    }

    @Test
    void accepts_a_short_authored_id() {
        assertThat(new NpcId("npc1").getValue()).isEqualTo("npc1");
    }
}
