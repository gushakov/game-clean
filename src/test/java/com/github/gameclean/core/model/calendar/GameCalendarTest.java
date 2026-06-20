package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GameCalendar} — its construction invariants and the time arithmetic it owns
 * ({@link GameCalendar#placeInstant}, {@link GameCalendar#monthOf}, {@link GameCalendar#weekdayOf}). The two
 * worked examples from the design discussion are pinned here, as are the radix boundaries (epoch, year
 * rollover, the last second of a year) and — the discriminating case — a calendar whose month is not a whole
 * number of weeks, which a per-month-reset weekday rule would get wrong but the continuous rule gets right.
 *
 * <p>Fixture names are ASCII stand-ins; the real (accented) names are authored in the seed.
 */
class GameCalendarTest {

    private static final long SECONDS_PER_HOUR = 300;
    private static final long HOURS_PER_DAY = 24;
    private static final long DAYS_PER_MONTH = 30;
    private static final long SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;     // 7_200
    private static final long SECONDS_PER_MONTH = SECONDS_PER_DAY * DAYS_PER_MONTH;   // 216_000
    private static final long SECONDS_PER_YEAR = SECONDS_PER_MONTH * 10;             // 2_160_000

    private static List<Weekday> fiveWeekdays() {
        return List.of(
                new Weekday("Elenya", "guidance"),
                new Weekday("Anarya", "vitality"),
                new Weekday("Isilya", "reflection"),
                new Weekday("Alduya", "growth"),
                new Weekday("Menelya", "higher matters"));
    }

    private static List<Month> tenMonths() {
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
    private static GameCalendar standard() {
        return new GameCalendar(300, 24, 30, fiveWeekdays(), tenMonths());
    }

    // --- construction invariants -------------------------------------------------------------------------

    @Test
    void rejects_a_non_positive_seconds_per_hour() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(0, 24, 30, fiveWeekdays(), tenMonths()));
    }

    @Test
    void rejects_a_non_positive_hours_per_day() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 0, 30, fiveWeekdays(), tenMonths()));
    }

    @Test
    void rejects_a_non_positive_days_per_month() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, -1, fiveWeekdays(), tenMonths()));
    }

    @Test
    void rejects_a_null_week() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, 30, null, tenMonths()));
    }

    @Test
    void rejects_an_empty_week() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, 30, List.of(), tenMonths()));
    }

    @Test
    void rejects_an_empty_months_list() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, 30, fiveWeekdays(), List.of()));
    }

    @Test
    void rejects_duplicate_weekday_names() {
        List<Weekday> dupes = List.of(new Weekday("Elenya", "a"), new Weekday("Elenya", "b"));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, 30, dupes, tenMonths()));
    }

    @Test
    void rejects_duplicate_month_names() {
        List<Month> dupes = List.of(new Month("Aelorin", "a"), new Month("Aelorin", "b"));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new GameCalendar(300, 24, 30, fiveWeekdays(), dupes));
    }

    // --- placeInstant: the worked examples ---------------------------------------------------------------

    @Test
    void places_the_epoch_instant_at_the_first_moment_of_year_1000() {
        GameCalendar calendar = standard();
        GameDate epoch = calendar.placeInstant(0);
        assertThat(epoch).isEqualTo(new GameDate(GameCalendar.EPOCH_YEAR, 0, 0, 0, 0));
        assertThat(calendar.monthOf(epoch).getName()).isEqualTo("Aelorin");
        assertThat(calendar.weekdayOf(epoch).getName()).isEqualTo("Elenya");
    }

    @Test
    void places_one_real_hour_at_hour_twelve_of_the_first_day() {
        // 3_600s / 300 = 12 game hours into the first day of Aelorin, year 1000 (Elenya).
        GameCalendar calendar = standard();
        GameDate date = calendar.placeInstant(3_600);
        assertThat(date).isEqualTo(new GameDate(1000, 0, 0, 12, 0));
        assertThat(calendar.monthOf(date).getName()).isEqualTo("Aelorin");
        assertThat(calendar.weekdayOf(date).getName()).isEqualTo("Elenya");
    }

    @Test
    void places_ten_real_days_at_the_first_moment_of_the_fifth_month() {
        // 864_000s = 120 game days = exactly 4 months elapsed -> start of the 5th month, Evaniel, year 1000.
        GameCalendar calendar = standard();
        GameDate date = calendar.placeInstant(864_000);
        assertThat(date).isEqualTo(new GameDate(1000, 4, 0, 0, 0));
        assertThat(calendar.monthOf(date).getName()).isEqualTo("Evaniel");
        assertThat(calendar.weekdayOf(date).getName()).isEqualTo("Elenya");
    }

    // --- placeInstant: radix boundaries ------------------------------------------------------------------

    @Test
    void rolls_into_the_next_year_after_a_full_year_of_seconds() {
        assertThat(standard().placeInstant(SECONDS_PER_YEAR)).isEqualTo(new GameDate(1001, 0, 0, 0, 0));
    }

    @Test
    void places_the_last_second_of_the_year_at_the_final_index_of_every_unit() {
        // One second before the year rolls over: last month (9), last day (29), last hour (23), last second (299).
        assertThat(standard().placeInstant(SECONDS_PER_YEAR - 1))
                .isEqualTo(new GameDate(1000, 9, 29, 23, 299));
    }

    @Test
    void splits_a_sub_hour_remainder_into_the_second_of_hour() {
        // 12 hours and 150 seconds into day 0.
        assertThat(standard().placeInstant(12 * SECONDS_PER_HOUR + 150))
                .isEqualTo(new GameDate(1000, 0, 0, 12, 150));
    }

    @Test
    void rejects_a_negative_elapsed() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> standard().placeInstant(-1));
    }

    // --- absoluteHourOf: the monotonic hour-since-epoch --------------------------------------------------

    @Test
    void absoluteHourOf_counts_whole_game_hours_since_the_epoch() {
        GameCalendar calendar = standard();
        assertThat(calendar.absoluteHourOf(0)).isZero();
        assertThat(calendar.absoluteHourOf(6 * SECONDS_PER_HOUR)).isEqualTo(6L);          // day 0, hour 6 (dawn)
        assertThat(calendar.absoluteHourOf(6 * SECONDS_PER_HOUR + 299)).isEqualTo(6L);     // floored within the hour
    }

    @Test
    void absoluteHourOf_keeps_climbing_across_days_while_hourIndex_wraps() {
        GameCalendar calendar = standard();
        long dayOneDawn = (HOURS_PER_DAY + 6) * SECONDS_PER_HOUR;        // day 1, hour-of-day 6
        long absoluteHour = calendar.absoluteHourOf(dayOneDawn);
        int hourIndex = calendar.placeInstant(dayOneDawn).getHourIndex();

        assertThat(absoluteHour).isEqualTo(30L);                         // 24 + 6, not 6
        assertThat(hourIndex).isEqualTo(6);                              // the cyclic hour still wraps
        assertThat(absoluteHour % HOURS_PER_DAY).isEqualTo((long) hourIndex);   // consistent by construction
    }

    @Test
    void absoluteHourOf_rejects_a_negative_elapsed() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> standard().absoluteHourOf(-1));
    }

    // --- the continuous week -----------------------------------------------------------------------------

    @Test
    void weekday_runs_continuously_across_a_month_whose_length_is_not_a_whole_number_of_weeks() {
        // 31-day months, 5-day weeks: the 2nd month starts on absolute day 31, and 31 % 5 == 1, so its first
        // day is the SECOND weekday — not the first, as a per-month reset would (wrongly) give.
        GameCalendar calendar = new GameCalendar(300, 24, 31, fiveWeekdays(),
                List.of(new Month("First", "a"), new Month("Second", "b")));
        long secondsPerMonth = 300L * 24 * 31;
        GameDate firstOfSecondMonth = calendar.placeInstant(secondsPerMonth);

        assertThat(firstOfSecondMonth).isEqualTo(new GameDate(1000, 1, 0, 0, 0));
        assertThat(calendar.weekdayOf(firstOfSecondMonth).getName()).isEqualTo("Anarya");
    }

    @Test
    void weekday_wraps_through_the_cycle_day_by_day() {
        GameCalendar calendar = standard();
        // Five consecutive days from the epoch cover the whole week, then wrap back to the first.
        assertThat(calendar.weekdayOf(calendar.placeInstant(0)).getName()).isEqualTo("Elenya");
        assertThat(calendar.weekdayOf(calendar.placeInstant(SECONDS_PER_DAY)).getName()).isEqualTo("Anarya");
        assertThat(calendar.weekdayOf(calendar.placeInstant(4 * SECONDS_PER_DAY)).getName()).isEqualTo("Menelya");
        assertThat(calendar.weekdayOf(calendar.placeInstant(5 * SECONDS_PER_DAY)).getName()).isEqualTo("Elenya");
    }

    @Test
    void weekday_continues_unbroken_across_a_year_boundary() {
        // 30-day months x 10 months = 300 days per year; 300 % 5 == 0, so year 1001 day 0 is again Elenya,
        // and the day after the last day of year 1000 follows the cycle without a reset.
        GameCalendar calendar = standard();
        assertThat(calendar.weekdayOf(calendar.placeInstant(SECONDS_PER_YEAR)).getName()).isEqualTo("Elenya");
    }

    // --- behaviour-method argument guards ----------------------------------------------------------------

    @Test
    void monthOf_and_weekdayOf_reject_a_null_date() {
        // A null collaborator to a behaviour method is a caller bug -> plain NullPointerException, unlike the
        // construction invariants above.
        GameCalendar calendar = standard();
        assertThatNullPointerException().isThrownBy(() -> calendar.monthOf(null));
        assertThatNullPointerException().isThrownBy(() -> calendar.weekdayOf(null));
    }
}
