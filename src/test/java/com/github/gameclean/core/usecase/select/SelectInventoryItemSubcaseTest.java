package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link SelectInventoryItemSubcase} in isolation — the inventory-sourced concrete of
 * the {@code select} Template Method, mirroring {@code SelectSceneItemSubcaseTest} with the provenance
 * swapped: candidates come from the player's keeping ({@code findItemsHeldBy}), keyed by a {@link PlayerId}
 * coordinate. The dialogue skeleton lives on the shared base and is pinned through each concrete, so both
 * provisioners exercise it against their own port stub; what is specific here is only the provenance.
 */
@ExtendWith(MockitoExtension.class)
class SelectInventoryItemSubcaseTest {

    private static final PlayerId SELF = PlayerId.of("plr1");

    @Mock
    private SelectTargetPresenterOutputPort<Item> presenter;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private SelectInventoryItemSubcase subcase;

    // --- playerDesignatesTarget (designation by description) ----------------------------------------

    @Test
    void returnsTheSingleCarriedItemAFragmentDesignates() {
        Item dagger = heldItem("itmRt4Xw7Kq", "A rusty dagger.");
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(dagger, heldItem("itmLm2bQ9Zx", "A brass lantern.")));

        Item resolved = subcase.playerDesignatesTarget("dagger", SELF);

        assertThat(resolved).isEqualTo(dagger);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsNoSuchTargetAndSignalsWhenAFragmentDesignatesNothingCarried() {
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(heldItem("itmRt4Xw7Kq", "A rusty dagger.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("sword", SELF))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("sword");
    }

    @Test
    void presentsAmbiguousTargetUnorderedAndSignalsWhenAFragmentMatchesMany() {
        Item rustyDagger = heldItem("itmRt4Xw7Kq", "A rusty dagger.");
        Item rustyKey = heldItem("itmKey8Pp3a", "A rusty key.");
        when(itemOps.findItemsHeldBy(SELF))
                .thenReturn(List.of(rustyDagger, rustyKey, heldItem("itmLm2bQ9Zx", "A brass lantern.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("rusty", SELF))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        ArgumentCaptor<List<Item>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("rusty"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(rustyDagger, rustyKey);
    }

    // --- playerDesignatesChosenCandidate (designation by choosing from the offer) -------------------

    @Test
    void returnsTheChosenCandidateWhenItIsStillCarried() {
        Item dagger = heldItem("itmRt4Xw7Kq", "A rusty dagger.");
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(dagger));

        Item resolved = subcase.playerDesignatesChosenCandidate(1, List.of("itmRt4Xw7Kq", "itmKey8Pp3a"), SELF);

        assertThat(resolved).isEqualTo(dagger);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsTargetNoLongerAvailableAndSignalsWhenTheChosenCandidateIsNoLongerCarried() {
        // The token was offered earlier, but the item is no longer in the player's keeping (dropped / lost).
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(heldItem("itmLm2bQ9Zx", "A brass lantern.")));

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("itmRt4Xw7Kq"), SELF))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("itmRt4Xw7Kq");
    }

    @Test
    void throwsAPreconditionFaultWhenNothingWasOffered() {
        // With the conversation dispatcher the console resumes only an armed conversation, so an empty offer
        // reaching the subcase is a wiring fault, not a player outcome (see the scene concrete's test).
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of(), SELF))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(presenter, itemOps);   // gated before any read, and never presented
    }

    @Test
    void presentsNoSuchOptionAndSignalsWhenThePickIsOutOfRange() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(5, List.of("itmRt4Xw7Kq", "itmKey8Pp3a"), SELF))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchOption(5);
        verifyNoInteractions(itemOps);   // gated before any read
    }

    @Test
    void propagatesAMalformedChosenTokenWithoutPresenting() {
        // The token comes from our own remembered offer, so a malformed one is an internal fault — it reaches
        // the parent's catch-all, never a presented outcome. Gated (the concrete's requireWellFormedToken
        // reconstitutes the ItemId) before provisioning, so no read happens.
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("not-an-item-id"), SELF))
                .isInstanceOf(InvalidDomainObjectError.class);

        verifyNoInteractions(presenter, itemOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private static Item heldItem(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.HeldBy(SELF))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Item>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
