package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.inventory.TakeInputPort;
import com.github.gameclean.infrastructure.terminal.SelectionKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code take} disambiguation as a {@link Conversation}: a bare number while a take offer is armed resumes
 * by taking the chosen candidate. The second selection conversation — its existence is what forces the dispatcher
 * to route by {@link SelectionKind} (without it, a number after {@code take rusty} would wrongly resume
 * {@code examine}). Wired in the composition root, collected by {@code ConsoleSession} as a {@code Conversation}.
 */
public class TakeConversation extends AbstractSelectionConversation {

    public TakeConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public SelectionKind kind() {
        return SelectionKind.TAKE;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(TakeInputPort.class).playerTakesChosenCandidate(ordinal, offer);
    }
}
