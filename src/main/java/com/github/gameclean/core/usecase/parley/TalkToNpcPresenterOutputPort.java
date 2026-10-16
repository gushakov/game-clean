package com.github.gameclean.core.usecase.parley;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code TalkToNpc}, co-located with its use case. It carries the three
 * talk outcomes plus the inherited catch-all; the orient not-founds and the disambiguation outcomes belong to
 * the {@code orient} and {@code select} subcase ports, which the concrete terminal presenter implements
 * alongside this one as flat interfaces (composition, not a presenter base class) — exactly as {@code hit}
 * composes them.
 *
 * <p>The three stripes are decided <em>in the use case</em> on domain facts and handed here already chosen —
 * the presenter never inspects the NPC's stance or saying list to decide what to render:
 * {@link #presentNpcSays(Npc, String)} carries the line the NPC's own dice pick chose;
 * {@link #presentNpcRefusesToTalk(Npc)} is the hostile NPC shutting the conversation;
 * {@link #presentNpcHasNothingToSay(Npc)} is a peaceful NPC authored without sayings.
 *
 * <p>Domain objects pass straight through ({@link Npc}) — no response DTOs.
 */
public interface TalkToNpcPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the NPC answers with {@code saying}, one of its authored lines picked uniformly. */
    void presentNpcSays(Npc npc, String saying);

    /**
     * The NPC is hostile (the player struck it, and the combat stance persists), so it will not talk. The use
     * case checks this first: a hostile NPC refuses even when it has lines — hostility outranks eloquence.
     */
    void presentNpcRefusesToTalk(Npc npc);

    /** A peaceful NPC with no authored sayings: it acknowledges the player and offers nothing. */
    void presentNpcHasNothingToSay(Npc npc);
}
