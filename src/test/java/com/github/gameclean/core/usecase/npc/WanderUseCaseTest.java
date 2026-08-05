package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionAndFireAfterCommit;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionDetectingLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link WanderUseCase} in isolation — the executing interaction for a dispatched wander
 * (#66 step 2). Every port is mocked. The exit was chosen upstream by the policy and carried in the command, so
 * this use case only re-validates and moves.
 *
 * <p>It presents once on every path: a perceptible move (from or into the player's scene) narrates via
 * {@code presentNpcMovement} after commit; an off-stage move, a gone NPC / scene / exit / dangling target, or a
 * lost lock race present the quiet {@code presentNothingHappened}. The saved NPC's new position is asserted on
 * the narrated path.
 */
@ExtendWith(MockitoExtension.class)
class WanderUseCaseTest {

    @Mock
    private WanderPresenterOutputPort presenter;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private WanderUseCase useCase;

    @Test
    void narratesADepartureWhenTheNpcLeavesThePlayersSceneAndSavesTheMove() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn1");   // player watches from the source scene
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.npcWandersThrough("npc1", "north");

        // The NPC is saved at its new scene ...
        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        assertThat(saved.getValue().getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        // ... and the player, standing where it left, sees a departure by the exit name.
        assertPerceived(MovementKind.DEPARTED, "npc1", "north");
    }

    @Test
    void narratesAnArrivalWhenTheNpcEntersThePlayersScene() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn2");   // player watches from the target scene
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.npcWandersThrough("npc1", "north");

        verify(npcOps).saveNpc(any(Npc.class));
        // The arrival detail is the source scene's name, not an exit.
        assertPerceived(MovementKind.ARRIVED, "npc1", "Old Gate");
    }

    @Test
    void savesAnOffstageMoveButPresentsNothingHappened() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn3");   // player is elsewhere — the move touches neither their scene
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.npcWandersThrough("npc1", "north");

        verify(npcOps).saveNpc(any(Npc.class));
        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentNpcMovement(any());
    }

    @Test
    void isQuietWhenTheNpcIsGoneOrDead() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.empty());

        useCase.npcWandersThrough("npc1", "north");

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
        verifyNoInteractions(sceneOps, playerOps, playerRepositoryOps, txOps);
    }

    @Test
    void isQuietWhenTheCurrentSceneCannotBeResolved() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scnX")));
        when(sceneOps.findScene(SceneId.of("scnX"))).thenReturn(Optional.empty());

        useCase.npcWandersThrough("npc1", "north");

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
    }

    @Test
    void isQuietWhenTheNamedExitHasVanished() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));   // has only "north"

        useCase.npcWandersThrough("npc1", "west");   // the exit the policy chose is no longer here

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
    }

    @Test
    void isQuietWhenTheExitTargetDangles() {
        Scene gateToNowhere = Scene.builder()
                .id(SceneId.of("scn1")).name("Old Gate")
                .shortDescription("A weathered archway.").fullDescription("A gate.")
                .exits(List.of(new Exit("north", SceneId.of("scn9"))))
                .build();
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gateToNowhere));
        when(sceneOps.findScene(SceneId.of("scn9"))).thenReturn(Optional.empty());

        useCase.npcWandersThrough("npc1", "north");

        verify(presenter).presentNothingHappened();
        verify(npcOps, never()).saveNpc(any());
    }

    @Test
    void isQuietWhenTheVersionedWriteLosesTheRace() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(npc("npc1", "scn1")));
        when(sceneOps.findScene(SceneId.of("scn1"))).thenReturn(Optional.of(gate()));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard()));
        playerInScene("scn1");
        doThrow(new OptimisticLockingError("stale version")).when(npcOps).saveNpc(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.npcWandersThrough("npc1", "north");

        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentNpcMovement(any());
        verify(presenter, never()).presentError(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void playerInScene(String sceneId) {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(Player.builder()
                        .id(PlayerId.of("plr1")).currentScene(SceneId.of(sceneId))
                        .hitPoints(HitPoints.full(30)).version(1).build()));
    }

    private void assertPerceived(MovementKind kind, String npcId, String detail) {
        ArgumentCaptor<PerceivedNpcMovement> captor = ArgumentCaptor.forClass(PerceivedNpcMovement.class);
        verify(presenter).presentNpcMovement(captor.capture());
        PerceivedNpcMovement movement = captor.getValue();
        assertThat(movement.getKind()).isEqualTo(kind);
        assertThat(movement.getNpc().getId()).isEqualTo(NpcId.of(npcId));
        assertThat(movement.getDetail()).isEqualTo(detail);
        verify(presenter, never()).presentNothingHappened();
    }

    private static Npc npc(String id, String currentScene) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(SceneId.of(currentScene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(10))
                .build();
    }

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
}
