package com.github.gameclean.core.port.daytime;

import com.github.gameclean.core.model.daytime.DayPhaseSchedule;

/**
 * Driven (output) port that serves the authored day-phase schedule — which named moments (dawn, dusk, …)
 * occur and at which hour-of-day. The {@code OutputPort} suffix marks the hexagonal direction: the time-of-day
 * announcement use case is the caller; an infrastructure adapter over the authored source is the implementor.
 *
 * <p><b>Like the calendar source, this port returns a <em>valid</em> {@link DayPhaseSchedule}, not an
 * invalid-capable carrier</b> — the same deliberate, documented departure (design-notes §3/§11). Day phases
 * are authored world content loaded once at boot and held in memory (not persisted, not per-interaction
 * input), so they behave like cached configuration: the adapter constructs and validates the schedule once at
 * load — including the inter-model check that every phase hour is within the calendar's {@code hoursPerDay} —
 * and fails fast at boot if it is malformed, rather than surfacing a presented mid-game outcome. This port
 * hands back the already-valid model.
 *
 * <p>Failures surface as the unchecked {@link DayPhaseScheduleSourceOperationsError}. A distinct type from the
 * calendar source's error even though the same adapter and file back both: failing to source the day-phase
 * schedule is this port's concern, and each driven port owning its own boundary error keeps the §3/§5
 * symmetry.
 */
public interface DayPhaseScheduleSourceOperationsOutputPort {

    /**
     * @return the authored, always-valid day-phase schedule (never {@code null}; possibly empty if no phases
     *         are authored)
     * @throws DayPhaseScheduleSourceOperationsError if the schedule cannot be read, parsed, constructed, or
     *                                               reconciled against the calendar
     */
    DayPhaseSchedule loadDayPhases();
}
