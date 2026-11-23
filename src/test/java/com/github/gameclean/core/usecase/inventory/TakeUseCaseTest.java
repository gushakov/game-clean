package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionWithResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link TakeUseCase} in isolation — every collaborator is mocked. The use case is pure
 * orchestration: the {@code orient} subcase resolves the player and scene, the {@code select} subcase resolves
 * the item (both mocked here; their own outcomes are covered by their tests), and the use case mutates and
 * persists. The transaction port is stubbed via the result-returning lock-aware helper, which mirrors the
 * adapter: the action's result on a commit, the supplied loss value on a lost race, any other error propagated.
 *
 * <p>The interaction presents <em>once</em> on every path, and always after the transaction block: a subcase
 * signals {@link SubcaseAlreadyPresented} (then nothing more happens here); the happy path presents the taken
 * item once the block has returned; a lost optimistic-lock race presents "got away"; anything unhandled —
 * including a write that fails inside the block — routes to {@code presentError} with no success presented. Note {@code Item} equality is by
 * id, so the saved item is captured and its <em>location</em> asserted — the proof the take actually moved it.
 */
@ExtendWith(MockitoExtension.class)
class TakeUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");

    @Mock
    private TakePresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, Item> selectTargetSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private TakeUseCase useCase;

    @Test
    void takesTheItemDesignatedByDescriptionAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenReturn(dagger);
        runLockAwareTransactionWithResult(txOps);

        useCase.playerTakesTarget("dagger");

        // The item is saved into the player's keeping (location moved, identity and version preserved)...
        Item saved = capturedSavedItem();
        assertThat(saved.getId()).isEqualTo(ItemId.of("itm1"));
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(PlayerId.of("plr1")));
        // ...and the taken item is presented only once the write has been made, never ahead of it.
        InOrder writeThenPresent = inOrder(itemOps, presenter);
        writeThenPresent.verify(itemOps).saveItem(saved);
        writeThenPresent.verify(presenter).presentItemTaken(saved);
        verify(presenter, never()).presentItemGotAway(any());
    }

    @Test
    void takesTheChosenCandidateAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("itm0", "itm1"), HERE))
                .thenReturn(dagger);
        runLockAwareTransactionWithResult(txOps);

        useCase.playerTakesChosenCandidate(2, List.of("itm0", "itm1"));

        Item saved = capturedSavedItem();
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(PlayerId.of("plr1")));
        verify(presenter).presentItemTaken(saved);
    }

    @Test
    void presentsItemGotAwayWhenTheVersionedWriteLosesTheRace() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenReturn(dagger);
        // The item was present when selected, but another actor's take committed first.
        doThrow(new OptimisticLockingError("stale version")).when(itemOps).saveItem(any());
        runLockAwareTransactionWithResult(txOps);

        useCase.playerTakesTarget("dagger");

        verify(presenter).presentItemGotAway(ItemId.of("itm1"));
        verify(presenter, never()).presentItemTaken(any());
        verify(presenter, never()).presentError(any());
    }

    @Test
    void presentsOnlyTheErrorWhenTheWriteFailsInsideTheTransaction() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenReturn(dagger);
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(itemOps).saveItem(any());
        runLockAwareTransactionWithResult(txOps);

        useCase.playerTakesTarget("dagger");

        // A fault is not a lost race: it unwinds past the statements after the block, so nothing that
        // follows the transaction runs on a rollback — neither the success nor the "got away" stripe.
        verify(presenter).presentError(boom);
        verify(presenter, never()).presentItemTaken(any());
        verify(presenter, never()).presentItemGotAway(any());
    }

    @Test
    void refusesToTakeAnAnchoredItemAndWritesNothing() {
        orientedAtScn1();
        Item chest = Item.builder()
                .id(ItemId.of("itm6"))
                .location(new Location.OnGround(HERE))
                .shortDescription("A barnacled sea chest.")
                .fullDescription("A longer description of the chest.")
                .container(true)
                .anchored(true)
                .version(1)
                .build();
        when(selectTargetSubcase.playerDesignatesTarget("chest", HERE)).thenReturn(chest);

        useCase.playerTakesTarget("chest");

        // A business refusal decided on the domain fact, before any write or transaction opens.
        verify(presenter).presentItemAnchored(chest);
        verify(presenter, never()).presentItemTaken(any());
        verifyNoInteractions(itemOps, txOps);
    }

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTakesTarget("dagger");

        verifyNoInteractions(presenter, selectTargetSubcase, itemOps, txOps);
    }

    @Test
    void presentsNothingWhenTheSelectSubcaseHasAlreadyPresented() {
        orientedAtScn1();
        when(selectTargetSubcase.playerDesignatesTarget(any(), any())).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTakesTarget("rusty");

        verifyNoInteractions(presenter, itemOps, txOps);
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        orientedAtScn1();
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenThrow(boom);

        useCase.playerTakesTarget("dagger");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentItemTaken(any());
        verify(itemOps, never()).saveItem(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = Player.builder().id(PlayerId.of("plr1")).currentScene(HERE)
                .hitPoints(HitPoints.full(30)).version(1).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Item capturedSavedItem() {
        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        return saved.getValue();
    }

    private static Item groundItem(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.OnGround(HERE))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
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
