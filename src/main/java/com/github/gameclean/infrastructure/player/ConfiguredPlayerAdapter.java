package com.github.gameclean.infrastructure.player;

import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter implementing {@link PlayerOperationsOutputPort} by reading the single
 * configured player id ({@code game.player.id}) from {@link GameConfigurationProperties}.
 *
 * <p>This is the single-player, no-authentication answer to "who is the acting player": there is
 * exactly one player, named in configuration, and every console interaction is theirs. The id is
 * returned as a primitive — the use case owns {@code PlayerId} construction — so an invalid configured
 * id is rejected at the use case's validity gate, not here.
 *
 * <p>Evolution: once NPCs and the clock act asynchronously, "who is acting" becomes per-interaction
 * rather than global, and this adapter gives way to one backed by the driving path's context (e.g. the
 * actor whose turn the outbox relay is processing). The core port does not change — that is the point
 * of resolving the actor through a port rather than a parameter.
 */
@Component
@RequiredArgsConstructor
public class ConfiguredPlayerAdapter implements PlayerOperationsOutputPort {

    private final GameConfigurationProperties properties;

    @Override
    public String currentPlayerId() {
        return properties.getPlayer().getId();
    }
}
