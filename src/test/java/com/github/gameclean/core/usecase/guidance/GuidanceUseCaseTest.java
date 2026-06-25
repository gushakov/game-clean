package com.github.gameclean.core.usecase.guidance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Interaction tests for {@link GuidanceUseCase} in isolation — the only output port, the presenter, is mocked.
 * The use case has no domain state to stub: it hands the raw input straight to the presenter. Exactly one
 * {@code present*} is reached on every path.
 */
@ExtendWith(MockitoExtension.class)
class GuidanceUseCaseTest {

    @Mock
    private GuidancePresenterOutputPort presenter;

    @InjectMocks
    private GuidanceUseCase useCase;

    @Test
    void presentsTheUnrecognizedInputForGuidance() {
        useCase.playerIssuesUnrecognizedCommand("flibber");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(presenter).presentUnrecognizedCommand(captor.capture());
        assertThat(captor.getValue()).isEqualTo("flibber");
        verify(presenter, never()).presentError(any());
    }

    @Test
    void routesAnUnexpectedFailureToTheCatchAll() {
        // The only way to reach the catch-all on this I/O-free path is a presenter that itself throws.
        RuntimeException boom = new RuntimeException("rendering blew up");
        doThrow(boom).when(presenter).presentUnrecognizedCommand("flibber");

        useCase.playerIssuesUnrecognizedCommand("flibber");

        verify(presenter).presentError(boom);
    }

    @Test
    void greetsThePlayerWithTheWelcome() {
        useCase.systemGreetsPlayer();

        verify(presenter).presentWelcome();
        verify(presenter, never()).presentError(any());
    }

    @Test
    void routesAGreetingFailureToTheCatchAll() {
        RuntimeException boom = new RuntimeException("rendering blew up");
        doThrow(boom).when(presenter).presentWelcome();

        useCase.systemGreetsPlayer();

        verify(presenter).presentError(boom);
    }
}
