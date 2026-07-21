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
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionAndFireAfterCommit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link AnimateNpcsUseCase} in isolation — every output port is mocked and the use case
 * is exercised directly through its input port. The move is non-deterministic, so a {@link ScriptedDice} is
 * scripted with fixed rolls and exit picks, making both the hit/miss decision and the chosen exit reproducible.
 * The transaction port is stubbed to run its action inline and fire after-commit callbacks immediately, so the
 * single post-commit presentation is observable.
 *
 * <p>The interaction presents <em>once</em> on every path: an empty world, a tick where nothing moved, and a
 * tick with no perceptible movement all present {@code presentNothingHappened} (the first two before any
 * transaction); a tick with witnessed movement presents {@code presentNpcMovements} after commit; an unhandled
 * fault routes to {@code presentError}. The perceptibility filter (DEPARTED / ARRIVED / off-stage) and the
 * silent-skip cases (dead-end, dangling target, unresolved scene, no player) are pinned individually.
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
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private AnimateNpcsUseCase useCase;

    @Test
    void presentsNothingHappenedForAnEmptyWorldWithNoTransaction() {
        when(npcOps.findAllNpcs()).thenReturn(List.of());

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verifyNoInteractions(sceneOps, playerOps, playerRepositoryOps);
    }

    @Test
    void aMissMovesNothingAndPresentsNothingHappenedWithNoTransaction() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        dice.willRoll(false);   // rolls but misses; no pick, no move

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        // The player is never resolved when nothing moves.
        verifyNoInteractions(playerOps, playerRepositoryOps);
    }

    @Test
    void narratesADepartureWhenTheNpcLeavesThePlayersScene() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn1");   // player watches from the source scene
        dice.willRoll(true).willPick(0);   // hit, take the only exit (north -> scn2)
        runTransactionAndFireAfterCommit(txOps);

        useCase.systemAdvancesNpcs();

        // The NPC is saved at its new scene ...
        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        assertThat(saved.getValue().getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        // ... and the player, standing where it left, sees a departure by the exit name.
        assertSinglePerceived(MovementKind.DEPARTED, "npc1", "north");
    }

    @Test
    void narratesAnArrivalWhenTheNpcEntersThePlayersScene() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn2");   // player watches from the target scene
        dice.willRoll(true).willPick(0);
        runTransactionAndFireAfterCommit(txOps);

        useCase.systemAdvancesNpcs();

        verify(npcOps).saveNpc(any(Npc.class));
        // The arrival detail is the source scene's name, not an exit.
        assertSinglePerceived(MovementKind.ARRIVED, "npc1", "Old Gate");
    }

    @Test
    void savesAnOffstageMoveButPresentsNothingHappened() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn3");   // player is elsewhere: the move touches neither their scene
        dice.willRoll(true).willPick(0);
        runTransactionAndFireAfterCommit(txOps);

        useCase.systemAdvancesNpcs();

        // The move still persists ...
        verify(npcOps).saveNpc(any(Npc.class));
        // ... but there is nothing the player can witness.
        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentNpcMovements(any());
    }

    @Test
    void savesTheMoveButNarratesNothingWhenNoPlayerCanBeResolved() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0);
        runTransactionAndFireAfterCommit(txOps);

        useCase.systemAdvancesNpcs();

        verify(npcOps).saveNpc(any(Npc.class));
        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentNpcMovements(any());
    }

    @Test
    void skipsADeadEndNpcSilentlyWithoutRollingAndPresentsNothingHappened() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn3")));
        when(sceneOps.findScene(SceneId.of("scn3"))).thenReturn(Optional.of(armoury()));   // no exits
        // No roll is scripted: the dead-end is skipped before the dice are touched, and an unscripted roll
        // would throw — pinning that a dead-end NPC never rolls.

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
    }

    @Test
    void skipsAnNpcWhoseCurrentSceneCannotBeResolved() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scnX")));
        when(sceneOps.findScene(SceneId.of("scnX"))).thenReturn(Optional.empty());

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
    }

    @Test
    void skipsAMoveIntoADanglingExitTargetSilently() {
        Scene gateToNowhere = Scene.builder()
                .id(SceneId.of("scn1")).name("Old Gate")
                .shortDescription("A weathered archway.").fullDescription("A gate.")
                .exits(List.of(new Exit("north", SceneId.of("scn9"))))   // target never resolves
                .build();
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gateToNowhere));
        when(sceneOps.findScene(SceneId.of("scn9"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0);   // hit and pick, but the target dangles

        useCase.systemAdvancesNpcs();

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
    }

    @Test
    void narratesOnlyThePerceptibleOfSeveralMovesButSavesThemAll() {
        when(npcOps.findAllNpcs()).thenReturn(List.of(npc("npc1", "scn1"), npc("npc2", "scn3")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));            // npc1: scn1 -> scn2
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        when(sceneOps.findScene(SceneId.of("scn3"))).thenReturn(Optional.of(armouryWithExit())); // npc2: scn3 -> scn4
        when(sceneOps.findScene(SceneId.of("scn4"))).thenReturn(Optional.of(watchtower()));
        playerInScene("scn1");   // sees npc1 depart; npc2's scn3->scn4 is off-stage
        dice.willRoll(true).willPick(0)   // npc1 hits, takes its only exit
                .willRoll(true).willPick(0);   // npc2 hits, takes its only exit
        runTransactionAndFireAfterCommit(txOps);

        useCase.systemAdvancesNpcs();

        // Both moves persist ...
        verify(npcOps, times(2)).saveNpc(any(Npc.class));
        // ... but only npc1's departure is narrated.
        assertSinglePerceived(MovementKind.DEPARTED, "npc1", "north");
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(npcOps.findAllNpcs()).thenThrow(boom);

        useCase.systemAdvancesNpcs();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentNpcMovements(any());
        verify(presenter, never()).presentNothingHappened();
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void playerInScene(String sceneId) {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(Player.builder()
                        .id(PlayerId.of("plr1")).currentScene(SceneId.of(sceneId)).build()));
    }

    private void assertSinglePerceived(MovementKind kind, String npcId, String detail) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerceivedNpcMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentNpcMovements(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(movement -> {
            assertThat(movement.getKind()).isEqualTo(kind);
            assertThat(movement.getNpc().getId()).isEqualTo(NpcId.of(npcId));
            assertThat(movement.getDetail()).isEqualTo(detail);
        });
        verify(presenter, never()).presentNothingHappened();
    }

    private static Npc npc(String id, String currentScene) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(SceneId.of(currentScene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(10))
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

    private static Scene courtyard() {
        return Scene.builder()
                .id(SceneId.of("scn2")).name("Courtyard")
                .shortDescription("A courtyard.").fullDescription("A grassy yard.")
                .exits(List.of(new Exit("south", SceneId.of("scn1"))))
                .build();
    }

    /** scn3 "Armoury" with no exits — a dead-end. */
    private static Scene armoury() {
        return Scene.builder()
                .id(SceneId.of("scn3")).name("Armoury")
                .shortDescription("An armoury.").fullDescription("Empty racks.")
                .exits(List.of())
                .build();
    }

    /** scn3 with a single exit west -> scn4, for the multi-NPC case. */
    private static Scene armouryWithExit() {
        return Scene.builder()
                .id(SceneId.of("scn3")).name("Armoury")
                .shortDescription("An armoury.").fullDescription("Empty racks.")
                .exits(List.of(new Exit("west", SceneId.of("scn4"))))
                .build();
    }

    private static Scene watchtower() {
        return Scene.builder()
                .id(SceneId.of("scn4")).name("Watchtower")
                .shortDescription("A watchtower.").fullDescription("A high parapet.")
                .exits(List.of(new Exit("down", SceneId.of("scn3"))))
                .build();
    }
}
