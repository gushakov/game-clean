package com.github.gameclean.core.usecase.parley;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.npc.Offer;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link TalkToNpcUseCase} in isolation — every collaborator is mocked. Pure
 * orchestration: the {@code orient} subcase resolves player + scene, the {@code select} subcase resolves the NPC
 * (both mocked; their own outcomes are covered by their tests), the {@link Dice} picks the line.
 *
 * <p>It presents once on every path — the NPC's answer, a refusal, a vendor's wares, or nothing-to-say for a
 * resolved NPC; {@code presentError} on an unexpected failure — and a subcase's {@link SubcaseAlreadyPresented}
 * is swallowed. The stripe order is pinned (hostility outranks eloquence and commerce; wares outrank the plain
 * line), and the stripes without a line consume no entropy.
 * Read-only: there is no transaction port and no persistence port to verify untouched — the use case cannot
 * write by construction.
 */
@ExtendWith(MockitoExtension.class)
class TalkToNpcUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");
    private static final List<String> LINES = List.of("Mind where they lead.", "The number changes.");
    // Deliberately not in name order: the wares must come through in authored order, unsorted.
    private static final List<Offer> WARES = List.of(
            new Offer("room", "A room for the night.", 2, "itm6"),
            new Offer("ale", "A mug of ale.", 1, "itm7"));

    @Mock
    private TalkToNpcPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    @Mock
    private Dice dice;

    @InjectMocks
    private TalkToNpcUseCase useCase;

    // --- the NPC answers ---------------------------------------------------------------------------

    @Test
    void theNpcDesignatedByDescriptionAnswersWithTheLineTheDicePicks() {
        orientedAtScn1();
        Npc wanderer = npc("npc1", LINES);
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(wanderer);
        when(dice.pick(LINES)).thenReturn("The number changes.");

        useCase.playerTalksToTarget("hooded");

        verify(presenter).presentNpcSays(wanderer, "The number changes.");
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void theChosenCandidateAnswersWithTheLineTheDicePicks() {
        orientedAtScn1();
        Npc wanderer = npc("npc1", LINES);
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("npc0", "npc1"), HERE))
                .thenReturn(wanderer);
        when(dice.pick(LINES)).thenReturn("Mind where they lead.");

        useCase.playerTalksToChosenCandidate(2, List.of("npc0", "npc1"));

        verify(presenter).presentNpcSays(wanderer, "Mind where they lead.");
        verifyNoMoreInteractions(presenter);
    }

    // --- the silent stripes ------------------------------------------------------------------------

    @Test
    void aHostileNpcRefusesToTalkEvenThoughItHasLines() {
        orientedAtScn1();
        Npc provoked = npc("npc1", LINES).provoked();
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenReturn(provoked);

        useCase.playerTalksToTarget("hooded");

        // Hostility outranks eloquence: the refusal, and no line is even picked.
        verify(presenter).presentNpcRefusesToTalk(provoked);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(dice);
    }

    @Test
    void aPeacefulNpcWithoutLinesHasNothingToSay() {
        orientedAtScn1();
        Npc watchman = npc("npc1", List.of());
        when(selectTargetSubcase.playerDesignatesChosenCandidate(1, List.of("npc1"), HERE))
                .thenReturn(watchman);

        useCase.playerTalksToChosenCandidate(1, List.of("npc1"));

        verify(presenter).presentNpcHasNothingToSay(watchman);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(dice);
    }

    // --- the vendor shows its wares ----------------------------------------------------------------

    @Test
    void aPeacefulVendorWithLinesSaysTheLineTheDicePicksAndShowsItsWaresInAuthoredOrder() {
        orientedAtScn1();
        Npc keeper = vendor("npc2", LINES, WARES);
        when(selectTargetSubcase.playerDesignatesTarget("innkeeper", HERE)).thenReturn(keeper);
        when(dice.pick(LINES)).thenReturn("Mind where they lead.");

        useCase.playerTalksToTarget("innkeeper");

        List<StandingOffer> shown = assertWaresShown(keeper, Optional.of("Mind where they lead."));
        assertThat(shown).extracting(StandingOffer::getOffer).containsExactlyElementsOf(WARES);
        assertThat(shown).extracting(StandingOffer::getVendor).containsOnly(keeper);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void aPeacefulVendorWithoutLinesShowsItsWaresWithNoSayingAndRollsNoDice() {
        orientedAtScn1();
        Npc keeper = vendor("npc2", List.of(), WARES);
        when(selectTargetSubcase.playerDesignatesTarget("innkeeper", HERE)).thenReturn(keeper);

        useCase.playerTalksToTarget("innkeeper");

        List<StandingOffer> shown = assertWaresShown(keeper, Optional.empty());
        assertThat(shown).extracting(StandingOffer::getOffer).containsExactlyElementsOf(WARES);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(dice);
    }

    @Test
    void aHostileVendorRefusesToTalkAndShowsNothing() {
        orientedAtScn1();
        Npc provoked = vendor("npc2", LINES, WARES).provoked();
        when(selectTargetSubcase.playerDesignatesTarget("innkeeper", HERE)).thenReturn(provoked);

        useCase.playerTalksToTarget("innkeeper");

        // Hostility outranks commerce: the refusal alone, no wares and no line picked.
        verify(presenter).presentNpcRefusesToTalk(provoked);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(dice);
    }

    @Test
    void theChosenCandidateShowsItsWaresToo() {
        orientedAtScn1();
        Npc keeper = vendor("npc2", LINES, WARES);
        when(selectTargetSubcase.playerDesignatesChosenCandidate(2, List.of("npc1", "npc2"), HERE))
                .thenReturn(keeper);
        when(dice.pick(LINES)).thenReturn("The number changes.");

        useCase.playerTalksToChosenCandidate(2, List.of("npc1", "npc2"));

        List<StandingOffer> shown = assertWaresShown(keeper, Optional.of("The number changes."));
        assertThat(shown).extracting(StandingOffer::getOffer).containsExactlyElementsOf(WARES);
        verifyNoMoreInteractions(presenter);
    }

    // --- subcase already presented -----------------------------------------------------------------

    @Test
    void whenOrientAlreadyPresentedTheUseCasePresentsNothingMore() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTalksToTarget("hooded");

        verifyNoInteractions(presenter, selectTargetSubcase, dice);
    }

    @Test
    void whenSelectAlreadyPresentedByDescriptionTheUseCasePresentsNothingMore() {
        orientedAtScn1();
        when(selectTargetSubcase.playerDesignatesTarget("ghost", HERE)).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTalksToTarget("ghost");

        verifyNoInteractions(presenter, dice);
    }

    @Test
    void whenSelectAlreadyPresentedByChoiceTheUseCasePresentsNothingMore() {
        orientedAtScn1();
        when(selectTargetSubcase.playerDesignatesChosenCandidate(9, List.of("npc1"), HERE))
                .thenThrow(new SubcaseAlreadyPresented());

        useCase.playerTalksToChosenCandidate(9, List.of("npc1"));

        verifyNoInteractions(presenter, dice);
    }

    // --- catch-all ---------------------------------------------------------------------------------

    @Test
    void anUnexpectedFailureIsPresentedAsAnError() {
        orientedAtScn1();
        IllegalStateException boom = new IllegalStateException("boom");
        when(selectTargetSubcase.playerDesignatesTarget("hooded", HERE)).thenThrow(boom);

        useCase.playerTalksToTarget("hooded");

        verify(presenter).presentError(boom);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void anUnexpectedFailureOnTheChosenCandidateIsPresentedAsAnError() {
        IllegalStateException boom = new IllegalStateException("boom");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerTalksToChosenCandidate(1, List.of("npc1"));

        verify(presenter).presentError(boom);
        verifyNoMoreInteractions(presenter);
    }

    // --- fixtures ----------------------------------------------------------------------------------

    private void orientedAtScn1() {
        Player player = Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(HERE)
                .hitPoints(new HitPoints(30, 30))
                .version(1)
                .build();
        Scene scene = Scene.builder()
                .id(HERE)
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scene));
    }

    /**
     * Verifies the one wares presentation, pins the vendor and the saying, and returns the wares as presented so
     * the caller can assert their order and pairing.
     */
    @SuppressWarnings("unchecked")
    private List<StandingOffer> assertWaresShown(Npc expectedVendor, Optional<String> expectedSaying) {
        ArgumentCaptor<List<StandingOffer>> wares = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentNpcShowsWares(eq(expectedVendor), eq(expectedSaying), wares.capture());
        return wares.getValue();
    }

    private static Npc npc(String id, List<String> sayings) {
        return vendor(id, sayings, List.of());
    }

    private static Npc vendor(String id, List<String> sayings, List<Offer> offers) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(HERE)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(20))
                .hostile(false)
                .sayings(sayings)
                .offers(offers)
                .version(1)
                .build();
    }
}
