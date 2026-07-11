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

import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransaction;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionAndFireAfterCommit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link DropUseCase} in isolation — every collaborator is mocked; the mirror of
 * {@link TakeUseCaseTest} with the coordinates criss-crossed (select by player, mutate by scene). The
 * transaction port is stubbed via the <em>plain</em> {@code (boolean, Runnable)} helpers, because drop
 * deliberately uses the plain overload rather than the lock-aware one — and the lock-propagation test pins
 * exactly that contrast: an {@link OptimisticLockingError} from the save is <b>not</b> translated into a
 * player outcome but rides to the catch-all (a held item is single-writer, so a lost race is a fault, not an
 * expected outcome; design-notes §5).
 *
 * <p>Note {@code Item} equality is by id, so the saved item is captured and its <em>location</em> asserted —
 * the proof the drop actually moved it onto the ground.
 */
@ExtendWith(MockitoExtension.class)
class DropUseCaseTest {

    private static final SceneId HERE = new SceneId("scn1");
    private static final PlayerId SELF = new PlayerId("plr1");

    @Mock
    private DropPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<PlayerId> selectTargetSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private DropUseCase useCase;

    @Test
    void dropsTheItemDesignatedByDescriptionAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item dagger = heldItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", SELF)).thenReturn(dagger);
        runTransactionAndFireAfterCommit(txOps);

        useCase.playerDropsTarget("dagger");

        // The item is saved onto the ground of the current scene (location moved, identity and version preserved)...
        Item saved = capturedSavedItem();
        assertThat(saved.getId()).isEqualTo(new ItemId("itm1"));
        assertThat(saved.getLocation()).isEqualTo(new Location.OnGround(HERE));
        // ...and the dropped item is presented only after the write commits.
        verify(presenter).presentItemDropped(saved);
    }

    @Test
    void dropsTheChosenCandidateAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item dagger = heldItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("itm0", "itm1"), SELF))
                .thenReturn(dagger);
        runTransactionAndFireAfterCommit(txOps);

        useCase.playerDropsChosenCandidate(2, List.of("itm0", "itm1"));

        Item saved = capturedSavedItem();
        assertThat(saved.getLocation()).isEqualTo(new Location.OnGround(HERE));
        verify(presenter).presentItemDropped(saved);
    }

    @Test
    void propagatesALockLossToTheCatchAllInsteadOfHandlingIt() {
        orientedAtScn1();
        Item dagger = heldItem("itm1", "A rusty dagger.");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", SELF)).thenReturn(dagger);
        // Unreachable today (a held item is single-writer) — precisely why drop mints no lock-loss outcome:
        // were it ever to fire, it is a wiring surprise and rides to the catch-all as a fault.
        OptimisticLockingError loss = new OptimisticLockingError("stale version");
        doThrow(loss).when(itemOps).saveItem(any());
        runTransaction(txOps);

        useCase.playerDropsTarget("dagger");

        verify(presenter).presentError(loss);
        verify(presenter, never()).presentItemDropped(any());
    }

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerDropsTarget("dagger");

        verifyNoInteractions(presenter, selectTargetSubcase, itemOps, txOps);
    }

    @Test
    void presentsNothingWhenTheSelectSubcaseHasAlreadyPresented() {
        orientedAtScn1();
        when(selectTargetSubcase.playerDesignatesTarget(any(), any())).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerDropsTarget("rusty");

        verifyNoInteractions(presenter, itemOps, txOps);
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        orientedAtScn1();
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", SELF)).thenThrow(boom);

        useCase.playerDropsTarget("dagger");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentItemDropped(any());
        verify(itemOps, never()).saveItem(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = Player.builder().id(SELF).currentScene(HERE).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Item capturedSavedItem() {
        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        return saved.getValue();
    }

    private static Item heldItem(String id, String shortDescription) {
        return Item.builder()
                .id(new ItemId(id))
                .location(new Location.HeldBy(SELF))
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
