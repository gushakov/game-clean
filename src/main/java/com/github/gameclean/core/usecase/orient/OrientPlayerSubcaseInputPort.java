package com.github.gameclean.core.usecase.orient;

/**
 * Input port of the {@link OrientPlayerSubcase orient} subcase — the shared opening reused by every
 * interaction grounded in the player's current location ({@code look}, {@code move}, and later
 * {@code examine}, {@code take}, ...). The parent use case depends on this interface, not the
 * implementation, and is handed a fresh instance wired with the parent's own presenter.
 */
public interface OrientPlayerSubcaseInputPort {

    /**
     * Resolves the ambient player and the scene they currently stand in. On success returns the oriented
     * {@link OrientPlayerResult} (player + scene) for the caller to act on; on a missing player or a
     * dangling current-scene reference it presents the outcome itself and throws
     * {@link com.github.gameclean.core.port.SubcaseAlreadyPresented} to signal the caller to stop.
     *
     * @return the player and their current scene, when both resolve
     */
    OrientPlayerResult playerGetsBearings();
}
