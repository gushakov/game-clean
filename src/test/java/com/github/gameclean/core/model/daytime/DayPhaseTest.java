package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.ScriptedDice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DayPhase} — its construction gate (non-blank name, non-negative hour, at least one
 * non-blank message), value equality, and that the message pick delegates to the {@code Dice} over its
 * messages (the uniform scale-and-clamp mechanic lives on the dice and is tested in {@code DiceTest}). Whether
 * the hour is within a calendar's {@code hoursPerDay} is the source adapter's reconciliation job, not this
 * value's, so it is not tested here.
 */
class DayPhaseTest {

    @Test
    void exposes_its_fields() {
        DayPhase dawn = new DayPhase("Dawn", 6, List.of("Light returns.", "A new day."));
        assertThat(dawn.getName()).isEqualTo("Dawn");
        assertThat(dawn.getHourOfDay()).isEqualTo(6);
        assertThat(dawn.getMessages()).containsExactly("Light returns.", "A new day.");
    }

    @Test
    void rejects_a_blank_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhase("  ", 6, List.of("x")));
    }

    @Test
    void rejects_a_negative_hour() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhase("Dawn", -1, List.of("x")));
    }

    @Test
    void rejects_no_messages() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhase("Dawn", 6, List.of()));
    }

    @Test
    void rejects_a_blank_message() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new DayPhase("Dawn", 6, List.of("ok", "   ")));
    }

    @Test
    void picks_the_message_the_dice_selects_among_its_messages() {
        DayPhase dawn = new DayPhase("Dawn", 6, List.of("first", "second", "third"));
        assertThat(dawn.pickMessage(new ScriptedDice().willPick(0))).isEqualTo("first");
        assertThat(dawn.pickMessage(new ScriptedDice().willPick(2))).isEqualTo("third");
    }

    @Test
    void rejects_a_null_dice() {
        DayPhase dawn = new DayPhase("Dawn", 6, List.of("first", "second"));
        assertThatNullPointerException().isThrownBy(() -> dawn.pickMessage(null));
    }

    @Test
    void is_equal_by_value() {
        assertThat(new DayPhase("Dawn", 6, List.of("a")))
                .isEqualTo(new DayPhase("Dawn", 6, List.of("a")))
                .isNotEqualTo(new DayPhase("Dawn", 7, List.of("a")));
    }
}
