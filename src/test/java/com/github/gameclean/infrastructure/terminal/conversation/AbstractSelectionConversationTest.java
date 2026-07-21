package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.SelectionAffordance;
import com.github.gameclean.infrastructure.terminal.command.SelectCommand;
import com.github.gameclean.infrastructure.terminal.command.UnknownCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the Template-Method seam in {@link AbstractSelectionConversation}: the base casts the answer
 * command to a {@link SelectCommand}, extracts its ordinal, and delegates to {@code resumeWith} with the
 * affordance's tokens. The {@code getBean}+call in each real concrete is exercised by integration tests (like
 * every prototype-pull site), so this pins only the shared cast — no {@code ApplicationContext} needed. The
 * inherited default {@code continuedBy} (a bare number continues a selection) is pinned here too.
 */
class AbstractSelectionConversationTest {

    @Test
    void resume_casts_the_command_to_an_ordinal_and_delegates_with_the_offered_tokens() {
        RecordingConversation conversation = new RecordingConversation();
        List<String> offer = List.of("itmA", "itmB", "itmC");

        conversation.resume(new SelectCommand(3), new SelectionAffordance(AffordanceKind.EXAMINE, offer));

        assertThat(conversation.lastOrdinal).isEqualTo(3);
        assertThat(conversation.lastOffer).isEqualTo(offer);
    }

    @Test
    void a_selection_conversation_is_continued_by_a_bare_number_and_nothing_else() {
        RecordingConversation conversation = new RecordingConversation();

        assertThat(conversation.continuedBy(new SelectCommand(2))).isTrue();
        assertThat(conversation.continuedBy(new UnknownCommand("north"))).isFalse();
    }

    /** A minimal concrete recording the hook's arguments; the ApplicationContext is unused by the base. */
    private static final class RecordingConversation extends AbstractSelectionConversation {

        private int lastOrdinal;
        private List<String> lastOffer;

        RecordingConversation() {
            super(null);
        }

        @Override
        public AffordanceKind kind() {
            return AffordanceKind.EXAMINE;
        }

        @Override
        protected void resumeWith(int ordinal, List<String> offer) {
            this.lastOrdinal = ordinal;
            this.lastOffer = offer;
        }
    }
}
