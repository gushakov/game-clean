package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.trade.BuyFromNpcInputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code buy} disambiguation as a {@link Conversation}: a bare number while a ware offer is armed resumes
 * by buying the chosen candidate. Mirrors {@link TalkConversation} — the same scene-sourced disambiguation, a
 * different completion interaction. Wired in the composition root, collected by {@code ConsoleSession} as a
 * {@code Conversation}.
 */
public class BuyConversation extends AbstractSelectionConversation {

    public BuyConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public AffordanceKind kind() {
        return AffordanceKind.BUY;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(BuyFromNpcInputPort.class).playerBuysChosenCandidate(ordinal, offer);
    }
}
