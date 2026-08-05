package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.infrastructure.terminal.render.Console;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Session-lifetime lifecycle latch for the terminal: the single fact "the game is over — end this session". The
 * end-of-game twin of {@link AffordanceContext}, and deliberately <b>not</b> folded into it. The two rhyme
 * structurally — a driven presenter writes, the driving loop reads, through a dumb shared holder rather than by
 * calling one another — but they are semantic opposites and would break the affordance mechanism's invariants:
 *
 * <ul>
 *   <li><b>Thread contract is opposite.</b> {@link AffordanceContext} is thread-confined to the single input
 *       thread (its javadoc says so — "the asynchronous tickers never touch it"). Game-over is latched by a
 *       <em>background</em> actor: a provoked NPC's counterstrike runs on the NPC-activity ticker thread while
 *       the console is parked in {@code readLine} on its own thread. That is the very "a background actor arms
 *       it" case {@code AffordanceContext} documents it does not support, so the flag here is {@code volatile}
 *       and lives in a separate holder with its own cross-thread contract.</li>
 *   <li><b>An affordance is reversible and plural; game-over is terminal and singular.</b> An affordance means
 *       "what you can do next" — armed, then continued, abandoned, or completion-disarmed. Game-over means
 *       "there is nothing left to do": it is never cleared, and a dead player doing "something else" must not
 *       un-die, so the affordance's abandon-clears-it lifecycle would be exactly backwards.</li>
 *   <li><b>No continuation, so no routing.</b> Every {@link AffordanceKind} must have a {@code Conversation}
 *       handler (a wiring-time completeness check enforces it) so an armed offer can be resumed. Game-over
 *       resumes nothing — it carries no kind, no payload, no handler.</li>
 * </ul>
 *
 * <p>It owns the single-sourced end-of-game step: {@link #endGame()} announces the game-over line above the live
 * prompt <em>and</em> raises the latch, in one call, so every death cause (an NPC kill today; a trap or poison
 * later) ends the game with the same message and can never render the line yet forget to latch. The console
 * presents nothing itself; it only reads {@link #endRequested()} and, when set, discards the keystroke that woke
 * it and leaves the game the same way {@code bye} does (banking the session's elapsed time via {@code
 * SuspendGame}, then breaking its loop).
 */
public class GameLifecycle {

    private static final String GAME_OVER = "Game is over. Press Enter to quit.";

    private final Console console;
    private volatile boolean ended;

    public GameLifecycle(Console console) {
        this.console = console;
    }

    /**
     * The single-sourced end-of-game step, invoked by a death outcome's presenter as the terminal act of
     * rendering that outcome: narrate the game-over line above the live prompt, then latch the session to end.
     * Written from a background (ticker) thread while a {@code readLine} is in flight, so it uses the
     * asynchronous {@link Console#printAbove} path — exactly as the slain narration that immediately precedes it.
     */
    public void endGame() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.bold()).append(GAME_OVER);
        console.printAbove(sb);
        this.ended = true;
    }

    /**
     * @return whether a death outcome has latched the game over, so the console loop should end the session on
     *         the next keystroke that wakes it
     */
    public boolean endRequested() {
        return ended;
    }
}
