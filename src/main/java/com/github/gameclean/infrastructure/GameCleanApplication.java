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
 * <p>{@code main} does not contain the startup logic. What happens at boot — construct the world,
 * then open it to the player — is owned by {@link BootSequence}, the {@code @Configuration} that
 * declares the two {@code @Order}-ed {@code ApplicationRunner} beans stating that order explicitly.
 * Look there for the startup choreography, not here.
 *
 * <p>What {@code main} <em>does</em> own is the process run-and-exit. {@code SpringApplication.run}
 * returns only once every {@code ApplicationRunner} has completed — including the one that blocks in
 * the interactive console until {@code bye}. At that point the player session is over, so we
 * explicitly close the context via {@code SpringApplication.exit} and terminate. This is load-bearing:
 * {@code @EnableScheduling} runs Spring's scheduler on <em>non-daemon</em> threads, which would keep
 * the JVM alive (still ticking the {@code GameClockTicker}) long after {@code bye} if the context
 * were left open. Closing it cancels the scheduled tasks (waiting for any in-flight run) and then
 * destroys the plain singletons last — including the JLine {@code Terminal}, restoring the tty — the
 * ordered shutdown the design assumes. {@code BootSequence} owns startup ordering; {@code main} owns
 * the run-and-exit.
 *
 * <p>The single {@link GameConfigurationProperties} catalog of all {@code game.*} properties is
 * registered here, centrally, rather than from whichever feature bean happens to consume it.
 */
@SpringBootApplication
@EnableConfigurationProperties(GameConfigurationProperties.class)
public class GameCleanApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(GameCleanApplication.class, args)));
    }

}
