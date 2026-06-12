package com.github.gameclean.infrastructure.world;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Type-safe configuration for world seeding, bound from the {@code game.world.*} properties. Used by
 * {@link WorldSeedRunner} so the boot-time behaviour is configurable without scattering magic-string
 * keys: where the seed lives, and whether the world is constructed at startup at all.
 *
 * <p>Immutable and constructor-bound — a single constructor makes Spring Boot use constructor
 * binding, and {@code @DefaultValue} supplies sensible defaults so absent configuration still binds.
 * (Constructor binding, not a record, to honour the project's Lombok-over-records convention;
 * {@code spring-boot-configuration-processor} on the annotation path generates IDE metadata.)
 *
 * <p>The {@code construct-on-startup} flag is <em>also</em> read directly by
 * {@link WorldSeedRunner}'s {@code @ConditionalOnProperty} guard: a bean-creation condition cannot
 * consult a bound properties object, so the raw key appears there too — the standard Boot pairing of
 * a condition with its configuration-properties type.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "game.world")
public class WorldSeedProperties {

    /** Whether the world is constructed and seeded at application startup. */
    boolean constructOnStartup;

    /** Location of the authored world seed (any Spring resource string, e.g. {@code classpath:...}). */
    Resource seedLocation;

    public WorldSeedProperties(
            @DefaultValue("false") boolean constructOnStartup,
            @DefaultValue("classpath:world/scenes.yaml") Resource seedLocation) {
        this.constructOnStartup = constructOnStartup;
        this.seedLocation = seedLocation;
    }
}
