package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.explore.ExamineInputPort;
import com.github.gameclean.infrastructure.terminal.SelectionKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code examine} disambiguation as a {@link Conversation}: a bare number while an examine offer is armed
 * resumes by describing the chosen candidate. Wired in the composition root and pulled by {@code ConsoleSession}
 * as one of the {@code List<Conversation>} the container collects.
 */
public class ExamineConversation extends AbstractSelectionConversation {

    public ExamineConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public SelectionKind kind() {
        return SelectionKind.EXAMINE;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(ExamineInputPort.class).playerExaminesChosenCandidate(ordinal, offer);
    }
}
