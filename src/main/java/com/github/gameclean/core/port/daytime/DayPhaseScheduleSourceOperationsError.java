package com.github.gameclean.core.port.daytime;

/**
 * Unchecked failure of a {@link DayPhaseScheduleSourceOperationsOutputPort} operation — the authored day-phase
 * schedule could not be read, parsed, constructed into a valid
 * {@link com.github.gameclean.core.model.daytime.DayPhaseSchedule}, or reconciled against the calendar (a
 * phase hour at or beyond the calendar's {@code hoursPerDay}).
 *
 * <p>Unchecked on purpose, mirroring the other driven-port boundary errors. Because the schedule is loaded
 * once at boot and held in memory (not per-interaction input), a malformed authored schedule is a fail-fast
 * boot/configuration fault — the adapter raises this as it constructs the schedule at startup — rather than a
 * presented per-read outcome (see {@link DayPhaseScheduleSourceOperationsOutputPort} for the rationale).
 */
public class DayPhaseScheduleSourceOperationsError extends RuntimeException {

    public DayPhaseScheduleSourceOperationsError(String message) {
        super(message);
    }

    public DayPhaseScheduleSourceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
