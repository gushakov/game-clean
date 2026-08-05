package com.github.gameclean.infrastructure.npc.command;

import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.port.npccommands.NpcCommandsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Secondary (driven) adapter implementing {@link NpcCommandsOutputPort}: it turns a decided NPC action into an
 * infrastructure {@link NpcCommand} record and puts it on the {@code npcCommandChannel}. This is the one place
 * the core-side vocabulary (the port's imperative methods) becomes a concrete channel message — the outbound
 * twin of a presenter adapter. It flattens the domain {@link NpcId} to its raw token as it builds the record
 * (primitives cross the boundary); the receiving session reconstructs the id inward.
 *
 * <p>Fire-and-forget from the policy's view: {@code send()} on the synchronous {@code DirectChannel} runs the
 * executing interaction inline and returns, but the policy neither waits for nor reads a result — a failed
 * dispatch or a rolled-back execution just costs this round (the loop re-derives). Gated with the interactive
 * runtime, so it exists only when the channel it sends on does.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringNpcCommandDispatchAdapter implements NpcCommandsOutputPort {

    MessageChannel npcCommandChannel;

    @Override
    public void dispatchStrike(NpcId npcId) {
        npcCommandChannel.send(MessageBuilder.withPayload((NpcCommand) new StrikePlayer(npcId.asString())).build());
    }

    @Override
    public void dispatchWander(NpcId npcId, String exitName) {
        npcCommandChannel.send(
                MessageBuilder.withPayload((NpcCommand) new WanderThrough(npcId.asString(), exitName)).build());
    }
}
