package com.github.gameclean.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Application entry point and Spring composition boot. It lives in {@code infrastructure} on
 * purpose: {@code @SpringBootApplication} roots component scanning at its own package, so placing
 * it here confines the scan (and all autoconfiguration) to the infrastructure ring. The framework
 * never scans {@code core} — the clean core stays framework-free, as the hexagonal boundary
 * requires.
 *
 * <p>{@code main} only refreshes the context; it does <em>not</em> contain the startup logic. What
 * happens at boot — construct the world, then open it to the player — is owned by
 * {@link BootSequence}, the {@code @Configuration} that declares the two {@code @Order}-ed
 * {@code ApplicationRunner} beans stating that order explicitly. Look there for the startup
 * choreography, not here.
 *
 * <p>The single {@link GameConfigurationProperties} catalog of all {@code game.*} properties is
 * registered here, centrally, rather than from whichever feature bean happens to consume it.
 */
@SpringBootApplication
@EnableConfigurationProperties(GameConfigurationProperties.class)
public class GameCleanApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCleanApplication.class, args);
    }

}
