package com.github.gameclean.core.model.calendar;

import java.util.List;

/**
 * The canonical test calendar and its named cycles, shared by the calendar model test and every use case test
 * that needs a calendar to place an instant. Pins the worked-example radices from the design discussion — 300s
 * hours, 24h days, 30-day months, 5 weekdays, 10 months — so a fixed elapsed-seconds value always lands on a
 * known {@link GameDate}.
 *
 * <p>Fixture names are ASCII stand-ins; the real (accented) names are authored in the seed. Only the
 * <em>names</em> are ever asserted ({@link GameCalendar#monthOf}, {@link GameCalendar#weekdayOf}), so the
 * flavour descriptions are illustrative.
 */
public final class CalendarFixtures {

    private CalendarFixtures() {
    }

    public static List<Weekday> fiveWeekdays() {
        return List.of(
                new Weekday("Elenya", "guidance"),
                new Weekday("Anarya", "vitality"),
                new Weekday("Isilya", "reflection"),
                new Weekday("Alduya", "growth"),
                new Weekday("Menelya", "higher matters"));
    }

    public static List<Month> tenMonths() {
        return List.of(
                new Month("Aelorin", "The Silver Wind Returns"),
                new Month("Sylvael", "The First Bloom Stirs"),
                new Month("Calivorn", "The Waters Awaken"),
                new Month("Thalinde", "The Light Grows Long"),
                new Month("Evaniel", "The Golden Tide"),
                new Month("Miraleth", "The Amber Canopy"),
                new Month("Faerundel", "The Drifting Mists"),
                new Month("Veloris", "The Cold Moon Rises"),
                new Month("Aelindra", "The Deep Frost"),
                new Month("Sorivael", "The Returning Light"));
    }

    /** The calendar from the design discussion: 300s hours, 24h days, 30-day months, 5 weekdays, 10 months. */
    public static GameCalendar standard() {
        return new GameCalendar(300, 24, 30, fiveWeekdays(), tenMonths());
    }
}
