package com.github.gameclean.infrastructure;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test ({@code *IT}). It owns a single, throwaway Postgres
 * container that the test run alone controls — replacing the persistent, shared {@code gameclean-db}
 * the {@code docker-compose.yaml} stack runs for actually playing the game.
 *
 * <p>This is the fix for the dirty-start failure mode (issue #17): the compose DB is shared
 * <em>between</em> {@code mvn verify} runs (its named volume keeps rows) and <em>with</em> the
 * running game (which seeds and commits into it), so a prior run or a playthrough could leave rows
 * that made the next run's first {@code INSERT} collide or skewed an idempotency assertion. An
 * ephemeral container is pristine on every run, so that sharing — the root cause — is gone.
 *
 * <p><strong>Singleton container, started once.</strong> The field is {@code static} and started
 * directly in a static initializer rather than managed by the {@code @Testcontainers}/{@code @Container}
 * JUnit extension (which starts and stops a container <em>per class</em>). One container is therefore
 * shared by all {@code *IT} classes in a run and is reaped by the Testcontainers Ryuk sidecar at JVM
 * exit — fast, and isolation between runs is preserved because each run gets a fresh container.
 *
 * <p>{@code @ServiceConnection} lets Spring Boot auto-wire the datasource from the running container
 * (no hardcoded {@code localhost:5432}); Boot's context customizer detects the annotated static field
 * here in the base class and applies it to both {@code @DataJdbcTest} slices and full
 * {@code @SpringBootTest} subclasses. Flyway migrates the fresh container at context startup exactly
 * as in production. The image tag is pinned to match {@code docker-compose.yaml} so tests run against
 * the same Postgres version the game does.
 *
 * <p>Subclasses keep their {@code @AfterEach} cleanups as cheap insurance for inter-test
 * order-independence <em>within</em> a run (the committing {@code @SpringBootTest} ITs share the one
 * container); they are no longer load-bearing for isolation <em>across</em> runs.
 */
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.12");

    static {
        POSTGRES.start();
    }
}
