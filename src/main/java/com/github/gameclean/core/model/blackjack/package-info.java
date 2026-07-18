/**
 * The playing-cards / blackjack <em>generic subdomain</em> (Evans, ch. 14): decks, hands, and the rules of a
 * blackjack round, modelled as pure always-valid Value Objects with side-effect-free behaviour. Nothing here is
 * specific to this game's world — any RPG might want a hand of cards in a tavern — which is exactly what makes
 * it generic: a candidate off-the-shelf library, kept inside the single Maven module for now.
 *
 * <p><b>The module boundary we are not building is structurally simulated.</b> An ArchUnit rule
 * ({@code HexagonalArchitectureTest}) confines this package's dependencies to itself, {@code core.model.dice}
 * (the game's entropy capability — a real library would take an equivalent shuffle source), the {@code core.model}
 * root (the always-valid construction gate — a standalone library would substitute its own exception type), the
 * JDK, and Lombok. In particular the package cannot name {@code PlayerId} or {@code NpcId}: a
 * {@link com.github.gameclean.core.model.blackjack.BlackjackRound} is <em>anonymous</em> — who plays it, and
 * against whom, is not its concern.
 *
 * <p><b>A round is a position, not an entity.</b> {@code BlackjackRound} is deliberately a Value Object — like a
 * chess position, it is genuine accumulated state without identity. What promotes a position into an aggregate is
 * never its transition logic but a <em>custody obligation</em> (parties, a stake, something owed across
 * interruptions); with no stakes there is none, so the round lives ephemerally in the terminal's affordance
 * buffer and an abandoned hand simply evaporates (the dealer sweeps the cards). Stakes, if they ever arrive, are
 * the trigger that mints an aggregate — which would <em>wrap</em> this VO as its position, not replace it.
 */
package com.github.gameclean.core.model.blackjack;
