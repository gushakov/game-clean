package com.github.gameclean.infrastructure.calendar;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.daytime.DayPhaseSchedule;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsError;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.daytime.DayPhaseScheduleSourceOperationsError;
import com.github.gameclean.core.port.daytime.DayPhaseScheduleSourceOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Driven adapter over the authored calendar YAML, implementing <em>both</em> time-fabric source ports: it
 * serves the {@link GameCalendar} ({@link CalendarSourceOperationsOutputPort}) and the {@link DayPhaseSchedule}
 * ({@link DayPhaseScheduleSourceOperationsOutputPort}). Both live in the same {@code calendar.yaml}, so one
 * adapter owns reading it; the two distinct port contracts (and their distinct boundary errors) reflect that
 * the calendar and the day phases are different concerns even when co-authored. All access to the YAML parsing
 * machinery lives here, confined to the infrastructure ring, behind the ports.
 *
 * <p><b>Loaded once, at boot.</b> Both the calendar and the day-phase schedule are read and constructed in the
 * constructor and cached for the life of the application — immutable authored content, not persisted and not
 * per-interaction input (load each boot rather than persist). So {@link #loadCalendar()} and
 * {@link #loadDayPhases()} hand back the cached, already-valid models on every call. Failing in the
 * constructor means a malformed document <em>fails fast at startup</em> rather than on the first interaction.
 *
 * <p><b>Inter-model reconciliation, fail-fast.</b> A day phase's hour-of-day is meaningful only against the
 * calendar's {@code hoursPerDay}; an out-of-range hour would silently never fire. So after both are read, the
 * constructor checks every phase hour is within range and refuses to come up otherwise — the day-phase
 * analogue of the calendar's own always-valid gate, kept here (in the adapter that has both) rather than on
 * the calendar-agnostic {@code DayPhaseSchedule}.
 *
 * <p>Every technical failure — an unreadable resource, a malformed document, or authored content that fails
 * the always-valid gate — is translated into the unchecked error the relevant port contract declares
 * ({@link CalendarSourceOperationsError} / {@link DayPhaseScheduleSourceOperationsError}).
 */
@Component
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class YamlCalendarSource implements CalendarSourceOperationsOutputPort,
        DayPhaseScheduleSourceOperationsOutputPort {

    GameCalendar calendar;
    DayPhaseSchedule dayPhases;

    public YamlCalendarSource(CalendarYamlReader reader, GameConfigurationProperties properties) {
        Resource location = properties.getTime().getCalendarLocation();

        log.info("[Calendar] Loading the authored calendar from {}", location);
        try (InputStream in = location.getInputStream()) {
            this.calendar = reader.read(in);
        } catch (IOException | RuntimeException e) {
            throw new CalendarSourceOperationsError(
                    "could not read, parse, or construct the calendar from %s".formatted(location), e);
        }

        log.info("[Calendar] Loading the authored day phases from {}", location);
        DayPhaseSchedule schedule;
        try (InputStream in = location.getInputStream()) {
            schedule = reader.readDayPhases(in);
        } catch (IOException | RuntimeException e) {
            throw new DayPhaseScheduleSourceOperationsError(
                    "could not read, parse, or construct the day phases from %s".formatted(location), e);
        }
        reconcileWithCalendar(schedule);
        this.dayPhases = schedule;
    }

    @Override
    public GameCalendar loadCalendar() {
        return calendar;
    }

    @Override
    public DayPhaseSchedule loadDayPhases() {
        return dayPhases;
    }

    /** Fail-fast inter-model check: every phase begins at an hour the calendar's day actually has. */
    private void reconcileWithCalendar(DayPhaseSchedule schedule) {
        schedule.getPhases().forEach(phase -> {
            if (phase.getHourOfDay() >= calendar.getHoursPerDay()) {
                throw new DayPhaseScheduleSourceOperationsError(
                        "day phase '%s' begins at hour %d, beyond the calendar's %d hours per day".formatted(
                                phase.getName(), phase.getHourOfDay(), calendar.getHoursPerDay()));
            }
        });
    }
}
