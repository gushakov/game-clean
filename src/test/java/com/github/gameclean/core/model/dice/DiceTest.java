package com.github.gameclean.core.model.dice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the {@link Dice} mechanics that {@link AbstractDice} houses once for both implementations — the
 * odds interpretation ({@code roll} delegating to {@link Chance}) and the uniform scale-and-clamp pick that was
 * previously duplicated across {@code SpawnRule.pickScene} and {@code DayPhase.pickMessage}. The draw source is
 * pinned with a fixed-draw subclass so the mechanics are exercised deterministically. {@link SeededDice}
 * reproducibility (the determinism the old randomness port existed for) and {@link SystemDice}'s
 * draw-independent extremes are covered too.
 */
class DiceTest {

    /** An {@link AbstractDice} whose draws come from a fixed sequence — throws if over-pulled. */
    private static AbstractDice diceDrawing(double... draws) {
        return new AbstractDice() {
            private int next;

            @Override
            protected double nextDraw() {
                return draws[next++];
            }
        };
    }

    @Test
    void roll_hits_when_the_draw_is_below_the_chance_and_misses_at_or_above() {
        Chance evens = new Chance(1, 2); // probability 0.5
        assertThat(diceDrawing(0.49).roll(evens)).isTrue();
        assertThat(diceDrawing(0.5).roll(evens)).isFalse();
    }

    @Test
    void pick_scales_the_draw_across_the_options() {
        List<String> options = List.of("a", "b", "c");
        assertThat(diceDrawing(0.0).pick(options)).isEqualTo("a");
        assertThat(diceDrawing(0.5).pick(options)).isEqualTo("b");   // (int)(0.5 * 3) = 1
        assertThat(diceDrawing(0.999).pick(options)).isEqualTo("c"); // (int)(0.999 * 3) = 2
    }

    @Test
    void pick_clamps_a_draw_at_the_top_of_the_range_to_the_last_option() {
        assertThat(diceDrawing(1.0).pick(List.of("a", "b"))).isEqualTo("b");
    }

    @Test
    void pick_rejects_an_empty_list() {
        assertThatIllegalArgumentException().isThrownBy(() -> diceDrawing(0.0).pick(List.of()));
    }

    @Test
    void roll_rejects_a_null_chance() {
        assertThatNullPointerException().isThrownBy(() -> diceDrawing(0.0).roll(null));
    }

    @Test
    void seeded_dice_with_the_same_seed_produce_the_same_sequence() {
        Chance evens = new Chance(1, 2);
        List<String> options = List.of("a", "b", "c", "d");
        SeededDice one = new SeededDice(42);
        SeededDice two = new SeededDice(42);
        for (int i = 0; i < 20; i++) {
            assertThat(one.roll(evens)).isEqualTo(two.roll(evens));
            assertThat(one.pick(options)).isEqualTo(two.pick(options));
        }
    }

    @Test
    void system_dice_roll_is_deterministic_at_the_extremes_regardless_of_the_draw() {
        SystemDice dice = new SystemDice();
        assertThat(dice.roll(new Chance(0, 1))).isFalse();  // 0/1 never hits (draw is always < 1, never < 0)
        assertThat(dice.roll(new Chance(1, 1))).isTrue();   // 1/1 always hits (draw in [0,1) is always < 1)
        assertThat(dice.pick(List.of("only"))).isEqualTo("only");
    }
}
