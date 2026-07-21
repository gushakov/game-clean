package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.combat.HitInputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code hit} disambiguation as a {@link Conversation}: a bare number while a hit offer is armed resumes by
 * striking the chosen candidate. Mirrors {@link TakeConversation}; combat is the first non-item selection, so
 * this is the first conversation resuming a use case outside {@code inventory}/{@code explore}. Wired in the
 * composition root, collected by {@code ConsoleSession} as a {@code Conversation}.
 */
public class HitConversation extends AbstractSelectionConversation {

    public HitConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public AffordanceKind kind() {
        return AffordanceKind.HIT;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(HitInputPort.class).playerHitsChosenCandidate(ordinal, offer);
    }
}
