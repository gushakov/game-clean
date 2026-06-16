package com.github.gameclean.core.port;

/**
 * Control-flow signal thrown by a subcase that has <em>already presented</em> its outcome (any error
 * branch, or — for a purely terminal subcase — a success). It tells the caller (the parent use case) that
 * the single presentation has happened and the interaction must not continue. Not a real error: the parent
 * swallows it in a dedicated {@code catch} as a no-op, keeping "presentation is terminal" intact across the
 * parent/subcase pair. Unchecked, so it rides the same path as the catch-all checkpoint and the parent's
 * more specific {@code catch} for it sits ahead of {@code catch (Exception)}.
 */
public class SubcaseAlreadyPresented extends RuntimeException {

}
