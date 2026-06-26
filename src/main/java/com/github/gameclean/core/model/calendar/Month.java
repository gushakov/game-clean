package com.github.gameclean.core.model.calendar;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A named month — one authored entry in the calendar's {@code months:} cycle, carrying the name the renderer
 * shows (e.g. {@code Aelorin}) and a flavour description. A Value Object: always-valid (name and description
 * both non-blank), equality by value.
 *
 * <p>A {@code Month} carries no index: its position is its slot in the {@link GameCalendar#getMonths()} list,
 * and the number of months <em>is</em> the length of the year. {@link GameCalendar#monthOf} resolves a date's
 * month index to one of these.
 */
@Value
public class Month {

    String name;
    String description;

    public Month(String name, String description) {
        this.name = requireNonBlank(name, "month name");
        this.description = requireNonBlank(description, "month description");
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
