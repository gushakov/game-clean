package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link InventoryUseCase} in isolation — every port is mocked. The read-only
 * counterpart of the {@code take}/{@code drop} tests: no transaction port exists to stub, and the opening
 * is the inlined player resolution rather than the mocked {@code orient} subcase (inventory is grounded in
 * the player alone — no scene port is a collaborator at all).
 */
@ExtendWith(MockitoExtension.class)
class InventoryUseCaseTest {

    private static final PlayerId SELF = new PlayerId("plr1");

    @Mock
    private InventoryPresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private InventoryUseCase useCase;

    @Test
    void presentsTheItemsThePlayerIsCarrying() {
        ambientPlayerResolves();
        List<Item> keeping = List.of(heldItem("itm1", "A rusty dagger."), heldItem("itm2", "A tin whistle."));
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(keeping);

        useCase.playerReviewsBelongings();

        assertPresentedCarriedItems(keeping);
    }

    @Test
    void presentsAnEmptyKeepingAsTheSameOutcome() {
        // Carrying nothing is not a distinct stripe: the same presentation with an empty list, phrased by
        // the renderer (the presentScene empty-ground precedent).
        ambientPlayerResolves();
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of());

        useCase.playerReviewsBelongings();

        assertPresentedCarriedItems(List.of());
    }

    @Test
    void presentsPlayerNotFoundWhenTheAmbientPlayerDoesNotResolve() {
        when(playerOps.currentPlayerId()).thenReturn(SELF.getValue());
        when(playerRepositoryOps.findPlayer(SELF)).thenReturn(Optional.empty());

        useCase.playerReviewsBelongings();

        verify(presenter).presentPlayerNotFound(SELF);
        verify(presenter, never()).presentCarriedItems(any());
        verifyNoInteractions(itemOps);
    }

    @Test
    void routesAMalformedConfiguredIdToTheCatchAll() {
        // A malformed ambient id fails the value-object construction gate; that is a wiring fault, not a
        // player outcome, so it rides to the catch-all.
        when(playerOps.currentPlayerId()).thenReturn("no-prefix");

        useCase.playerReviewsBelongings();

        Exception presented = capturedError();
        assertThat(presented).isInstanceOf(InvalidDomainObjectError.class);
        verifyNoInteractions(playerRepositoryOps, itemOps);
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        ambientPlayerResolves();
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(itemOps.findItemsHeldBy(SELF)).thenThrow(boom);

        useCase.playerReviewsBelongings();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentCarriedItems(any());
    }

    // --- fixtures and captors -------------------------------------------------------------------------

    private void ambientPlayerResolves() {
        when(playerOps.currentPlayerId()).thenReturn(SELF.getValue());
        Player player = Player.builder().id(SELF).currentScene(new SceneId("scn1")).build();
        when(playerRepositoryOps.findPlayer(SELF)).thenReturn(Optional.of(player));
    }

    private void assertPresentedCarriedItems(List<Item> expected) {
        ArgumentCaptor<List<Item>> captor = carriedItemsCaptor();
        verify(presenter).presentCarriedItems(captor.capture());
        assertThat(captor.getValue()).containsExactlyElementsOf(expected);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Item>> carriedItemsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private Exception capturedError() {
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(presenter).presentError(captor.capture());
        return captor.getValue();
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
}
