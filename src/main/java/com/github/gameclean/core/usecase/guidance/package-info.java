/**
 * Summary goal: <b>guide the player toward what they can do</b>. The interactions here turn a player who is
 * unsure how to proceed back toward their available actions, without the core ever learning the concrete
 * command vocabulary that expresses those actions.
 *
 * <p>One goal so far. {@link com.github.gameclean.core.usecase.guidance.GuidanceInputPort Guidance}: when the
 * player types something the delivery mechanism cannot map to any goal, the system responds with an
 * orientation outcome. The use case decides only <em>that</em> a lost player should be guided; the
 * <em>concrete list of commands</em> is delivery-mechanism vocabulary (design-notes §9), owned entirely by
 * the presenter — so the outcome it presents is abstract ({@code presentUnrecognizedCommand}), carrying no
 * verbs.
 *
 * <p><b>A presenter-only use case is intentional, not ceremony.</b> This goal has no domain ports today: its
 * single decision is trivial. It exists because "controllers never present" is absolute and "a presenter
 * port is mandated even with no human audience" (design-notes §4) — routing even a thin, vocabulary-free
 * outcome through a use case is the cost of that invariant, and it keeps the seam ready for the day guidance
 * grows domain-aware (e.g. "you can also examine what lies here"), at which point it earns real ports.
 * A future explicit {@code help}/{@code ?} request, and the welcome greeting, would join here as sibling
 * interactions sharing the same presented "available commands" content.
 */
package com.github.gameclean.core.usecase.guidance;
