package com.github.gameclean.core.usecase.inventory;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
 * Interaction tests for {@link TakeUseCase} in isolation — every collaborator is mocked. The use case is pure
 * orchestration: the {@code orient} subcase resolves the player and scene, the {@code select} subcase resolves
 * the item (both mocked here; their own outcomes are covered by their tests), and the use case mutates and
 * persists. The transaction port is stubbed via the lock-aware helpers, so the success path's after-commit
 * presentation and the lock-loss path's handler are both observable synchronously.
 *
 * <p>The interaction presents <em>once</em> on every path: a subcase signals {@link SubcaseAlreadyPresented}
 * (then nothing more happens here); the happy path presents the taken item after commit; a lost optimistic-lock
 * race presents "got away"; anything unhandled routes to {@code presentError}. Note {@code Item} equality is by
 * id, so the saved item is captured and its <em>location</em> asserted — the proof the take actually moved it.
 */
@ExtendWith(MockitoExtension.class)
class TakeUseCaseTest {

    private static final SceneId HERE = new SceneId("scn1");

    @Mock
    private TakePresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort selectTargetSubcase;
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
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesTarget("dagger");

        // The item is saved into the player's keeping (location moved, identity and version preserved)...
        Item saved = capturedSavedItem();
        assertThat(saved.getId()).isEqualTo(new ItemId("itm1"));
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(new PlayerId("plr1")));
        // ...and the taken item is presented only after the write commits.
        verify(presenter).presentItemTaken(saved);
        verify(presenter, never()).presentItemGotAway(any());
    }

    @Test
    void takesTheChosenCandidateAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("itm0", "itm1"), HERE))
                .thenReturn(dagger);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesChosenCandidate(2, List.of("itm0", "itm1"));

        Item saved = capturedSavedItem();
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(new PlayerId("plr1")));
        verify(presenter).presentItemTaken(saved);
    }

    @Test
    void presentsItemGotAwayWhenTheVersionedWriteLosesTheRace() {
        orientedAtScn1();
        Item dagger = groundItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenReturn(dagger);
        // The item was present when selected, but another actor's take committed first.
        doThrow(new OptimisticLockingError("stale version")).when(itemOps).saveItem(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.playerTakesTarget("dagger");

        verify(presenter).presentItemGotAway(new ItemId("itm1"));
        verify(presenter, never()).presentItemTaken(any());
        verify(presenter, never()).presentError(any());
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
        Player player = Player.builder().id(new PlayerId("plr1")).currentScene(HERE).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Item capturedSavedItem() {
        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        return saved.getValue();
    }

    private static Item groundItem(String id, String shortDescription) {
        return Item.builder()
                .id(new ItemId(id))
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
