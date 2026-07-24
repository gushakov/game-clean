package com.github.gameclean.infrastructure.npc.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;

/**
 * Declares the NPC command channel — the in-band bridge between the animate policy (which dispatches decided
 * actions through {@code NpcCommandsOutputPort}) and the {@link NpcCommandSession} (which drives the executing
 * interactions). A Spring Integration {@link DirectChannel}: <b>synchronous, in-band</b> — {@code send()} runs
 * the subscribed handler on the caller's (ticker) thread and returns only after the executing interaction has
 * completed. That is deliberate (design-notes §8): a command derived from persisted stance needs no crash-durable
 * outbox, because the polling loop re-derives a lost one next tick — the loop is the retry mechanism. Going
 * asynchronous later is a one-line swap here (a queue or executor channel) with the port and both adapters
 * untouched.
 *
 * <p>A bare channel used manually needs no {@code @EnableIntegration}; the bean is returned as {@code DirectChannel}
 * so it injects both as a {@code MessageChannel} (the dispatch adapter sends) and a {@code SubscribableChannel}
 * (the session subscribes). Gated by {@code game.terminal.enabled} with the rest of the interactive runtime, so
 * test slices neither create the channel nor its session.
 */
@Configuration
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
public class NpcCommandChannelConfig {

    @Bean
    public DirectChannel npcCommandChannel() {
        return new DirectChannel();
    }
}
