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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionAndFireAfterCommit;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionDetectingLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link TakeFromUseCase} in isolation — every collaborator is mocked. The {@code orient}
 * subcase resolves the player and scene and the {@code select} subcase resolves the contained item (both
 * mocked; their own outcomes are covered by their tests); what is this use case's own is the container
 * filter, the coordinate it hands to {@code select}, the anchored gate, and the contested write tail.
 *
 * <p>The interaction presents <em>once</em> on every path. {@code Item} equality is by id, so the saved item
 * is captured and its <em>location</em> asserted — the proof the take moved it out of the container.
 */
@ExtendWith(MockitoExtension.class)
class TakeFromUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");
    private static final PlayerId SELF = PlayerId.of("plr1");

    @Mock
    private TakeFromPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<List<ItemId>, Item> selectTargetSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private TakeFromUseCase useCase;

    // --- playerTakesTargetFrom (designation by description) -----------------------------------------

    @Test
    void takesTheItemOutOfTheDesignatedContainerAndPresentsItAfterCommit() {
        orientedAtScn1();
        Item corpse = container("itm5", "The corpse of a hooded wanderer.");
        Item ring = contained("itm9", "A tarnished silver ring.", corpse);
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(corpse, groundItem("itm1", "A rusty dagger.")));
        when(selectTargetSubcase.playerDesignatesTarget("ring", List.of(corpse.getId()))).thenReturn(ring);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesTargetFrom("ring", "corpse");

        // Only the contained item's row is written — moved into the player's keeping, version preserved ...
        Item saved = capturedSavedItem();
        assertThat(saved.getId()).isEqualTo(ItemId.of("itm9"));
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(SELF));
        assertThat(saved.getVersion()).isEqualTo(ring.getVersion());
        // ... and the success, naming the container it came out of, is presented only after commit.
        verify(presenter).presentItemTakenFrom(saved, corpse);
        verify(presenter, never()).presentItemGotAway(any());
    }

    @Test
    void takesFromAnAnchoredContainerBecauseTheGateReadsTheItemNotTheContainer() {
        // The load-bearing semantic: a corpse IS anchored (the container polarity default) and is still
        // exactly what one takes from. 'take corpse' is refused by Take; 'take ring from corpse' is not.
        orientedAtScn1();
        Item corpse = container("itm5", "The corpse of a hooded wanderer.");
        assertThat(corpse.isAnchored()).isTrue();
        Item ring = contained("itm9", "A tarnished silver ring.", corpse);
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(corpse));
        when(selectTargetSubcase.playerDesignatesTarget("ring", List.of(corpse.getId()))).thenReturn(ring);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesTargetFrom("ring", "corpse");

        verify(presenter).presentItemTakenFrom(any(), any());
        verify(presenter, never()).presentItemAnchored(any());
    }

    @Test
    void searchesEveryContainerTheFragmentDesignatesRatherThanAskingWhichOne() {
        // Two look-alike corpses are not an ambiguity to resolve: both are handed to select as the coordinate,
        // and the success names the one the item actually lay in.
        orientedAtScn1();
        Item emptyCorpse = container("itm5", "The corpse of a hooded wanderer.");
        Item ladenCorpse = container("itm6", "The corpse of a hooded wanderer.");
        Item chest = container("itm7", "An oak chest.");
        Item ring = contained("itm9", "A tarnished silver ring.", ladenCorpse);
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(emptyCorpse, ladenCorpse, chest));
        when(selectTargetSubcase.playerDesignatesTarget("ring", List.of(emptyCorpse.getId(), ladenCorpse.getId())))
                .thenReturn(ring);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesTargetFrom("ring", "corpse");

        Item saved = capturedSavedItem();
        verify(presenter).presentItemTakenFrom(saved, ladenCorpse);
    }

    @Test
    void presentsNoSuchContainerWhenNothingOnTheGroundMatchesTheContainerFragment() {
        orientedAtScn1();
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(container("itm7", "An oak chest.")));

        useCase.playerTakesTargetFrom("ring", "corpse");

        verify(presenter).presentNoSuchContainer("corpse");
        verifyNoInteractions(selectTargetSubcase, txOps);
        verify(itemOps, never()).saveItem(any());
    }

    @Test
    void presentsNoSuchContainerWhenWhatTheFragmentDesignatesCannotHoldThings() {
        orientedAtScn1();
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(groundItem("itm1", "A rusty dagger.")));

        useCase.playerTakesTargetFrom("ring", "dagger");

        verify(presenter).presentNoSuchContainer("dagger");
        verifyNoInteractions(selectTargetSubcase, txOps);
    }

    @Test
    void refusesToTakeAnAnchoredContentAndWritesNothing() {
        orientedAtScn1();
        Item chest = container("itm7", "An oak chest.");
        Item anvil = Item.builder()
                .id(ItemId.of("itm9"))
                .location(new Location.Inside(chest.getId()))
                .shortDescription("A bolted-down anvil.")
                .fullDescription("A longer description of the item.")
                .anchored(true)
                .version(1)
                .build();
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(chest));
        when(selectTargetSubcase.playerDesignatesTarget("anvil", List.of(chest.getId()))).thenReturn(anvil);

        useCase.playerTakesTargetFrom("anvil", "chest");

        verify(presenter).presentItemAnchored(anvil);
        verify(presenter, never()).presentItemTakenFrom(any(), any());
        verify(itemOps, never()).saveItem(any());
        verifyNoInteractions(txOps);
    }

    @Test
    void presentsItemGotAwayWhenTheVersionedWriteLosesTheRace() {
        // Contents of a container on the ground are within any actor's reach, so take-from is contested like
        // a ground take: the lock-aware overload, with the loss as its own stripe — NOT propagated to
        // presentError, which is drop's (single-writer) shape.
        orientedAtScn1();
        Item corpse = container("itm5", "The corpse of a hooded wanderer.");
        Item ring = contained("itm9", "A tarnished silver ring.", corpse);
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(corpse));
        when(selectTargetSubcase.playerDesignatesTarget("ring", List.of(corpse.getId()))).thenReturn(ring);
        doThrow(new OptimisticLockingError("stale version")).when(itemOps).saveItem(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.playerTakesTargetFrom("ring", "corpse");

        verify(presenter).presentItemGotAway(ItemId.of("itm9"));
        verify(presenter, never()).presentItemTakenFrom(any(), any());
        verify(presenter, never()).presentError(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    // --- playerTakesChosenContent (designation by choosing from the offer) --------------------------

    @Test
    void takesTheChosenContentReValidatedAgainstEveryContainerOnTheGround() {
        // No fragment travels with a menu pick, so the coordinate is every container within reach — the
        // chest too, though the original command said 'corpse'.
        orientedAtScn1();
        Item corpse = container("itm5", "The corpse of a hooded wanderer.");
        Item chest = container("itm7", "An oak chest.");
        Item ring = contained("itm9", "A tarnished silver ring.", corpse);
        when(itemOps.findItemsInScene(HERE))
                .thenReturn(List.of(corpse, chest, groundItem("itm1", "A rusty dagger.")));
        when(selectTargetSubcase.playerDesignatesChosenCandidate(
                2, List.of("itm8", "itm9"), List.of(corpse.getId(), chest.getId()))).thenReturn(ring);
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.playerTakesChosenContent(2, List.of("itm8", "itm9"));

        Item saved = capturedSavedItem();
        assertThat(saved.getLocation()).isEqualTo(new Location.HeldBy(SELF));
        verify(presenter).presentItemTakenFrom(saved, corpse);
    }

    @Test
    void handsSelectAnEmptyCoordinateWhenNoContainerIsLeftSoItPresentsTheMiss() {
        // The containers went away between the offer and the pick: not this use case's outcome — the select
        // subcase, finding nothing, presents "no longer available" and signals.
        orientedAtScn1();
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of());
        when(selectTargetSubcase.playerDesignatesChosenCandidate(1, List.of("itm9"), List.of()))
                .thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTakesChosenContent(1, List.of("itm9"));

        verifyNoInteractions(presenter, txOps);
    }

    // --- shared paths -------------------------------------------------------------------------------

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTakesTargetFrom("ring", "corpse");

        verifyNoInteractions(presenter, selectTargetSubcase, itemOps, txOps);
    }

    @Test
    void presentsNothingWhenTheSelectSubcaseHasAlreadyPresented() {
        orientedAtScn1();
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(container("itm5", "The corpse of a hooded wanderer.")));
        when(selectTargetSubcase.playerDesignatesTarget(any(), any())).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTakesTargetFrom("ring", "corpse");

        verifyNoInteractions(presenter, txOps);
        verify(itemOps, never()).saveItem(any());
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        orientedAtScn1();
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(itemOps.findItemsInScene(HERE)).thenThrow(boom);

        useCase.playerTakesTargetFrom("ring", "corpse");

        verify(presenter).presentError(boom);
        verify(itemOps, never()).saveItem(any());
    }

    @Test
    void routesAnItemLyingInNoneOfTheSearchedContainersToTheCatchAllAsAWiringFault() {
        orientedAtScn1();
        Item corpse = container("itm5", "The corpse of a hooded wanderer.");
        Item stray = groundItem("itm1", "A rusty dagger.");
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(corpse));
        when(selectTargetSubcase.playerDesignatesTarget("dagger", List.of(corpse.getId()))).thenReturn(stray);

        useCase.playerTakesTargetFrom("dagger", "corpse");

        verify(presenter).presentError(any(IllegalStateException.class));
        verify(itemOps, never()).saveItem(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = Player.builder().id(SELF).currentScene(HERE)
                .hitPoints(HitPoints.full(30)).version(1).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn1()));
    }

    private Item capturedSavedItem() {
        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        return saved.getValue();
    }

    /** A container on the ground here — anchored, as the container polarity default makes corpses and chests. */
    private static Item container(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.OnGround(HERE))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the container.")
                .container(true)
                .anchored(true)
                .version(1)
                .build();
    }

    private static Item contained(String id, String shortDescription, Item container) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.Inside(container.getId()))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .version(3)
                .build();
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
