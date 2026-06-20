package com.github.gameclean.infrastructure.time;

import com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayInputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Primary (driving) adapter: a background metronome that drives the {@code AnnounceTimeOfDay} interaction on a
 * fixed real-time interval — the project's first parallel, time-driven actor (the system-actor peer of the
 * player-driven {@link com.github.gameclean.infrastructure.terminal.ConsoleSession}).
 *
 * <p><b>A blind metronome — no domain knowledge.</b> Each tick it pulls a <strong>fresh prototype</strong>
 * {@link AnnounceTimeOfDayInputPort} from the {@link ApplicationContext} (the cargo-clean idiom, exactly as
 * {@code ConsoleSession} pulls its use cases) and fires it. It does not know the calendar, the seconds-per-hour,
 * or what a day phase is: the use case derives "now", finds any phase boundary, and dedups against its
 * versioned watermark, so polling more often than needed is harmless.
 *
 * <p><b>Why {@code @Scheduled}, not a hand-rolled {@code SmartLifecycle} thread.</b> The only thing a thread
 * would buy over Spring's scheduler is <em>per-bean phase ordering relative to another lifecycle participant</em>
 * — and we have no second async writer to order against yet (design-notes §6: deferred to that trigger). So
 * this is one annotated method; Spring owns the scheduler thread. The shutdown guarantee the JLine console
 * needs comes for free: {@code @Scheduled} tasks are lifecycle-managed, so the container <em>cancels</em> them
 * (waiting for an in-flight run) at context close — before it destroys plain singletons like the
 * {@code Terminal} — so no {@code printAbove} fires on a closed terminal. {@code fixedDelay} measures the gap
 * from the end of one run to the start of the next (no overlap), and an {@code initialDelay} of one interval
 * gives the {@code @Order(1)} seeder a head start; an early tick before the world is seeded is a safe no-op
 * (the use case branch-and-presents {@code presentGameNotInitialized}). Should the fired interaction ever throw
 * (the use case routes its own outcomes to its presenter, so only an unexpected infra fault would), Spring's
 * default scheduled-task error handler logs it and keeps the schedule running — so no hand-rolled catch.
 *
 * <p>The bean is guarded by {@code game.terminal.enabled} (like the rest of the interactive runtime) and the
 * scheduling infrastructure itself is enabled on the equally-guarded {@code BootSequence}, so test slices
 * neither register the task nor spin up a scheduler.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GameClockTicker {

    private final ApplicationContext applicationContext;

    @Scheduled(fixedDelayString = "${game.time.ticker.interval}", initialDelayString = "${game.time.ticker.interval}")
    public void observeTimeOfDay() {
        // Fresh prototype use case per observation; it presents its own outcome (including errors).
        AnnounceTimeOfDayInputPort useCase = applicationContext.getBean(AnnounceTimeOfDayInputPort.class);
        useCase.systemObservesTimeOfDay();
    }
}
