package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The authored fabric of game time: how many seconds make an hour, hours a day, days a month, and the ordered
 * named cycles of {@link Weekday}s and {@link Month}s. A Value Object — always-valid (every radix positive,
 * both cycles non-empty with unique names) and equal by value — that is the always-valid form of one
 * {@code calendar:} block in the seed.
 *
 * <p><strong>Time is a mixed-radix positional number.</strong> Because one real second is one game second,
 * the entire date is a pure function of how many seconds have elapsed since the epoch — there is no ticking
 * clock and no stored "current time". The radices are uniform (every month has {@link #daysPerMonth} days,
 * every year {@code months.size()} months), so placing an instant is repeated {@code divmod} down the ladder
 * seconds → hours → days → months → years. The calendar owns that computation ({@link #placeInstant}) because
 * it owns the radices: the use case orchestrates (fetch the elapsed seconds, ask the calendar to place them,
 * hand the result to a presenter) while the arithmetic lives on the model.
 *
 * <p><strong>The week is continuous.</strong> Which weekday a date falls on is the absolute day count since
 * the epoch modulo the week length ({@link #weekdayOf}), running unbroken across month and year boundaries —
 * <em>not</em> reset per month. The epoch day (the first day of {@link #EPOCH_YEAR}) is therefore the first
 * weekday in the cycle. When {@code daysPerMonth} is a whole number of weeks this coincides with a per-month
 * reset, but the continuous rule is the one that stays correct when it is not.
 *
 * <p>The persisted real epoch timestamp and a {@code now} time-source are deliberately out of this slice:
 * {@link #placeInstant} takes a plain elapsed-seconds {@code long}, so the whole calendar is unit-testable
 * with no port, clock, or database. The base year is fixed at {@link #EPOCH_YEAR} for now.
 */
@Value
public class GameCalendar {

    /** The game year the epoch instant (zero elapsed seconds) maps to — the first day of this year. */
    public static final int EPOCH_YEAR = 1000;

    int secondsPerHour;
    int hoursPerDay;
    int daysPerMonth;
    List<Weekday> week;
    List<Month> months;

    public GameCalendar(int secondsPerHour, int hoursPerDay, int daysPerMonth,
                        List<Weekday> week, List<Month> months) {
        this.secondsPerHour = requirePositive(secondsPerHour, "calendar seconds per hour");
        this.hoursPerDay = requirePositive(hoursPerDay, "calendar hours per day");
        this.daysPerMonth = requirePositive(daysPerMonth, "calendar days per month");
        this.week = requireNamedCycle(week, Weekday::getName, "calendar week", "weekday");
        this.months = requireNamedCycle(months, Month::getName, "calendar months", "month");
    }

    /**
     * Places an elapsed-seconds instant on this calendar, decomposing it into a {@link GameDate} by running
     * {@code divmod} down the radix ladder. The result's fields are 0-based indices; the {@code year} is
     * absolute ({@link #EPOCH_YEAR} plus the whole years elapsed). This is the calendar's core
     * side-effect-free function and the sole factory of {@link GameDate}.
     *
     * @param elapsedGameSeconds game seconds since the epoch — equal to real seconds since the epoch, since
     *                           one real second is one game second; must not be negative
     * @return the instant decomposed into this calendar's units
     * @throws InvalidDomainObjectError if {@code elapsedGameSeconds} is negative
     */
    public GameDate placeInstant(long elapsedGameSeconds) {
        if (elapsedGameSeconds < 0) {
            throw new InvalidDomainObjectError(
                    "elapsed game seconds must not be negative, got " + elapsedGameSeconds);
        }
        long secondsPerDay = (long) secondsPerHour * hoursPerDay;
        long secondsPerMonth = secondsPerDay * daysPerMonth;
        long secondsPerYear = secondsPerMonth * months.size();

        int year = EPOCH_YEAR + (int) (elapsedGameSeconds / secondsPerYear);
        long withinYear = elapsedGameSeconds % secondsPerYear;
        int monthIndex = (int) (withinYear / secondsPerMonth);
        long withinMonth = withinYear % secondsPerMonth;
        int dayIndex = (int) (withinMonth / secondsPerDay);
        long withinDay = withinMonth % secondsPerDay;
        int hourIndex = (int) (withinDay / secondsPerHour);
        int secondOfHour = (int) (withinDay % secondsPerHour);

        return new GameDate(year, monthIndex, dayIndex, hourIndex, secondOfHour);
    }

    /**
     * The absolute game hour an instant falls in: the monotonic count of whole game hours since the epoch,
     * running unbroken across day, month and year boundaries — the <em>un-wrapped</em> companion to
     * {@link #placeInstant}'s cyclic {@link GameDate#getHourIndex() hourIndex}. Where {@code hourIndex} resets
     * each day (so a phase at hour-of-day 6 recurs as hour 6 every day), this keeps climbing (day 0's hour 6 is
     * absolute hour 6, day 1's is {@code 6 + hoursPerDay}, …), which is exactly what an ordering or
     * once-per-occurrence dedup key needs. The two are consistent by construction:
     * {@code placeInstant(e).getHourIndex() == absoluteHourOf(e) % hoursPerDay}.
     *
     * <p>The calendar owns this arithmetic for the same reason it owns {@link #placeInstant}: it owns the
     * {@code secondsPerHour} radix. A use case orchestrates — fetch the elapsed seconds, ask the calendar for
     * the absolute hour — rather than dividing by the radix itself.
     *
     * @param elapsedGameSeconds game seconds since the epoch (must not be negative)
     * @return the number of whole game hours elapsed since the epoch
     * @throws InvalidDomainObjectError if {@code elapsedGameSeconds} is negative
     */
    public long absoluteHourOf(long elapsedGameSeconds) {
        if (elapsedGameSeconds < 0) {
            throw new InvalidDomainObjectError(
                    "elapsed game seconds must not be negative, got " + elapsedGameSeconds);
        }
        return elapsedGameSeconds / secondsPerHour;
    }

    /**
     * The {@link Month} a date falls in — its {@code monthIndex} resolved against this calendar's month cycle.
     * The index is trusted to be in range because {@link GameDate}s are minted only by {@link #placeInstant}.
     *
     * @param date a date produced by this calendar
     * @return the named month at the date's month index
     */
    public Month monthOf(GameDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return months.get(date.getMonthIndex());
    }

    /**
     * The {@link Weekday} a date falls on, under the continuous week: the absolute number of days from the
     * epoch to the date, modulo the week length. Computed from the date's positional fields (plus this
     * calendar's radices) rather than stored on the date, so the weekday is a total derivation that cannot
     * drift from the year/month/day it is read off.
     *
     * @param date a date produced by this calendar
     * @return the named weekday the date falls on
     */
    public Weekday weekdayOf(GameDate date) {
        Objects.requireNonNull(date, "date must not be null");
        long absoluteDays = ((long) (date.getYear() - EPOCH_YEAR) * months.size() + date.getMonthIndex())
                * daysPerMonth + date.getDayIndex();
        return week.get((int) (absoluteDays % week.size()));
    }

    private static int requirePositive(int value, String what) {
        if (value < 1) {
            throw new InvalidDomainObjectError(what + " must be positive, got " + value);
        }
        return value;
    }

    private static <T> List<T> requireNamedCycle(List<T> cycle, Function<T, String> name,
                                                 String what, String element) {
        List<T> copy = List.copyOf(DomainValidation.requireNonNull(cycle, what + " must not be null"));
        if (copy.isEmpty()) {
            throw new InvalidDomainObjectError(what + " must have at least one " + element);
        }
        if (copy.stream().map(name).distinct().count() != copy.size()) {
            throw new InvalidDomainObjectError(what + " names must be unique");
        }
        return copy;
    }
}
