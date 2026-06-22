package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A moment in game time, decomposed into a {@link GameCalendar}'s units — the pure positional result of
 * placing an elapsed-seconds instant on the calendar ({@link GameCalendar#placeInstant}). All fields are
 * <em>0-based indices</em>: {@code monthIndex 0} is the first month, {@code dayIndex 0} the first day of that
 * month, {@code hourIndex 0} the first hour of that day, and {@code secondOfHour} the offset within the hour.
 * {@code year} is the absolute game year (from {@link GameCalendar#EPOCH_YEAR}). Equality by value.
 *
 * <p>Turning these indices into the ordinals and clock labels a player reads ("the 1st day", "hour 12") is a
 * <em>rendering</em> concern and lives one layer out, never on this value — the same separation scenes keep
 * between the model and the terminal renderer. Likewise the month and weekday <em>names</em> are not held
 * here: they are resolved against the calendar ({@link GameCalendar#monthOf}, {@link GameCalendar#weekdayOf}),
 * so a date stays a minimal tuple of integers independent of any one calendar's named cycles.
 *
 * <p>Always-valid, but only to the depth this value can judge alone: the constructor rejects negative fields.
 * Whether an index is <em>within range</em> for a given calendar (e.g. {@code monthIndex} below the month
 * count) is guaranteed by the sole factory, {@link GameCalendar#placeInstant} — a {@code GameDate} is never
 * constructed except by placing an instant, so it carries no calendar reference to check bounds against.
 */
@Value
public class GameDate {

    private final int year;
    private final int monthIndex;
    private final int dayIndex;
    private final int hourIndex;
    private final int secondOfHour;

    public GameDate(int year, int monthIndex, int dayIndex, int hourIndex, int secondOfHour) {
        this.year = requireNonNegative(year, "game date year");
        this.monthIndex = requireNonNegative(monthIndex, "game date month index");
        this.dayIndex = requireNonNegative(dayIndex, "game date day index");
        this.hourIndex = requireNonNegative(hourIndex, "game date hour index");
        this.secondOfHour = requireNonNegative(secondOfHour, "game date second of hour");
    }

    private static int requireNonNegative(int value, String what) {
        if (value < 0) {
            throw new InvalidDomainObjectError(what + " must not be negative, got " + value);
        }
        return value;
    }
}
