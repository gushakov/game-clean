package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link ConstructWorldUseCase} in isolation — every output port is mocked and
 * the use case is exercised directly through its input port (no Spring, no database). The transaction
 * port is stubbed to run its action inline and to fire after-commit callbacks immediately, so the
 * post-commit presentation is observable; the persistence-failure case instead lets the action's
 * error propagate, exactly as the real adapter would.
 */
@ExtendWith(MockitoExtension.class)
class ConstructWorldUseCaseTest {

    @Mock
    private ConstructWorldPresenterOutputPort presenter;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private ConstructWorldUseCase useCase;

    @Test
    void seedsAnEmptyWorldAndPresentsSuccessAfterCommit() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        runTransactionsAndFireAfterCommit();

        useCase.constructWorld(twoConnectedScenes());

        assertScenesSavedInOrder("scn1", "scn2");
        assertSuccessfulConstructionPresentedFor("scn1", "scn2");
    }

    @Test
    void skipsSeedingAndPresentsAlreadyConstructedWhenTheWorldIsNotEmpty() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        runTransactionsAndFireAfterCommit();

        useCase.constructWorld(twoConnectedScenes());

        verify(sceneOps, never()).saveScene(any());
        verify(presenter).presentWorldAlreadyConstructed();
        verify(presenter, never()).presentSuccessfulWorldConstruction(any());
    }

    @Test
    void rejectsAMalformedEntryBeforeOpeningAnyTransaction() {
        // id without the 'scn' prefix — SceneId construction fails the intra-aggregate validity gate
        List<SceneEntry> entries = List.of(
                new SceneEntry("bogus", "Old Gate", "A gate.", "A weathered stone archway.", List.of()));

        useCase.constructWorld(entries);

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNoTransactionAndNoWrites();
    }

    @Test
    void rejectsAnExitWhoseTargetIsNotADefinedScene() {
        // scn1's only exit points at scn9, which the seed never defines — an inter-aggregate failure
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(new ExitEntry("east", "scn9"))));

        useCase.constructWorld(entries);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<SceneId, List<Exit>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentErrorWhenExitTargetUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys(new SceneId("scn1"));
        assertThat(captor.getValue().get(new SceneId("scn1")))
                .extracting(Exit::getName).containsExactly("east");
        verifyNoTransactionAndNoWrites();
    }

    @Test
    void presentsAnErrorWhenASceneCannotBeSaved() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(sceneOps).saveScene(any());
        runTransactionsPropagatingErrors();

        useCase.constructWorld(twoConnectedScenes());

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSuccessfulWorldConstruction(any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static List<SceneEntry> twoConnectedScenes() {
        return List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.",
                        "The gate's iron hinges have long since rusted shut.",
                        List.of(new ExitEntry("east", "scn2"))),
                new SceneEntry("scn2", "Courtyard", "A grass-cracked courtyard.",
                        "Weeds push between the flagstones of a drilling yard.",
                        List.of(new ExitEntry("west", "scn1"))));
    }

    // --- transaction-port stubs -----------------------------------------------------------------

    /** Run the transactional action inline and fire after-commit callbacks immediately. */
    private void runTransactionsAndFireAfterCommit() {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(txOps).doAfterCommit(any(Runnable.class));
    }

    /** Run the transactional action inline, letting any error it throws propagate (as the real port does). */
    private void runTransactionsPropagatingErrors() {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    // --- assertion helpers ----------------------------------------------------------------------

    private void assertScenesSavedInOrder(String... expectedIds) {
        ArgumentCaptor<Scene> captor = ArgumentCaptor.forClass(Scene.class);
        verify(sceneOps, times(expectedIds.length)).saveScene(captor.capture());
        assertThat(captor.getAllValues()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedIds);
    }

    private void assertSuccessfulConstructionPresentedFor(String... expectedIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentSuccessfulWorldConstruction(captor.capture());
        assertThat(captor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedIds);
    }

    private void verifyNoTransactionAndNoWrites() {
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(sceneOps, never()).saveScene(any());
    }
}
