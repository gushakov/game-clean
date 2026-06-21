package com.github.gameclean.infrastructure.calendar;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.Month;
import com.github.gameclean.core.model.calendar.Weekday;
import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.model.daytime.DayPhaseSchedule;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the authored calendar YAML and builds the always-valid {@link GameCalendar}. It parses YAML into
 * plain maps/lists (SnakeYAML with a {@link SafeConstructor}, the recommended safe-loading mode) and hand-maps
 * them to the radices and the {@link Weekday} / {@link Month} cycles.
 *
 * <p><b>Unlike the world-seed reader, this one <em>does</em> touch the domain model</b> — it constructs the
 * {@code GameCalendar} here. That is the calendar's deliberate boundary shape (design-notes §3/§11): the
 * calendar is loaded once at boot and held in memory (not persisted, not per-interaction input), so it
 * behaves like configuration, and its source port returns a <em>valid model</em> rather than an
 * invalid-capable carrier. A malformed calendar therefore fails fast at load: a structurally-wrong document
 * fails here like malformed YAML, and authored radices/cycles that break the always-valid gate throw
 * {@code InvalidDomainObjectError} from the constructors — both wrapped into the port's error by
 * {@link YamlCalendarSource}.
 *
 * <p>It also reads the {@code dayPhases:} block of the same document ({@link #readDayPhases}) into a
 * {@link DayPhaseSchedule}. Day phases are co-authored with the calendar but kept a distinct model (they
 * carry narrative, not radices), so they parse into their own carrier here; {@code YamlCalendarSource}
 * reconciles each phase hour against the calendar's {@code hoursPerDay} after both are read.
 */
@Component
public class CalendarYamlReader {

    /**
     * Parses the given YAML stream and builds the calendar. The document carries the scalar radices
     * ({@code secondsPerHour}, {@code hoursPerDay}, {@code daysPerMonth}) and the {@code week:} / {@code months:}
     * sequences of {@code {name, description}} entries.
     *
     * @param yamlStream the authored calendar document
     * @return the always-valid {@link GameCalendar}
     */
    public GameCalendar read(InputStream yamlStream) {
        Objects.requireNonNull(yamlStream, "yaml stream must not be null");

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(yamlStream);
        Objects.requireNonNull(root, "calendar document is empty");

        int secondsPerHour = asInt(root.get("secondsPerHour"), "secondsPerHour");
        int hoursPerDay = asInt(root.get("hoursPerDay"), "hoursPerDay");
        int daysPerMonth = asInt(root.get("daysPerMonth"), "daysPerMonth");
        List<Weekday> week = parseWeek(root.get("week"));
        List<Month> months = parseMonths(root.get("months"));

        return new GameCalendar(secondsPerHour, hoursPerDay, daysPerMonth, week, months);
    }

    /**
     * Parses the {@code dayPhases:} block of the given YAML stream into the day-phase schedule. Each entry
     * carries a {@code name}, an {@code hour} (0-based hour-of-day index), and a {@code messages:} sequence of
     * flavour lines. The block is optional: an absent or empty {@code dayPhases:} yields an empty schedule
     * (the world announces no day phases).
     *
     * @param yamlStream the authored calendar document
     * @return the always-valid {@link DayPhaseSchedule}
     */
    public DayPhaseSchedule readDayPhases(InputStream yamlStream) {
        Objects.requireNonNull(yamlStream, "yaml stream must not be null");

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(yamlStream);
        Objects.requireNonNull(root, "calendar document is empty");

        return new DayPhaseSchedule(parseDayPhases(root.get("dayPhases")));
    }

    private static List<DayPhase> parseDayPhases(Object node) {
        if (node == null) {
            return List.of();
        }
        List<DayPhase> phases = new ArrayList<>();
        for (Map<String, Object> entry : asListOfMaps(node, "dayPhases")) {
            phases.add(new DayPhase(
                    asString(entry.get("name")),
                    asInt(entry.get("hour"), "day phase hour"),
                    asListOfStrings(entry.get("messages"), "day phase messages")));
        }
        return phases;
    }

    private static List<Weekday> parseWeek(Object node) {
        List<Weekday> week = new ArrayList<>();
        for (Map<String, Object> entry : asListOfMaps(node, "week")) {
            week.add(new Weekday(asString(entry.get("name")), asString(entry.get("description"))));
        }
        return week;
    }

    private static List<Month> parseMonths(Object node) {
        List<Month> months = new ArrayList<>();
        for (Map<String, Object> entry : asListOfMaps(node, "months")) {
            months.add(new Month(asString(entry.get("name")), asString(entry.get("description"))));
        }
        return months;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object node, String what) {
        if (!(node instanceof List<?> list)) {
            throw new IllegalArgumentException("calendar '%s' must be a sequence".formatted(what));
        }
        return (List<Map<String, Object>>) list;
    }

    private static List<String> asListOfStrings(Object node, String what) {
        if (!(node instanceof List<?> list)) {
            throw new IllegalArgumentException("calendar '%s' must be a sequence".formatted(what));
        }
        return list.stream().map(CalendarYamlReader::asString).toList();
    }

    private static int asInt(Object value, String what) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(Objects.requireNonNull(value, what + " must not be null").toString().strip());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString().strip();
    }
}
