package com.github.gameclean.core.model.dice;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private final Deque<Integer> dieFaces = new ArrayDeque<>();
    private final Deque<List<Object>> shuffleFronts = new ArrayDeque<>();

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

    /** Enqueue the face values the next {@code rollDie} calls will return, in order. */
    public ScriptedDice willRollDie(int... faces) {
        for (int face : faces) {
            dieFaces.addLast(face);
        }
        return this;
    }

    /**
     * Enqueue the items the next {@code shuffle} call must move to the front, in order; the remaining items
     * keep their input order behind them. Lets a test rig exactly the cards a blackjack deal will hand out
     * (e.g. an ace and a king first — a natural) while staying deterministic about the rest.
     */
    public ScriptedDice willShuffleToFront(Object... front) {
        shuffleFronts.addLast(List.of(front));
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

    @Override
    public int rollDie(int sides) {
        if (dieFaces.isEmpty()) {
            throw new AssertionError("no scripted die roll left");
        }
        return dieFaces.removeFirst();
    }

    @Override
    public <T> List<T> shuffle(List<T> items) {
        // Identity shuffle by default: the scripted dice never reorder, so assertions read literally off the
        // input (e.g. the canonical deck order in a deal test). A scripted front (willShuffleToFront) instead
        // pulls the named items forward, keeping the rest in input order.
        if (shuffleFronts.isEmpty()) {
            return List.copyOf(items);
        }
        List<Object> front = shuffleFronts.removeFirst();
        List<T> rest = new ArrayList<>(items);
        List<T> shuffled = new ArrayList<>();
        for (Object item : front) {
            if (!rest.remove(item)) {
                throw new AssertionError("scripted shuffle-front item not among the shuffled items: " + item);
            }
            @SuppressWarnings("unchecked")
            T typed = (T) item;
            shuffled.add(typed);
        }
        shuffled.addAll(rest);
        return List.copyOf(shuffled);
    }
}
