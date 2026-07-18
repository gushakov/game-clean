package com.github.gameclean.core.model.blackjack;

/**
 * The four French suits. Carries no blackjack semantics — suits never affect a hand's value — and no display
 * form: how a suit renders (a glyph, a letter, a word) is a presentation concern owned by the renderer.
 */
public enum Suit {
    CLUBS, DIAMONDS, HEARTS, SPADES
}
