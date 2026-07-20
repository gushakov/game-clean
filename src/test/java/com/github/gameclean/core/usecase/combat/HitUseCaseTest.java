package com.github.gameclean.core.usecase.combat;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionAndFireAfterCommit;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionDetectingLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link HitUseCase} in isolation — every collaborator is mocked. The use case is pure
 * orchestration: the {@code orient} subcase resolves the player and scene, the {@code select} subcase resolves
 * the NPC (both mocked here; their own outcomes are covered by their tests), the {@code Dice} yields the
 * damage, and the use case lowers the NPC and persists. The transaction port is stubbed via the lock-aware
 * helpers, so the success path's after-commit presentation and the lock-loss path's handler are both
 * observable synchronously.
 *
 * <p>The interaction presents <em>once</em> on every path: a subcase signals {@link SubcaseAlreadyPresented}
 * (then nothing more happens here); a landed strike presents slain or struck after commit; a lost
 * optimistic-lock race presents "got away"; anything unhandled routes to {@code presentError}. The saved NPC is
 * captured and its hit points asserted — the proof the strike actually lowered them.
 */
@ExtendWith(MockitoExtension.class)
class HitUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");

    @Mock
    private HitPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private TransactionOperationsOutputPort txOps;
    @Mock
    private Dice dice;

    @InjectMocks
    private HitUseCase useCase;

    @Test
    void strikesTheNpcDesignatedByDescriptionAndPresentsStruckAfterCommit() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsTarget("hooded");

        // The NPC is saved with its hit points lowered (identity and version preserved) ...
        Npc saved = capturedSavedNpc();
        assertThat(saved.getId()).isEqualTo(NpcId.of("npc1"));
        assertThat(saved.getHitPoints()).isEqualTo(new HitPoints(16, 20));
        // ... and the struck outcome (with the damage dealt) is presented only after the write commits.
        verify(presenter).presentNpcStruck(saved, 4);
        verify(presenter, never()).presentNpcSlain(any());
        verify(presenter, never()).presentNpcGotAway(any());
    }

    @Test
    void strikesTheChosenCandidateAndPresentsStruckAfterCommit() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20);
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("npc0", "npc1"), HERE))
                .thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsChosenCandidate(2, List.of("npc0", "npc1"));

        Npc saved = capturedSavedNpc();
        assertThat(saved.getHitPoints()).isEqualTo(new HitPoints(16, 20));
        verify(presenter).presentNpcStruck(saved, 4);
    }

    @Test
    void presentsNpcSlainWhenTheStrikeDepletesHitPoints() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 3);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(9);   // overkill floors at zero — dead
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsTarget("hooded");

        Npc saved = capturedSavedNpc();
        assertThat(saved.isDead()).isTrue();
        verify(presenter).presentNpcSlain(saved);
        verify(presenter, never()).presentNpcStruck(any(), anyInt());
    }

    @Test
    void presentsNpcGotAwayWhenTheVersionedWriteLosesTheRace() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
        // The NPC was present when selected, but the wandering ticker's write committed first.
        doThrow(new OptimisticLockingError("stale version")).when(npcOps).saveNpc(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.playerHitsTarget("hooded");

        verify(presenter).presentNpcGotAway(NpcId.of("npc1"));
        verify(presenter, never()).presentNpcStruck(any(), anyInt());
        verify(presenter, never()).presentNpcSlain(any());
        verify(presenter, never()).presentError(any());
    }

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerHitsTarget("hooded");

        verifyNoInteractions(presenter, selectTargetSubcase, npcOps, txOps, dice);
    }

    @Test
    void presentsNothingWhenTheSelectSubcaseHasAlreadyPresented() {
        orientedAtScn1();
        when(selectTargetSubcase.playerDesignatesTarget(any(), any())).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerHitsTarget("hooded");

        verifyNoInteractions(presenter, npcOps, txOps, dice);
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        orientedAtScn1();
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenThrow(boom);

        useCase.playerHitsTarget("hooded");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentNpcStruck(any(), anyInt());
        verify(npcOps, never()).saveNpc(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = Player.builder().id(PlayerId.of("plr1")).currentScene(HERE).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Npc capturedSavedNpc() {
        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        return saved.getValue();
    }

    private static Npc npc(String id, int maxHitPoints) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(HERE)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(maxHitPoints))
                .version(1)
                .build();
    }

    private static Scene scn1() {
        return Scene.builder()
                .id(HERE)
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
    }
}
