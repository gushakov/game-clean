package com.github.gameclean.core.model.dice;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A test {@link Dice} that plays back a fixed script: {@link #roll(Chance)} returns the next scripted boolean
 * and {@link #pick(List)} returns the option at the next scripted index. Reading a scripted roll/pick reads
 * cleaner than computing what a {@link SeededDice} sequence would produce, and lets a test pin exact placements
 * and the draw-ordering (one roll per attempt, one pick per hit). Over-pulling — more rolls or picks than were
 * scripted — throws, so a test's roll/pick counts are pinned exactly, the way the old fixed draw-sequence did.
 *
 * <p>Scriptable in place: {@code dice.willRoll(true, false).willPick(0, 1)}.
 */
public class ScriptedDice implements Dice {

    private final Deque<Boolean> rolls = new ArrayDeque<>();
    private final Deque<Integer> picks = new ArrayDeque<>();

    /** Enqueue the outcomes the next {@code roll} calls will return, in order. */
    public ScriptedDice willRoll(boolean... outcomes) {
        for (boolean outcome : outcomes) {
            rolls.addLast(outcome);
        }
        return this;
    }

    /** Enqueue the option indices the next {@code pick} calls will select, in order. */
    public ScriptedDice willPick(int... indices) {
        for (int index : indices) {
            picks.addLast(index);
        }
        return this;
    }

    @Override
    public boolean roll(Chance chance) {
        if (rolls.isEmpty()) {
            throw new AssertionError("no scripted roll left");
        }
        return rolls.removeFirst();
    }

    @Override
    public <T> T pick(List<T> options) {
        if (picks.isEmpty()) {
            throw new AssertionError("no scripted pick left");
        }
        return options.get(picks.removeFirst());
    }
}
