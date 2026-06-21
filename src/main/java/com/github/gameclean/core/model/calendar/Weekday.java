package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A named day of the week — one authored entry in the calendar's {@code week:} cycle, carrying the name the
 * renderer shows (e.g. {@code Elenya}) and a flavour description. A Value Object: always-valid (name and
 * description both non-blank), equality by value.
 *
 * <p>The week is <em>continuous</em>: which weekday a date falls on is computed from the absolute day count
 * since the epoch, so a {@code Weekday} carries no index of its own — its position is its slot in the
 * {@link GameCalendar#getWeek()} list, and {@link GameCalendar#weekdayOf} resolves a date to one of these.
 */
@Value
public class Weekday {

    private final String name;
    private final String description;

    public Weekday(String name, String description) {
        this.name = requireNonBlank(name, "weekday name");
        this.description = requireNonBlank(description, "weekday description");
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
