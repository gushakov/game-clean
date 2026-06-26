package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * The authored set of {@link DayPhase}s for the world — which named moments (dawn, dusk, …) occur and at which
 * hour-of-day. A Value Object: always-valid (no two phases may begin at the same hour, which would make "the
 * phase at hour H" ambiguous) and equal by value. An <em>empty</em> schedule is valid — it simply means the
 * world announces no day phases.
 *
 * <p>It owns the single lookup the announcement interaction needs: {@link #phaseBeginningAt(int)} — does a
 * phase begin at this hour of the day? The hour-of-day index it is queried with is a value's
 * {@link com.github.gameclean.core.model.calendar.GameDate#getHourIndex()}, so the schedule never needs the
 * calendar to answer.
 *
 * <p>Whether each phase's hour is <em>within range</em> for the calendar's {@code hoursPerDay} is an
 * inter-model consistency rule, checked once at load against the loaded calendar (a fail-fast configuration
 * fault, like a malformed calendar) rather than re-checked here on every read — so this schedule stays
 * calendar-agnostic, validating only what it can judge alone (uniqueness of phase hours).
 */
@Value
public class DayPhaseSchedule {

    List<DayPhase> phases;

    public DayPhaseSchedule(List<DayPhase> phases) {
        this.phases = List.copyOf(DomainValidation.requireNonNull(phases, "day phases must not be null"));
        long distinctHours = this.phases.stream().map(DayPhase::getHourOfDay).distinct().count();
        if (distinctHours != this.phases.size()) {
            throw new InvalidDomainObjectError("day phases must begin at distinct hours of the day");
        }
    }

    /**
     * The phase, if any, that begins at the given hour of the day.
     *
     * @param hourOfDay a 0-based hour index within the day (a {@code GameDate}'s {@code hourIndex})
     * @return the phase beginning at that hour, or empty if none does
     */
    public Optional<DayPhase> phaseBeginningAt(int hourOfDay) {
        return phases.stream().filter(phase -> phase.getHourOfDay() == hourOfDay).findFirst();
    }
}
