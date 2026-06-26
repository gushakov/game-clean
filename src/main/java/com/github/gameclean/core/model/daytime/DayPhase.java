package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Dice;
import lombok.Value;

import java.util.List;
import java.util.Objects;

/**
 * A named, recurring moment of the game day — dawn, dusk — that begins at a fixed hour-of-day and carries the
 * flavour lines the world announces when it arrives. A Value Object: always-valid (a non-blank name, a
 * non-negative hour index, and at least one non-blank message — a phase with nothing to say is meaningless)
 * and equal by value.
 *
 * <p><b>Authored time <em>narrative</em>, deliberately not calendar <em>structure</em>.</b> {@code DayPhase}
 * is co-authored with the calendar (both live in {@code calendar.yaml}) but is kept out of
 * {@link com.github.gameclean.core.model.calendar.GameCalendar}, which stays pure mixed-radix arithmetic and
 * named cycles. A day phase carries player-facing prose and is the seed of an asynchronous announcement — a
 * different kind of thing from "how many hours make a day". (design-notes §2 minimalism, §11.)
 *
 * <p>The hour is a bare 0-based index into the day, like {@link
 * com.github.gameclean.core.model.calendar.GameDate#getHourIndex()}. Whether it is <em>within range</em> for a
 * given calendar (below its {@code hoursPerDay}) is an inter-model consistency rule checked once at load — the
 * same split scenes use for their exit targets — so a {@code DayPhase} holds no calendar reference of its own.
 *
 * <p>It owns the whole message-selection policy: {@link #pickMessage(Dice)} chooses uniformly among its lines.
 * The choice is made by a {@link Dice} — the game's own source of chance (a domain collaborator, not a port) —
 * so the phase stays framework-free and deterministic under test (a
 * {@link com.github.gameclean.core.model.dice.SeededDice}, or a scripted dice, gives a fixed pick), exactly as
 * {@link com.github.gameclean.core.model.item.SpawnRule} rolls its placements with a dice.
 */
@Value
public class DayPhase {

    private final String name;
    private final int hourOfDay;
    private final List<String> messages;

    public DayPhase(String name, int hourOfDay, List<String> messages) {
        this.name = requireNonBlank(name, "day phase name");
        if (hourOfDay < 0) {
            throw new InvalidDomainObjectError("day phase hour of day must not be negative, got " + hourOfDay);
        }
        this.hourOfDay = hourOfDay;
        this.messages = List.copyOf(
                DomainValidation.requireNonNull(messages, "day phase '%s' messages must not be null".formatted(name)));
        if (this.messages.isEmpty()) {
            throw new InvalidDomainObjectError("day phase '%s' must have at least one message".formatted(name));
        }
        this.messages.forEach(message -> requireNonBlank(message, "day phase '%s' message".formatted(name)));
    }

    /**
     * Selects, uniformly, the line to announce for this phase by asking the given {@link Dice} to pick among
     * its messages. The uniform-pick mechanic (scale and clamp) lives on the dice, so the phase only names
     * <em>what</em> is being chosen among.
     *
     * @param dice the dice to pick with
     * @return the chosen message
     */
    public String pickMessage(Dice dice) {
        Objects.requireNonNull(dice, "dice must not be null");
        return dice.pick(messages);
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
