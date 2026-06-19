package com.github.gameclean.core.port.calendar;

import com.github.gameclean.core.model.calendar.GameCalendar;

/**
 * Driven (output) port that serves the authored game calendar — the radices and named weekday/month cycles
 * that turn elapsed seconds into a date. The {@code OutputPort} suffix marks the hexagonal direction: a use
 * case (e.g. the player asking for the time) is the caller, an infrastructure adapter over the authored
 * source the implementor.
 *
 * <p><b>This port returns a <em>valid</em> {@link GameCalendar}, not an invalid-capable carrier</b> — a
 * deliberate, documented departure from the seed-source port's shape (design-notes §3/§11). The seed source
 * returns {@code *Entry} carriers because authored world data's invalidity is a <em>presented</em> domain
 * outcome the use case must branch on. The calendar is different: it is <em>not persisted</em> (loaded each
 * boot, held in memory) yet is read by many interactions, so it behaves like cached <em>configuration</em>
 * rather than per-interaction input. Re-validating it on every read would be pointless, and an invalid
 * authored calendar is a boot-time configuration fault, not something to show a player mid-game. So the
 * adapter constructs and validates the calendar once at load and fails fast at boot if it is malformed; this
 * port hands back the already-valid model. (The cost of choosing "load each boot" over "persist the
 * calendar": calendar-authoring validity surfaces as a fail-fast boot fault here rather than as a presented
 * initialization outcome.)
 *
 * <p>Failures surface as the unchecked {@link CalendarSourceOperationsError}.
 */
public interface CalendarSourceOperationsOutputPort {

    /**
     * @return the authored, always-valid game calendar (never {@code null})
     * @throws CalendarSourceOperationsError if the calendar cannot be read, parsed, or constructed
     */
    GameCalendar loadCalendar();
}
