package com.github.gameclean.infrastructure.npc.command;

import com.github.gameclean.core.usecase.combat.FightNpcInputPort;
import com.github.gameclean.core.usecase.npc.WanderInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link NpcCommandSession} mapping — each sealed {@link NpcCommand} routes to the right executing
 * interaction, pulled fresh from the context per command. The {@code dispatch} mapping is exercised directly,
 * and one test drives it end-to-end through a real {@link DirectChannel} to pin the subscribe + payload-cast
 * wiring.
 */
@ExtendWith(MockitoExtension.class)
class NpcCommandSessionTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private FightNpcInputPort fightNpcUseCase;
    @Mock
    private WanderInputPort wanderUseCase;

    @Test
    void aStrikePlayerCommandDrivesTheNpcCounterstrike() {
        when(applicationContext.getBean(FightNpcInputPort.class)).thenReturn(fightNpcUseCase);
        NpcCommandSession session = new NpcCommandSession(new DirectChannel(), applicationContext);

        session.dispatch(new StrikePlayer("npc1"));

        verify(fightNpcUseCase).npcStrikesPlayer("npc1");
        verifyNoInteractions(wanderUseCase);
    }

    @Test
    void aWanderThroughCommandDrivesTheWander() {
        when(applicationContext.getBean(WanderInputPort.class)).thenReturn(wanderUseCase);
        NpcCommandSession session = new NpcCommandSession(new DirectChannel(), applicationContext);

        session.dispatch(new WanderThrough("npc2", "north"));

        verify(wanderUseCase).npcWandersThrough("npc2", "north");
        verifyNoInteractions(fightNpcUseCase);
    }

    @Test
    void subscribesToTheChannelAndRoutesADispatchedMessage() {
        DirectChannel channel = new DirectChannel();
        when(applicationContext.getBean(FightNpcInputPort.class)).thenReturn(fightNpcUseCase);
        NpcCommandSession session = new NpcCommandSession(channel, applicationContext);
        session.subscribe();   // the @PostConstruct wiring

        // A command put on the channel (as the dispatch adapter does) is routed to the executing interaction,
        // synchronously on the sender's thread (DirectChannel).
        channel.send(MessageBuilder.withPayload((NpcCommand) new StrikePlayer("npc1")).build());

        verify(fightNpcUseCase).npcStrikesPlayer("npc1");
    }
}
