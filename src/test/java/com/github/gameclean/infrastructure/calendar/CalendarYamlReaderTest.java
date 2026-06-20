package com.github.gameclean.infrastructure.calendar;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.Month;
import com.github.gameclean.core.model.calendar.Weekday;
import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.model.daytime.DayPhaseSchedule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CalendarYamlReader}: it parses the authored calendar document and builds the always-valid
 * {@link GameCalendar}. The first test reads the <em>real</em> authored {@code world/calendar.yaml} from the
 * classpath, pinning the radices and named cycles the game actually ships with; the others confirm the reader
 * surfaces malformed and domain-invalid documents as failures (the calendar's fail-fast-at-load shape).
 */
class CalendarYamlReaderTest {

    private final CalendarYamlReader reader = new CalendarYamlReader();

    @Test
    void readsTheAuthoredCalendarResource() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/world/calendar.yaml")) {
            assertThat(in).as("authored calendar resource on the classpath").isNotNull();
            GameCalendar calendar = reader.read(in);

            assertThat(calendar.getSecondsPerHour()).isEqualTo(300);
            assertThat(calendar.getHoursPerDay()).isEqualTo(24);
            assertThat(calendar.getDaysPerMonth()).isEqualTo(30);
            assertThat(calendar.getWeek()).extracting(Weekday::getName)
                    .containsExactly("Elenya", "Anarya", "Isilya", "Alduya", "Menelya");
            assertThat(calendar.getMonths()).extracting(Month::getName)
                    .containsExactly("Aelorin", "Sylvael", "Calivorn", "Thalinde", "Evaniel",
                            "Miraleth", "Faerundel", "Veloris", "Aelindra", "Sorivael");
        }
    }

    @Test
    void readsTheAuthoredDayPhases() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/world/calendar.yaml")) {
            assertThat(in).as("authored calendar resource on the classpath").isNotNull();
            DayPhaseSchedule schedule = reader.readDayPhases(in);

            assertThat(schedule.getPhases()).extracting(DayPhase::getName).containsExactly("Dawn", "Dusk");
            assertThat(schedule.phaseBeginningAt(6)).map(DayPhase::getName).contains("Dawn");
            assertThat(schedule.phaseBeginningAt(18)).map(DayPhase::getName).contains("Dusk");
            assertThat(schedule.phaseBeginningAt(6)).get()
                    .extracting(p -> p.getMessages().isEmpty()).isEqualTo(false);
        }
    }

    @Test
    void anAbsentDayPhasesBlockYieldsAnEmptySchedule() {
        // A valid calendar document with no dayPhases: section — the world announces no day phases.
        String yaml = """
                secondsPerHour: 300
                hoursPerDay: 24
                daysPerMonth: 30
                week:
                  - name: Elenya
                    description: guidance
                months:
                  - name: Aelorin
                    description: a
                """;
        assertThat(reader.readDayPhases(stream(yaml)).getPhases()).isEmpty();
    }

    @Test
    void rejectsADocumentWithDomainInvalidRadices() {
        // Zero secondsPerHour fails the GameCalendar always-valid gate during construction.
        String yaml = """
                secondsPerHour: 0
                hoursPerDay: 24
                daysPerMonth: 30
                week:
                  - name: Elenya
                    description: guidance
                months:
                  - name: Aelorin
                    description: a
                """;
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> reader.read(stream(yaml)));
    }

    @Test
    void rejectsADocumentWhoseWeekIsNotASequence() {
        String yaml = """
                secondsPerHour: 300
                hoursPerDay: 24
                daysPerMonth: 30
                week: not-a-list
                months:
                  - name: Aelorin
                    description: a
                """;
        assertThatThrownBy(() -> reader.read(stream(yaml))).isInstanceOf(IllegalArgumentException.class);
    }

    private static InputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
