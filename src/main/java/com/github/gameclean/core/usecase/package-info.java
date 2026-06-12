/**
 * Use cases — the cohesive home of the game's business processes, and the organizing artifact of
 * this codebase (Clean DDD: the Use Case <em>is</em> the home DCI's Roles/Contexts were reaching
 * for). Sub-packages name <em>Cockburn summary goals</em>, not technical layers — "screaming
 * architecture": a reader sees business intent (initialize, …) before any framework concern.
 *
 * <p>Within a summary-goal package, technical roles are read from class names, not sub-packages:
 * {@code {Name}InputPort} (the driving contract a controller calls), {@code {Name}UseCase} (the
 * framework-free implementation), and {@code {Name}PresenterOutputPort} (the co-located presenter
 * port). These three change together, so they live together.
 *
 * <p>The enterprise-level intent: a text role-playing game in which a player and autonomous actors
 * interact over a shared, persisted world — built to showcase the Clean DDD methodology in a
 * non-trivial, interaction-first domain.
 */
package com.github.gameclean.core.usecase;
