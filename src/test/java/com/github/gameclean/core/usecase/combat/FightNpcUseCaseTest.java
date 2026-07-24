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
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
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
import java.util.Optional;

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
 * Interaction tests for {@link FightNpcUseCase} in isolation — every collaborator is mocked. The use case
 * carries both actors of the fight: the player striking an NPC ({@code playerHits*}) and a provoked NPC
 * striking back ({@code npcStrikesPlayer}).
 *
 * <p><b>Player strikes.</b> Pure orchestration: the {@code orient} subcase resolves player + scene, the
 * {@code select} subcase resolves the NPC (both mocked; their own outcomes are covered by their tests), the
 * {@code Dice} yields the damage, the use case lowers the NPC, provokes a survivor, and persists. It presents
 * once on every path — struck / slain after commit, "got away" on a lost lock race, {@code presentError}
 * otherwise — and a subcase's {@link SubcaseAlreadyPresented} is swallowed.
 *
 * <p><b>NPC strikes back.</b> {@code npcStrikesPlayer} re-validates the policy's decision at execution: NPC
 * present and alive, player present, co-located. Any miss is the quiet {@code presentNothingHappened}; a landed
 * blow lowers the player and presents struck-player / player-slain after commit; a lost lock race is quiet.
 */
@ExtendWith(MockitoExtension.class)
class FightNpcUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");
    private static final SceneId ELSEWHERE = SceneId.of("scn2");

    @Mock
    private FightNpcPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private TransactionOperationsOutputPort txOps;
    @Mock
    private Dice dice;

    @InjectMocks
    private FightNpcUseCase useCase;

    // --- player strikes NPC ------------------------------------------------------------------------

    @Test
    void strikesTheNpcDesignatedByDescriptionAndPresentsStruckAfterCommit() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20, HERE);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsTarget("hooded");

        // The NPC is saved with its hit points lowered, provoked into the hostile stance (it survived) ...
        Npc saved = capturedSavedNpc();
        assertThat(saved.getId()).isEqualTo(NpcId.of("npc1"));
        assertThat(saved.getHitPoints()).isEqualTo(new HitPoints(16, 20));
        assertThat(saved.isHostile()).isTrue();
        // ... and the struck outcome (with the damage dealt) is presented only after the write commits.
        verify(presenter).presentNpcStruck(saved, 4);
        verify(presenter, never()).presentNpcSlain(any());
        verify(presenter, never()).presentNpcGotAway(any());
    }

    @Test
    void strikesTheChosenCandidateAndPresentsStruckAfterCommit() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20, HERE);
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("npc0", "npc1"), HERE))
                .thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsChosenCandidate(2, List.of("npc0", "npc1"));

        Npc saved = capturedSavedNpc();
        assertThat(saved.getHitPoints()).isEqualTo(new HitPoints(16, 20));
        assertThat(saved.isHostile()).isTrue();
        verify(presenter).presentNpcStruck(saved, 4);
    }

    @Test
    void presentsNpcSlainWhenTheStrikeDepletesHitPointsAndDoesNotProvokeTheDead() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 3, HERE);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(9);   // overkill floors at zero — dead
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerHitsTarget("hooded");

        Npc saved = capturedSavedNpc();
        assertThat(saved.isDead()).isTrue();
        assertThat(saved.isHostile()).isFalse();   // a slain NPC is not provoked (it won't fight from the grave)
        verify(presenter).presentNpcSlain(saved);
        verify(presenter, never()).presentNpcStruck(any(), anyInt());
    }

    @Test
    void presentsNpcGotAwayWhenTheVersionedWriteLosesTheRace() {
        orientedAtScn1();
        Npc goblin = npc("npc1", 20, HERE);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(goblin);
        when(dice.rollDie(10)).thenReturn(4);
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

    // --- NPC strikes back (secondary actor) --------------------------------------------------------

    @Test
    void npcStrikesPlayerLandsAndPresentsStruckPlayerAfterCommit() {
        Npc goblin = hostileNpc("npc1", HERE);
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(goblin));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.of(player(HERE, 30, 30)));
        when(dice.rollDie(10)).thenReturn(6);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.npcStrikesPlayer("npc1");

        // The player is saved with hit points lowered ...
        Player saved = capturedSavedPlayer();
        assertThat(saved.getHitPoints()).isEqualTo(new HitPoints(24, 30));
        // ... and the struck-player outcome (naming the striker, the damage, the survivor) presented after commit.
        verify(presenter).presentNpcStruckPlayer(goblin, 6, saved);
        verify(presenter, never()).presentPlayerSlain(any(), anyInt());
        verify(presenter, never()).presentNothingHappened();
    }

    @Test
    void npcStrikesPlayerPresentsPlayerSlainWhenTheBlowIsLethal() {
        Npc goblin = hostileNpc("npc1", HERE);
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(goblin));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.of(player(HERE, 4, 30)));
        when(dice.rollDie(10)).thenReturn(9);   // overkill floors at zero — the player is slain
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.npcStrikesPlayer("npc1");

        Player saved = capturedSavedPlayer();
        assertThat(saved.isDead()).isTrue();
        verify(presenter).presentPlayerSlain(goblin, 9);
        verify(presenter, never()).presentNpcStruckPlayer(any(), anyInt(), any());
    }

    @Test
    void npcStrikesPlayerIsQuietWhenThePlayerIsAlreadyDead() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(hostileNpc("npc1", HERE)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        // The player was slain on an earlier tick and is still co-located — a corpse must not be struck again.
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.of(player(HERE, 0, 30)));

        useCase.npcStrikesPlayer("npc1");

        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentPlayerSlain(any(), anyInt());
        verify(presenter, never()).presentNpcStruckPlayer(any(), anyInt(), any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verifyNoInteractions(dice);   // no blow is rolled at a corpse
    }

    @Test
    void npcStrikesPlayerWhiffsQuietlyWhenThePlayerHasMovedAway() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(hostileNpc("npc1", HERE)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(player(ELSEWHERE, 30, 30)));   // player is no longer co-located

        useCase.npcStrikesPlayer("npc1");

        verify(presenter).presentNothingHappened();
        verify(playerRepositoryOps, never()).savePlayer(any());
        verifyNoInteractions(dice);
    }

    @Test
    void npcStrikesPlayerIsQuietWhenTheStrikerIsGoneOrDead() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.empty());   // gone or dead since the decision

        useCase.npcStrikesPlayer("npc1");

        verify(presenter).presentNothingHappened();
        verifyNoInteractions(playerRepositoryOps, txOps, dice);
    }

    @Test
    void npcStrikesPlayerIsQuietWhenThereIsNoPlayer() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(hostileNpc("npc1", HERE)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());

        useCase.npcStrikesPlayer("npc1");

        verify(presenter).presentNothingHappened();
        verify(playerRepositoryOps, never()).savePlayer(any());
    }

    @Test
    void npcStrikesPlayerIsQuietWhenTheVersionedWriteLosesTheRace() {
        when(npcOps.findNpc(NpcId.of("npc1"))).thenReturn(Optional.of(hostileNpc("npc1", HERE)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.of(player(HERE, 30, 30)));
        when(dice.rollDie(10)).thenReturn(6);
        // The player's own move committed first — the counterstrike's versioned write loses.
        doThrow(new OptimisticLockingError("stale version")).when(playerRepositoryOps).savePlayer(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.npcStrikesPlayer("npc1");

        verify(presenter).presentNothingHappened();
        verify(presenter, never()).presentNpcStruckPlayer(any(), anyInt(), any());
        verify(presenter, never()).presentError(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = player(HERE, 30, 30);
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Npc capturedSavedNpc() {
        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        return saved.getValue();
    }

    private Player capturedSavedPlayer() {
        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(saved.capture());
        return saved.getValue();
    }

    private static Player player(SceneId scene, int currentHp, int maxHp) {
        return Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(scene)
                .hitPoints(new HitPoints(currentHp, maxHp))
                .version(1)
                .build();
    }

    private static Npc npc(String id, int maxHitPoints, SceneId scene) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(scene)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(maxHitPoints))
                .hostile(false)
                .version(1)
                .build();
    }

    private static Npc hostileNpc(String id, SceneId scene) {
        return npc(id, 20, scene).provoked();
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
