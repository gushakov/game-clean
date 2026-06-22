package com.github.gameclean.infrastructure.time;

import com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayInputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Primary (driving) adapter: a background metronome that drives the {@code AnnounceTimeOfDay} interaction on a
 * fixed delay — the project's first parallel, time-driven actor (the system-actor peer of the player-driven
 * {@link com.github.gameclean.infrastructure.terminal.ConsoleSession}).
 *
 * <p><b>A blind metronome — no domain knowledge.</b> Each tick it pulls a <strong>fresh prototype</strong>
 * {@link AnnounceTimeOfDayInputPort} from the {@link ApplicationContext} (the cargo-clean idiom, exactly as
 * {@code ConsoleSession} pulls its use cases) and fires it. It does not know the calendar, the seconds-per-hour,
 * or what a day phase is: the use case derives "now", finds any phase boundary, and dedups against its
 * versioned watermark, so polling more often than needed is harmless.
 *
 * <p><b>Why a {@code SchedulingConfigurer} reading the bound interval — not {@code @Scheduled(fixedDelayString)}.</b>
 * Two things rule out the annotation here. First, the interval's default lives as a binding-time
 * {@code @DefaultValue("5s")} on {@link GameConfigurationProperties} — it is <em>not</em> an entry in the
 * {@code Environment}, so an {@code @Scheduled("${game.time.ticker.interval}")} placeholder fails to resolve at
 * startup. Second, {@code @Scheduled}'s string attributes accept ISO-8601 ({@code PT5S}) or a millis number,
 * <em>not</em> the simplified {@code 5s} form (that style is for {@code @ConfigurationProperties} binding). The
 * clean way out is that scheduling is configured at <em>bean</em> time, after properties are bound — so unlike
 * a pre-binding {@code @ConditionalOnProperty}, it <em>can</em> consult the bound catalog. We inject the typed
 * {@link Duration} the catalog already validates and documents, and register the task with it: one source of
 * truth, no placeholder, no string parsing in the scheduling path.
 *
 * <p>Spring owns the scheduler thread and the task's lifecycle: it cancels scheduled tasks (waiting for an
 * in-flight run) at context close, <em>before</em> destroying plain singletons like the JLine {@code Terminal},
 * so no {@code printAbove} fires on a closed terminal — the §6/§7 shutdown order, without a hand-rolled
 * {@code SmartLifecycle}. The first tick fires shortly after startup, before the {@code @Order(1)} seeder may
 * have run; that early observation is a safe no-op (the use case branch-and-presents
 * {@code presentGameNotInitialized}).
 *
 * <p>The bean is guarded by {@code game.terminal.enabled} (like the rest of the interactive runtime), and the
 * scheduling infrastructure itself is enabled on the equally-guarded {@code BootSequence}, so test slices
 * neither register the task nor spin up a scheduler.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GameClockTicker implements SchedulingConfigurer {

    private final ApplicationContext applicationContext;
    private final GameConfigurationProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Duration interval = properties.getTime().getTicker().getInterval();
        taskRegistrar.addFixedDelayTask(this::observeTimeOfDay, interval);
    }

    private void observeTimeOfDay() {
        // Fresh prototype use case per observation; it presents its own outcome (including errors).
        AnnounceTimeOfDayInputPort useCase = applicationContext.getBean(AnnounceTimeOfDayInputPort.class);
        useCase.systemObservesTimeOfDay();
    }
}
