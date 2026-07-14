package com.github.gameclean.infrastructure.npc;

import com.github.gameclean.core.usecase.npc.AnimateNpcsInputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Primary (driving) adapter: a background metronome that drives the {@code AnimateNpcs} interaction on a fixed
 * delay — the project's <em>second</em> parallel, time-driven actor and second async console writer, the peer
 * of {@link com.github.gameclean.infrastructure.time.GameClockTicker}. Two blind metronomes on one terminal
 * confirm the "no hand-rolled {@code SmartLifecycle}" prediction: both are cancelled by Spring at context close
 * before the {@code Terminal} is destroyed, and neither has an ordering constraint against the other.
 *
 * <p><b>A blind metronome — no domain knowledge.</b> Each tick it pulls a <strong>fresh prototype</strong>
 * {@link AnimateNpcsInputPort} from the {@link ApplicationContext} (the cargo-clean idiom, exactly as
 * {@code GameClockTicker} pulls its use case) and fires it. It does not know how many NPCs there are, their
 * move chances, or where the player stands: the use case enumerates the NPCs, rolls each one's chance, moves
 * the winners, and narrates only what the player can witness, so firing more often than needed is harmless
 * (a tick where nothing moves presents the quiet outcome).
 *
 * <p>It reads the bound, typed {@code game.npc.ticker.interval} {@link Duration} from
 * {@link GameConfigurationProperties} and registers a fixed-delay task with it — a {@code SchedulingConfigurer}
 * rather than {@code @Scheduled(fixedDelayString)} for the same reasons as the time ticker: a
 * {@code @DefaultValue} is a binding-time default, not an {@code Environment} entry, and {@code @Scheduled}
 * string attributes reject the simplified {@code 10s} form. Scheduling is configured at bean time, after
 * properties are bound, so it can consult the bound catalog.
 *
 * <p>The bean is guarded by {@code game.terminal.enabled} (like the rest of the interactive runtime), and the
 * scheduling infrastructure itself is enabled on the equally-guarded {@code BootSequence}, so test slices
 * neither register the task nor spin up a scheduler. The first tick fires shortly after startup, possibly
 * before the {@code @Order(1)} seeder has run; that early tick is a safe no-op (the use case presents
 * {@code presentNothingHappened} for an empty world).
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class NpcActivityTicker implements SchedulingConfigurer {

    ApplicationContext applicationContext;
    GameConfigurationProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Duration interval = properties.getNpc().getTicker().getInterval();
        taskRegistrar.addFixedDelayTask(this::advanceNpcs, interval);
    }

    private void advanceNpcs() {
        // Fresh prototype use case per tick; it presents its own outcome (including errors).
        AnimateNpcsInputPort useCase = applicationContext.getBean(AnimateNpcsInputPort.class);
        useCase.systemAdvancesNpcs();
    }
}
