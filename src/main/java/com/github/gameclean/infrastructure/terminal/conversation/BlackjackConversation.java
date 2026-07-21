package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.usecase.blackjack.PlayBlackjackInputPort;
import com.github.gameclean.infrastructure.terminal.Affordance;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.EphemeralAffordance;
import com.github.gameclean.infrastructure.terminal.command.Command;
import com.github.gameclean.infrastructure.terminal.command.GameStandingCommand;
import com.github.gameclean.infrastructure.terminal.command.HitCardCommand;
import com.github.gameclean.infrastructure.terminal.command.PlayCommand;
import com.github.gameclean.infrastructure.terminal.command.StandCommand;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationContext;

/**
 * The blackjack table talk's mode object — the first conversation continued by <em>verbs</em> rather than a
 * bare number, so it implements {@link Conversation} directly (not the selection Template-Method base) and
 * overrides {@link #continuedBy} with its own grammar: {@code hit}/{@code hit me}, {@code stand}/{@code stay},
 * {@code game}/{@code table}, and {@code play} (sitting down mid-hand re-presents the table rather than
 * dealing a second hand — the helpful answer). Pure shape-matching over command types; any other line refuses,
 * which the dispatcher turns into abandonment — the forfeit, by the domain rule that a walked-away hand is
 * swept.
 *
 * <p>Its affordance is an {@link EphemeralAffordance}: the whole between-interaction state rides as the opaque
 * envelope ({@link EphemeralAffordance#getPayload()}), which this handler alone narrows back to the
 * {@link BlackjackRound} the arming presenter parked — the cast is the {@code SelectCommand} cast's twin, the
 * one place the shell's generic plumbing meets this dialogue's concrete currency. Like every conversation it pulls a <em>fresh</em>
 * prototype use case per resume and relays values inward; the use case decides and presents every outcome.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BlackjackConversation implements Conversation {

    ApplicationContext applicationContext;

    @Override
    public AffordanceKind kind() {
        return AffordanceKind.BLACKJACK;
    }

    @Override
    public boolean continuedBy(Command command) {
        return command instanceof HitCardCommand
                || command instanceof StandCommand
                || command instanceof GameStandingCommand
                || command instanceof PlayCommand;
    }

    @Override
    public void resume(Command command, Affordance affordance) {
        PlayBlackjackInputPort playBlackjackUseCase = applicationContext.getBean(PlayBlackjackInputPort.class);
        // Only an EphemeralAffordance arms blackjack, so the narrowing is total — a wrong family would be a
        // wiring fault, like the default arm below.
        BlackjackRound round = (BlackjackRound) ((EphemeralAffordance) affordance).getPayload();
        switch (command) {
            case HitCardCommand ignored -> playBlackjackUseCase.playerAsksDealerForHitCard(round);
            case StandCommand ignored -> playBlackjackUseCase.playerRequestsToStand(round);
            case GameStandingCommand ignored -> playBlackjackUseCase.playerExaminesGame(round);
            case PlayCommand ignored -> playBlackjackUseCase.playerExaminesGame(round);
            // resume() is only reached for a command continuedBy() accepted — anything else is a wiring fault.
            default -> throw new IllegalArgumentException(
                    "blackjack conversation resumed with a non-continuing command: " + command);
        }
    }
}
