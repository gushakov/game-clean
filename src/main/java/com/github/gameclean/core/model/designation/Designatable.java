package com.github.gameclean.core.model.designation;

/**
 * A thing the player can <em>designate</em> in conversation — the candidate capability of the {@code select}
 * subcase's target-disambiguation dialogue. The dialogue skeleton needs exactly two facts about a candidate,
 * and this interface is those two facts and nothing more: whether a free-text fragment designates it
 * (designation by description), and whether a remembered id token identifies it (designation by choosing from
 * an earlier offer, re-confirmed against live state).
 *
 * <p><b>Why a model-level interface, in its own neutral package.</b> The select subcase went generic in its
 * candidate type when the second candidate kind arrived (combat targets, issues #66/#67 — the trigger
 * design-notes §4 deferred the {@code <C, T>} step for), and the capability the skeleton relies on is a fact
 * about the <em>model</em>, told Tell-Don't-Ask style by each candidate about itself. It cannot live in
 * {@code core/usecase/select/} — the model never depends on the use-case layer — so it gets a neutral model
 * package, exactly as {@code SpawnRule} moved to {@code core/model/spawn/} when NPC spawning became its
 * second consumer.
 *
 * <p><b>The id token is a raw string on purpose.</b> Tokens are the flatten of a candidate's id
 * ({@code getId().getValue()}) that the driven presenter produces as it arms an offer, so the terminal's
 * conversational buffer stays model-free ("primitives inward"). {@link #hasIdToken} is accordingly a
 * <em>pure comparison</em> against that flatten — no reconstitution, no validity gate. The token's
 * <em>shape</em> gate (a malformed remembered token is an internal fault, never a presented outcome) is the
 * select concrete's eager {@code requireWellFormedToken} hook, which fires before any candidate exists to ask.
 */
public interface Designatable {

    /**
     * Tells whether this candidate is designated by the given free-text fragment — the player's typed target,
     * resolved in ubiquitous language, never by identifier. Each implementor owns its matching rule (the item
     * precedent: a case-insensitive substring over the short description the player sees listed).
     *
     * @param fragment the player's fragment (non-blank, already trimmed by the driving adapter; a null is a
     *                 caller programming error and stays a plain {@link NullPointerException})
     * @return whether the fragment designates this candidate
     */
    boolean matches(String fragment);

    /**
     * Tells whether the given raw id token identifies this candidate — the re-confirmation step of a
     * by-choice designation, comparing the remembered token against this candidate's own id flatten.
     *
     * @param idToken the raw id token (well-formedness already gated by the select concrete; a null is a
     *                caller programming error and stays a plain {@link NullPointerException})
     * @return whether the token is this candidate's id
     */
    boolean hasIdToken(String idToken);
}
