package com.github.gameclean.infrastructure.npc.command;

import com.github.gameclean.core.usecase.combat.FightNpcInputPort;
import com.github.gameclean.core.usecase.npc.WanderInputPort;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Component;

/**
 * Primary (driving) adapter for NPC actions — the deliberate sibling of the terminal's {@code ConsoleSession}.
 * Where the console turns a player's typed line into a use-case call, this turns a dispatched {@link NpcCommand}
 * into one: it subscribes to the {@code npcCommandChannel} and, per received command, pulls a <strong>fresh
 * prototype</strong> executing use case and fires it. It carries <em>no</em> decision logic (the policy already
 * decided), does no re-derivation, and renders nothing — the executing use case presents its own outcome.
 *
 * <p>The mapping from the sealed command set to the executing interactions is an exhaustive {@code switch}
 * (a compile error until a new command is handled): a {@link StrikePlayer} drives
 * {@code FightNpc.npcStrikesPlayer}, a {@link WanderThrough} drives {@code Wander.npcWandersThrough}. Each use
 * case is prototype-scoped, so it is pulled per command via {@link ApplicationContext#getBean} — a captured
 * prototype would silently defeat the scope, exactly as in {@code ConsoleSession}.
 *
 * <p>Gated by {@code game.terminal.enabled} with the rest of the interactive runtime; it subscribes to the
 * channel once, at {@code @PostConstruct}. Because the {@code DirectChannel} is synchronous, the handler runs on
 * the ticker thread that dispatched the command — the executing interaction completes before {@code send()}
 * returns to the policy.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class NpcCommandSession {

    SubscribableChannel npcCommandChannel;
    ApplicationContext applicationContext;

    @PostConstruct
    void subscribe() {
        npcCommandChannel.subscribe(this::handleMessage);
    }

    private void handleMessage(Message<?> message) {
        dispatch((NpcCommand) message.getPayload());
    }

    /**
     * Routes one command to its executing interaction. Package-visible so it is unit-testable directly (each
     * sealed command → the right prototype use-case call), without a live channel.
     */
    void dispatch(NpcCommand command) {
        switch (command) {
            case StrikePlayer strike ->
                    applicationContext.getBean(FightNpcInputPort.class).npcStrikesPlayer(strike.getNpcToken());
            case WanderThrough wander ->
                    applicationContext.getBean(WanderInputPort.class)
                            .npcWandersThrough(wander.getNpcToken(), wander.getExitName());
        }
    }
}
