/**
 * Driven (output) adapters that render use-case outcomes to the JLine console — one thin presenter per use
 * case, each implementing its co-located presenter port.
 *
 * <p>They are {@code new}ed by the composition root, not {@code @Component}s (design-notes §6), and carry no
 * rendering vocabulary of their own: each <em>composes</em> the shared {@code render} collaborators
 * ({@link com.github.gameclean.infrastructure.terminal.render.Console} and the scene / calendar renderers).
 * Kept in a package apart from {@code render} on purpose — a presenter is an <em>adapter</em>, while the
 * renderers and {@code Console} are shared <em>resources</em>; §7 turns on not confusing the two.
 */
package com.github.gameclean.infrastructure.terminal.presenter;
