package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.usecase.inventory.TakeFromInputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * The {@code take … from …} disambiguation as a {@link Conversation}: a bare number while a take-from offer is
 * armed resumes by taking the chosen content out of its container. Its own kind, distinct from
 * {@link TakeConversation}'s, because the two resume different interactions over different provenances — a
 * number after {@code take ring from corpse} must re-validate against the containers here, not the ground.
 * Only item tokens ride the affordance: the container fragment is a filter the first interaction already
 * applied, so nothing about it needs remembering.
 */
public class TakeFromConversation extends AbstractSelectionConversation {

    public TakeFromConversation(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public AffordanceKind kind() {
        return AffordanceKind.TAKE_FROM;
    }

    @Override
    protected void resumeWith(int ordinal, List<String> offer) {
        // Fresh prototype per resume (like ConsoleSession's other pulls); the use case presents every outcome.
        applicationContext.getBean(TakeFromInputPort.class).playerTakesChosenContent(ordinal, offer);
    }
}
