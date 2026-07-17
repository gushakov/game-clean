package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
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
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link SelectSceneNpcSubcase} in isolation — the presenter and the NPC port are mocked
 * and the subcase is exercised directly. The NPC twin of {@code SelectSceneItemSubcaseTest}: the same
 * guarded-prologue exits (a clean single resolution <em>returns</em> the NPC and presents nothing; every
 * disambiguation outcome <em>presents</em> and throws {@link SubcaseAlreadyPresented}), proving the generic
 * {@code select} skeleton works verbatim for the first non-item candidate type.
 *
 * <p>Because the subcase owns its candidate fetch, the NPC port is stubbed here (not in the parent's test), and
 * the ambiguous branch passes the matches <em>unordered</em> — ordering the menu is the presenter's job.
 */
@ExtendWith(MockitoExtension.class)
class SelectSceneNpcSubcaseTest {

    private static final SceneId HERE = new SceneId("scn1");

    @Mock
    private SelectTargetPresenterOutputPort<Npc> presenter;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;

    @InjectMocks
    private SelectSceneNpcSubcase subcase;

    // --- playerDesignatesTarget (designation by description) ----------------------------------------

    @Test
    void returnsTheSingleNpcAFragmentDesignates() {
        Npc wanderer = npc("npcRt4Xw7Kq", "A hooded wanderer.");
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(wanderer, npc("npcLm2bQ9Zx", "A blind lamplighter.")));

        Npc resolved = subcase.playerDesignatesTarget("wanderer", HERE);

        assertThat(resolved).isEqualTo(wanderer);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsNoSuchTargetAndSignalsWhenAFragmentDesignatesNothing() {
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(npc("npcRt4Xw7Kq", "A hooded wanderer.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("goblin", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("goblin");
    }

    @Test
    void presentsAmbiguousTargetUnorderedAndSignalsWhenAFragmentMatchesMany() {
        Npc hoodedWanderer = npc("npcRt4Xw7Kq", "A hooded wanderer.");
        Npc hoodedFigure = npc("npcKey8Pp3a", "A hooded figure.");
        when(npcOps.findNpcsInScene(HERE))
                .thenReturn(List.of(hoodedWanderer, hoodedFigure, npc("npcLm2bQ9Zx", "A blind lamplighter.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("hooded", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        ArgumentCaptor<List<Npc>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("hooded"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(hoodedWanderer, hoodedFigure);
    }

    // --- playerDesignatesChosenCandidate (designation by choosing from the offer) -------------------

    @Test
    void returnsTheChosenCandidateWhenItIsStillPresent() {
        Npc wanderer = npc("npcRt4Xw7Kq", "A hooded wanderer.");
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(wanderer));

        Npc resolved = subcase.playerDesignatesChosenCandidate(1, List.of("npcRt4Xw7Kq", "npcKey8Pp3a"), HERE);

        assertThat(resolved).isEqualTo(wanderer);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsTargetNoLongerAvailableAndSignalsWhenTheChosenCandidateHasLeft() {
        // The token was offered earlier, but the NPC is no longer in the scene (wandered off / slain).
        when(npcOps.findNpcsInScene(HERE)).thenReturn(List.of(npc("npcLm2bQ9Zx", "A blind lamplighter.")));

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("npcRt4Xw7Kq"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("npcRt4Xw7Kq");
    }

    @Test
    void throwsAPreconditionFaultWhenNothingWasOffered() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of(), HERE))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(presenter, npcOps);   // gated before any read, and never presented
    }

    @Test
    void presentsNoSuchOptionAndSignalsWhenThePickIsOutOfRange() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(5, List.of("npcRt4Xw7Kq", "npcKey8Pp3a"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchOption(5);
        verifyNoInteractions(npcOps);   // gated before any read
    }

    @Test
    void propagatesAMalformedChosenTokenWithoutPresenting() {
        // The token comes from our own remembered offer, so a malformed one is an internal fault reaching the
        // parent's catch-all. Gated (the concrete's requireWellFormedToken reconstitutes the NpcId) before any read.
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("not-an-npc-id"), HERE))
                .isInstanceOf(InvalidDomainObjectError.class);

        verifyNoInteractions(presenter, npcOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private static Npc npc(String id, String shortDescription) {
        return Npc.builder()
                .id(new NpcId(id))
                .currentScene(HERE)
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the NPC.")
                .moveChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(10))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Npc>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
