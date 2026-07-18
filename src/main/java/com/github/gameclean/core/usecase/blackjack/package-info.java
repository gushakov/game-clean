/**
 * The <b>amusement</b> summary goal (Cockburn): the player passes time at a mini-game. Its first user goal is
 * <b>playing a hand of blackjack</b> ({@link com.github.gameclean.core.usecase.blackjack.PlayBlackjackInputPort})
 * against a dealer <em>persona</em> — there is no dealer NPC and no secondary actor: the authored scene text
 * supplies the fiction, and the system executes the dealer's fixed rule inside the player's own interactions.
 *
 * <p><b>This use case is the project's showcase of a conversation's <em>substance</em>.</b> Where
 * {@code examine}/{@code take}/{@code drop} demonstrated the modal machinery around a thin generic dialogue
 * (disambiguation), blackjack is a genuinely multi-interaction goal with an arc: sit down (deal), any number of
 * hit-me rounds, stand, settlement — each interaction changing which affordances make sense next. The use case
 * owns every semantic step and outcome; the delivery mechanism owns only input routing (which line continues
 * the table talk) and the parked state between lines.
 *
 * <p><b>The between-interaction state is an ephemeral value, not an aggregate.</b> The
 * {@code BlackjackRound} position (see {@code core.model.blackjack}'s package doc) is handed <em>in</em> to
 * each interaction as a parameter by the driving adapter (dependency rejection) and handed <em>out</em> through
 * the presenter, which arms the terminal's affordance buffer with the new position as it renders — the same
 * one-way loop the disambiguation offer already rides, carrying substance instead of correlation tokens.
 * Nothing is persisted: with no stakes the domain owes nobody memory of a half-played hand, so abandonment
 * (any non-blackjack command) forfeits it by design — the dealer sweeps the cards.
 *
 * <p><b>The dealer's playout resolves synchronously</b> — the combat precedent: once the player stands, the
 * hand's outcome is exactly what they need to choose their next action, so it is an outcome stripe of this
 * interaction, never an event to resolve later. It is also, deliberately, <em>pure</em>: the deal captured the
 * shuffled deck (capture-at-offer), so the playout consumes no entropy at all.
 *
 * <p>{@code playerExaminesGame} is a Cockburn <b>anytime extension</b> (a {@code *a.} step: "at any time, the
 * player may ask where the game stands") — repeatable, stateless, re-presenting the table and re-arming the
 * same position.
 */
package com.github.gameclean.core.usecase.blackjack;
