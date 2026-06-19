/**
 * The terminal rendering toolkit — the three composition layers of design-notes §7, from most generic to
 * domain-aware:
 * <ul>
 *   <li>{@link com.github.gameclean.infrastructure.terminal.render.English} — pure grammar (ordinals,
 *       plurals); knows neither the terminal nor the domain.</li>
 *   <li>{@link com.github.gameclean.infrastructure.terminal.render.Console} — a domain-agnostic
 *       styled-writer facade over the shared JLine terminal; a <em>resource</em>, not an adapter.</li>
 *   <li>{@link com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer} and
 *       {@link com.github.gameclean.infrastructure.terminal.render.CalendarRenderer} — domain-aware
 *       renderers that compose the two layers beneath them.</li>
 * </ul>
 *
 * <p>The driven {@code presenter} adapters compose these collaborators; the core model never knows any of
 * them. {@code English} is a candidate to graduate to a shared infrastructure text util the day a non-terminal
 * site needs it.
 */
package com.github.gameclean.infrastructure.terminal.render;
