package com.github.gameclean.infrastructure;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * The single catalog of every {@code game.*} application property. These are the project's important
 * configuration points, so they live in one bean at the infrastructure root rather than scattered
 * across feature sub-packages where they would have to be hunted for. Related keys are grouped into
 * nested static classes mirroring the property tree ({@code game.world.*}, {@code game.terminal.*},
 * {@code game.time.*}).
 *
 * <p>Immutable and constructor-bound — a single constructor triggers Spring Boot constructor binding,
 * and {@code @DefaultValue} supplies sensible defaults so absent configuration still binds (a bare
 * {@code @DefaultValue} on a nested group instantiates it with its own defaults). Constructor binding,
 * not records, to honour the project's Lombok-over-records convention;
 * {@code spring-boot-configuration-processor} on the annotation path generates IDE metadata.
 *
 * <p>Note the one thing this catalog cannot own: a bean-creation {@code @ConditionalOnProperty}
 * (e.g. on {@code TerminalConfig} / {@code ConsoleSession} / {@code BootSequence}) reads its key
 * straight from the {@code Environment}, because a condition is evaluated before — and cannot consult
 * — a bound properties object. So {@code game.terminal.enabled} also appears as a raw key in those
 * guards. It is still bound here, so this remains the one place that documents every {@code game.*}
 * property even when a condition reads it independently.
 *
 * <p>The contrast is worth keeping straight, because it bit once: a <em>post</em>-binding consumer
 * <em>can</em> (and should) consult this bound catalog rather than re-read a raw key. The time ticker
 * ({@code GameClockTicker}) configures its schedule at bean time via a {@code SchedulingConfigurer} and
 * injects {@code getTime().getTicker().getInterval()} as a typed {@link Duration}. An earlier cut reached
 * for {@code @Scheduled("${game.time.ticker.interval}")} instead and failed at startup: a {@code @DefaultValue}
 * is a binding-time default, not an {@code Environment} entry, so the placeholder did not resolve. The
 * raw-key carve-out is only for pre-binding conditions; everything after binding reads the typed value here.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "game")
public class GameConfigurationProperties {

    World world;
    Terminal terminal;
    Player player;
    Time time;

    public GameConfigurationProperties(@DefaultValue World world, @DefaultValue Terminal terminal,
                                       @DefaultValue Player player, @DefaultValue Time time) {
        this.world = world;
        this.terminal = terminal;
        this.player = player;
        this.time = time;
    }

    /** {@code game.world.*} — world construction and seeding. */
    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static class World {

        /** Location of the authored world seed (any Spring resource string, e.g. {@code classpath:...}). */
        Resource seedLocation;

        public World(@DefaultValue("classpath:world/scenes.yaml") Resource seedLocation) {
            this.seedLocation = seedLocation;
        }
    }

    /** {@code game.terminal.*} — the interactive JLine runtime. */
    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static class Terminal {

        /**
         * Whether the interactive runtime boots: {@code BootSequence} seeds the world and runs the
         * console. Left unset (false) for tests so {@code @SpringBootTest} slices never seed
         * implicitly, block on a console, or grab a system terminal. Also read raw by the
         * {@code @ConditionalOnProperty} guards (see class javadoc).
         */
        boolean enabled;

        public Terminal(@DefaultValue("false") boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** {@code game.player.*} — the single player the interactive session acts as. */
    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static class Player {

        /** Id of the single player (single-player game). Read by {@code ConfiguredPlayerAdapter} to resolve the acting player for the use cases. */
        String id;

        /** Scene the player starts in; the boot seeder creates the player there if absent. */
        String startingSceneId;

        public Player(@DefaultValue("plr1") String id, @DefaultValue("scn1") String startingSceneId) {
            this.id = id;
            this.startingSceneId = startingSceneId;
        }
    }

    /** {@code game.time.*} — the game calendar, clock, and background time ticker. */
    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static class Time {

        /**
         * Location of the authored calendar (radices + named weekday/month cycles, plus the day phases), any
         * Spring resource string. The content is world data flowing through the domain; only this location is
         * operational configuration.
         */
        Resource calendarLocation;

        /** {@code game.time.ticker.*} — the background metronome that drives day-phase announcements. */
        Ticker ticker;

        public Time(@DefaultValue("classpath:world/calendar.yaml") Resource calendarLocation,
                    @DefaultValue Ticker ticker) {
            this.calendarLocation = calendarLocation;
            this.ticker = ticker;
        }

        /** {@code game.time.ticker.*} — the background time ticker. */
        @Getter
        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        public static class Ticker {

            /**
             * Real-time interval between observations of game time. The ticker is a blind metronome: it fires
             * the announcement use case this often, and the use case dedups so over-frequent polls are
             * harmless. Keep it well below one game hour ({@code secondsPerHour} real seconds) so a day-phase
             * boundary is announced promptly. A Spring Boot {@link Duration} string, e.g. {@code 5s}.
             */
            Duration interval;

            public Ticker(@DefaultValue("5s") Duration interval) {
                this.interval = interval;
            }
        }
    }
}
