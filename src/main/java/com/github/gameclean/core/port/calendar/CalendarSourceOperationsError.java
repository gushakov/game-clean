package com.github.gameclean.core.port.calendar;

/**
 * Unchecked failure of a {@link CalendarSourceOperationsOutputPort} operation — the authored calendar could
 * not be read, parsed, or constructed into a valid {@link com.github.gameclean.core.model.calendar.GameCalendar}
 * (a missing resource, an I/O error, a malformed document, or authored radices/cycles that fail the
 * always-valid gate).
 *
 * <p>Unchecked on purpose, mirroring the other driven-port boundary errors. Because the calendar is loaded
 * once at boot and held in memory (not per-interaction input), a malformed authored calendar is a fail-fast
 * boot/configuration fault — the adapter raises this as it constructs the calendar at startup — rather than a
 * presented per-read outcome (see {@link CalendarSourceOperationsOutputPort} for the rationale).
 */
public class CalendarSourceOperationsError extends RuntimeException {

    public CalendarSourceOperationsError(String message) {
        super(message);
    }

    public CalendarSourceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
