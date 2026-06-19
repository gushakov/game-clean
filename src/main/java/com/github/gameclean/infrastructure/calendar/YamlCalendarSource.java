package com.github.gameclean.infrastructure.calendar;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsError;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Driven adapter implementing {@link CalendarSourceOperationsOutputPort} over the authored calendar YAML: it
 * resolves the configured location, opens the resource, and hands the stream to {@link CalendarYamlReader} to
 * build the {@link GameCalendar}. This is where all access to the YAML parsing machinery lives — confined to
 * the infrastructure ring, behind the port.
 *
 * <p><b>Loaded once, at boot.</b> The calendar is read and constructed in the constructor and cached for the
 * life of the application — it is immutable authored content, not persisted and not per-interaction input
 * (design decision #2: load each boot rather than persist; drift hazard parked). So {@link #loadCalendar()}
 * hands back the cached, already-valid model on every call. Failing in the constructor means a malformed
 * calendar <em>fails fast at startup</em> (the bean, and the application, refuse to come up) rather than
 * surfacing on the first time-reading interaction — the documented consequence of returning a valid model
 * from this port instead of an invalid-capable carrier (see the port's javadoc).
 *
 * <p>Every technical failure — an unreadable resource ({@link IOException}), a malformed document, or
 * authored radices/cycles that fail the always-valid gate ({@code RuntimeException} from the reader) — is
 * translated into the unchecked {@link CalendarSourceOperationsError} the port contract declares.
 */
@Component
@Slf4j
public class YamlCalendarSource implements CalendarSourceOperationsOutputPort {

    private final GameCalendar calendar;

    public YamlCalendarSource(CalendarYamlReader reader, GameConfigurationProperties properties) {
        Resource location = properties.getTime().getCalendarLocation();
        log.info("[Calendar] Loading the authored calendar from {}", location);
        try (InputStream in = location.getInputStream()) {
            this.calendar = reader.read(in);
        } catch (IOException | RuntimeException e) {
            throw new CalendarSourceOperationsError(
                    "could not read, parse, or construct the calendar from %s".formatted(location), e);
        }
    }

    @Override
    public GameCalendar loadCalendar() {
        return calendar;
    }
}
