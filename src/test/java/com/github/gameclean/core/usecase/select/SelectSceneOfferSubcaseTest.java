package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.npc.Offer;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link SelectSceneOfferSubcase} in isolation — the presenter and the NPC port are
 * mocked and the subcase is exercised directly. The ware twin of {@link SelectSceneNpcSubcaseTest}, plus the
 * one thing that test does not have: candidates are <em>projected</em> from the NPCs the port returns, so the
 * provisioning itself is under test — a hostile NPC's wares are not offered, an NPC that sells nothing adds
 * nothing, and the re-confirmation of a menu pick sees the vendor behind the composite token (a vendor turned
 * hostile, or gone, takes her wares out of the candidate set with her).
 */
@ExtendWith(MockitoExtension.class)
class SelectSceneOfferSubcaseTest {

    private static final SceneId HERE = SceneId.of("scn5");

    @Mock
    private SelectTargetPresenterOutputPort<StandingOffer> presenter;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;

    @InjectMocks
    private SelectSceneOfferSubcase subcase;

    // --- playerDesignatesTarget (designation by description) ----------------------------------------

    @Test
    void provisions_the_wares_of_the_peaceful_vendors_in_the_scene() {
        Npc keeper = npc("npc2", "A stout innkeeper.", false, roomOffer());
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(keeper));

        StandingOffer resolved = subcase.playerDesignatesTarget("room", HERE);

        assertThat(resolved).isEqualTo(new StandingOffer(keeper, roomOffer()));
        assertThat(resolved.getVendor()).isSameAs(keeper);
        verifyNoInteractions(presenter);
    }

    @Test
    void a_hostile_vendors_wares_are_never_offered() {
        // Buying from an attacker is no option: the stance removes her whole catalogue — the same reading
        // talk applies to her words.
        Npc turnedKeeper = npc("npc2", "A stout innkeeper.", true, roomOffer());
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(turnedKeeper));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("room", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("room");
    }

    @Test
    void a_vendor_without_offers_adds_no_candidate() {
        // The wanderer sells nothing; a fragment that names its description buys nothing.
        Npc wanderer = npc("npc1", "A hooded wanderer.", false);
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(wanderer));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("wanderer", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("wanderer");
    }

    @Test
    void presents_ambiguous_target_unordered_and_signals_when_a_fragment_matches_many() {
        Npc keeper = npc("npc2", "A stout innkeeper.", false, roomOffer(), stewOffer());
        Npc peddler = npc("npc7", "A peddler of rooms.", false, new Offer("rooms", "Two rooms, side by side.", 4, "itm6"));
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(keeper, peddler));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("room", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        ArgumentCaptor<List<StandingOffer>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("room"), captor.capture());
        // The stew does not match "room" — only the two room-named wares are candidates (and note the
        // peddler's "rooms" does: the substring rule is the model's, pinned here through the subcase).
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new StandingOffer(keeper, roomOffer()),
                new StandingOffer(peddler, new Offer("rooms", "Two rooms, side by side.", 4, "itm6")));
    }

    // --- playerDesignatesChosenCandidate (designation by choosing from the offer) -------------------

    @Test
    void returns_the_chosen_ware_when_its_vendor_is_still_here() {
        Npc keeper = npc("npc2", "A stout innkeeper.", false, roomOffer());
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(keeper));

        StandingOffer resolved = subcase.playerDesignatesChosenCandidate(
                1, List.of("npc2:room", "npc2:stew"), HERE);

        assertThat(resolved).isEqualTo(new StandingOffer(keeper, roomOffer()));
        verifyNoInteractions(presenter);
    }

    @Test
    void presents_target_no_longer_available_when_the_vendor_turned_hostile_since_the_menu() {
        // The re-confirmation provisions live: a hostile vendor is out of the candidate set, so the remembered
        // token resolves to nothing — the ware is simply no longer on offer here.
        Npc provokedKeeper = npc("npc2", "A stout innkeeper.", true, roomOffer());
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(provokedKeeper));

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("npc2:room"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("npc2:room");
    }

    @Test
    void presents_target_no_longer_available_when_the_vendor_has_left_the_scene() {
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of());

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("npc2:room"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("npc2:room");
    }

    @Test
    void propagates_a_malformed_composite_token_without_presenting() {
        // The token comes from our own remembered menu, so a malformed one is an internal fault reaching the
        // parent's catch-all. Gated (the candidate type's own shape gate) before any read.
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("npc2"), HERE))
                .isInstanceOf(InvalidDomainObjectError.class);

        verifyNoInteractions(presenter, npcOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private static Offer roomOffer() {
        return new Offer("room", "A room for the night.", 2, "itm6");
    }

    private static Offer stewOffer() {
        return new Offer("stew", "A bowl of yesterday's stew.", 1, "itm1");
    }

    private static Npc npc(String id, String shortDescription, boolean hostile, Offer... offers) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(HERE)
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the NPC.")
                .moveChance(new Chance(0, 1))
                .attackChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(25))
                .hostile(hostile)
                .offers(List.of(offers))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<StandingOffer>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
