package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.parley.TalkToNpcInputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code talk} disambiguation as a {@link Conversation}: a bare number while a talk offer is armed resumes
 * by addressing the chosen candidate. Mirrors {@link HitConversation} — the same NPC-in-scene offer, a
 * different completion interaction. Wired in the composition root, collected by {@code ConsoleSession} as a
 * {@code Conversation}.
 */
public class TalkConversation extends AbstractSelectionConversation {

    public TalkConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public AffordanceKind kind() {
        return AffordanceKind.TALK;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(TalkToNpcInputPort.class).playerTalksToChosenCandidate(ordinal, offer);
    }
}
