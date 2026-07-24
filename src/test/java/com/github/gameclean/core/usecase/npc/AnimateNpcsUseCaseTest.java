package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.npccommands.NpcCommandsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link AnimateNpcsUseCase} — the read-only policy (#66 step 2). Every output port is
 * mocked and the use case is exercised through its input port. A {@link ScriptedDice} pins the attack gate and
 * the wander move-roll + exit pick, so the decisions are reproducible.
 *
 * <p>The policy <b>writes nothing</b> and <b>always presents the quiet stripe</b> — it decides each NPC's action
 * from persisted stance and dispatches it as a command through {@link NpcCommandsOutputPort}; the executions
 * narrate themselves elsewhere. The behaviour branches (L5) are pinned individually: a hostile, co-located NPC
 * that passes its attack gate → strike; a failed gate or a non-co-located hostile → stand ground; a non-hostile
 * NPC that passes its move gate → wander with the rolled exit. Batch-then-dispatch (all decisions derived before
 * any command is dispatched) and "zero persistence writes" are asserted explicitly.
 */
@ExtendWith(MockitoExtension.class)
class AnimateNpcsUseCaseTest {

    @Mock
    private AnimateNpcsPresenterOutputPort presenter;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Spy
    private ScriptedDice dice = new ScriptedDice();
    @Mock
    private NpcCommandsOutputPort commandOps;

    @InjectMocks
    private AnimateNpcsUseCase useCase;

    @Test
    void presentsNothingHappenedForAnEmptyWorldResolvingNothingElse() {
        when(npcOps.findAllNpcs()).thenReturn(List.of());

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verifyNoInteractions(sceneOps, playerOps, playerRepositoryOps, commandOps);
    }

    @Test
    void aHostileCoLocatedNpcThatPassesItsAttackGateDispatchesAStrikeAndNoWander() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(hostileNpc("npc1", "scn1")));
        playerInScene("scn1");                 // co-located with the hostile NPC
        dice.willRoll(true);                   // the attack gate passes

        useCase.systemAdvancesNpcs();

        verify(commandOps).dispatchStrike(NpcId.of("npc1"));
        verify(commandOps, never()).dispatchWander(any(), any());
        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());   // read-only: the policy never writes
    }

    @Test
    void aHostileCoLocatedNpcThatFailsItsAttackGateStandsGround() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(hostileNpc("npc1", "scn1")));
        playerInScene("scn1");
        dice.willRoll(false);                  // the attack gate fails

        useCase.systemAdvancesNpcs();

        verify(commandOps, never()).dispatchStrike(any());
        verify(commandOps, never()).dispatchWander(any(), any());
        verify(presenter).presentNothingHappened();
    }

    @Test
    void aHostileNpcNotCoLocatedStandsGroundWithoutRolling() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(hostileNpc("npc1", "scn1")));
        playerInScene("scn2");                 // player elsewhere — not co-located
        // No roll is scripted: a non-co-located hostile NPC never reaches the attack gate; an unscripted roll
        // would throw, pinning that it does not roll.

        useCase.systemAdvancesNpcs();

        verify(commandOps, never()).dispatchStrike(any());
        verify(commandOps, never()).dispatchWander(any(), any());
        verify(presenter).presentNothingHappened();
    }

    @Test
    void aNonHostileNpcThatPassesItsMoveGateDispatchesAWanderWithTheRolledExit() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(peacefulNpc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        playerInScene("scn2");
        dice.willRoll(true).willPick(0);       // move gate passes; pick the only exit (north)

        useCase.systemAdvancesNpcs();

        verify(commandOps).dispatchWander(NpcId.of("npc1"), "north");
        verify(commandOps, never()).dispatchStrike(any());
        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
    }

    @Test
    void aNonHostileNpcThatFailsItsMoveGateStaysPut() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(peacefulNpc("npc1", "scn1")));
        playerInScene("scn2");
        dice.willRoll(false);                  // stays put — the scene is never even resolved for an exit

        useCase.systemAdvancesNpcs();

        verify(commandOps, never()).dispatchWander(any(), any());
        verify(commandOps, never()).dispatchStrike(any());
        verify(presenter).presentNothingHappened();
    }

    @Test
    void withNoPlayerAHostileStandsGroundWhileOthersStillWander() {
        Npc hostile = hostileNpc("npc1", "scn1");
        Npc peaceful = peacefulNpc("npc2", "scn3");
        when(npcOps.findAllNpcs()).thenReturn(List.of(hostile, peaceful));
        when(sceneOps.findScene(SceneId.of("scn3"))).thenReturn(Optional.of(armouryWithExit()));
        noPlayer();
        // Hostile first (not co-located, no player → no roll); then the peaceful NPC's move gate passes + pick.
        dice.willRoll(true).willPick(0);

        useCase.systemAdvancesNpcs();

        verify(commandOps, never()).dispatchStrike(any());        // hostile stands ground with no player
        verify(commandOps).dispatchWander(NpcId.of("npc2"), "west");
        verify(presenter).presentNothingHappened();
    }

    @Test
    void derivesEveryDecisionBeforeDispatchingAnyCommand() {
        Npc first = peacefulNpc("npc1", "scn1");    // scn1 -> scn2 (north)
        Npc second = peacefulNpc("npc2", "scn3");   // scn3 -> scn4 (west)
        when(npcOps.findAllNpcs()).thenReturn(List.of(first, second));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn3"))).thenReturn(Optional.of(armouryWithExit()));
        playerInScene("scn2");
        dice.willRoll(true).willPick(0).willRoll(true).willPick(0);

        useCase.systemAdvancesNpcs();

        // Batch-then-dispatch: the SECOND NPC's decision read (findScene scn3) happens BEFORE the FIRST NPC's
        // command is dispatched — impossible if execution were interleaved into the decision loop.
        InOrder order = inOrder(sceneOps, commandOps);
        order.verify(sceneOps).findScene(SceneId.of("scn1"));
        order.verify(sceneOps).findScene(SceneId.of("scn3"));
        order.verify(commandOps).dispatchWander(NpcId.of("npc1"), "north");
        order.verify(commandOps).dispatchWander(NpcId.of("npc2"), "west");
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(npcOps.findAllNpcs()).thenThrow(boom);

        useCase.systemAdvancesNpcs();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentNothingHappened();
        verifyNoInteractions(commandOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void playerInScene(String sceneId) {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(Player.builder()
                        .id(PlayerId.of("plr1")).currentScene(SceneId.of(sceneId))
                        .hitPoints(HitPoints.full(30)).version(1).build()));
    }

    private void noPlayer() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
    }

    private static Npc peacefulNpc(String id, String currentScene) {
        return npc(id, currentScene, false);
    }

    private static Npc hostileNpc(String id, String currentScene) {
        return npc(id, currentScene, true);
    }

    private static Npc npc(String id, String currentScene, boolean hostile) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(SceneId.of(currentScene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(10))
                .hostile(hostile)
                .build();
    }

    /** scn1 "Old Gate" with a single exit north -> scn2. */
    private static Scene gate() {
        return Scene.builder()
                .id(SceneId.of("scn1")).name("Old Gate")
                .shortDescription("A weathered archway.").fullDescription("A gate.")
                .exits(List.of(new Exit("north", SceneId.of("scn2"))))
                .build();
    }

    /** scn3 with a single exit west -> scn4. */
    private static Scene armouryWithExit() {
        return Scene.builder()
                .id(SceneId.of("scn3")).name("Armoury")
                .shortDescription("An armoury.").fullDescription("Empty racks.")
                .exits(List.of(new Exit("west", SceneId.of("scn4"))))
                .build();
    }
}
