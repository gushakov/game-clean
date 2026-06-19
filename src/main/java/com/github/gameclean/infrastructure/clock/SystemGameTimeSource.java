package com.github.gameclean.infrastructure.clock;

import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Driven adapter backing {@link GameTimeSourceOutputPort} with the system wall clock — the real source of
 * "how long has this session run". One real second is one game second, so the session-elapsed game seconds
 * are simply the real seconds since this singleton was created, which is session (application) start. The
 * time-reading use cases interpret this through the {@code GameClock}, so they stay deterministic under test
 * (the port is stubbed with a fixed figure); only this adapter is non-deterministic.
 *
 * <p>The {@link Clock} is injectable through the package-private constructor so a test can pin time; the
 * public no-arg constructor Spring uses takes the system UTC clock. The elapsed figure is floored at zero so
 * a backwards clock adjustment can never make the session appear to run negative time.
 */
@Component
public class SystemGameTimeSource implements GameTimeSourceOutputPort {

    private final Clock clock;
    private final Instant sessionStart;

    public SystemGameTimeSource() {
        this(Clock.systemUTC());
    }

    SystemGameTimeSource(Clock clock) {
        this.clock = clock;
        this.sessionStart = clock.instant();
    }

    @Override
    public long elapsedSessionSeconds() {
        long seconds = Duration.between(sessionStart, clock.instant()).getSeconds();
        return Math.max(0, seconds);
    }
}
