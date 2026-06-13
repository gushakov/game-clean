package com.github.gameclean.infrastructure.world;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Type-safe configuration for world seeding, bound from the {@code game.world.*} properties. Used by
 * {@link WorldSeeder} to locate the authored seed without scattering magic-string keys.
 *
 * <p>Immutable and constructor-bound — a single constructor makes Spring Boot use constructor
 * binding, and {@code @DefaultValue} supplies a sensible default so absent configuration still binds.
 * (Constructor binding, not a record, to honour the project's Lombok-over-records convention;
 * {@code spring-boot-configuration-processor} on the annotation path generates IDE metadata.)
 *
 * <p>There is no longer a {@code construct-on-startup} flag: seeding is driven by {@code BootSequence}
 * (gated, with the console, by {@code game.terminal.enabled}) and {@link WorldSeeder#seed()} is
 * idempotent, so a separate enable flag became redundant.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "game.world")
public class WorldSeedProperties {

    /** Location of the authored world seed (any Spring resource string, e.g. {@code classpath:...}). */
    Resource seedLocation;

    public WorldSeedProperties(@DefaultValue("classpath:world/scenes.yaml") Resource seedLocation) {
        this.seedLocation = seedLocation;
    }
}
