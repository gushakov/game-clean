package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;

import java.util.Locale;

/**
 * The mini-games a scene can offer — authored world vocabulary, referenced by {@link Scene#offers(MiniGame)}
 * and authored per scene under the seed's {@code mini-games:} key. A closed set on purpose: a new mini-game is
 * a deliberate code change (a new use case, a new conversation), never just a new string in a YAML file.
 *
 * <p>This is a scene attribute, not a rules engine: the enum names <em>what is on offer here</em>; the game's
 * rules live in their own generic subdomain (e.g. {@code core.model.blackjack}), which this package never
 * references — the scene knows the name of the game, not how to play it.
 */
public enum MiniGame {

    BLACKJACK;

    /**
     * Resolves an authored (or stored) mini-game name, case-insensitively — the construction gate for this
     * vocabulary. An unknown name is invalid authored input (or a corrupt stored row, wrapped by the reading
     * adapter), signalled with the named construction failure like any other value object.
     *
     * @param name the authored name, e.g. {@code "blackjack"}
     * @return the matching mini-game
     */
    public static MiniGame fromAuthoredName(String name) {
        if (name == null || name.strip().isEmpty()) {
            throw new InvalidDomainObjectError("mini-game name must not be blank");
        }
        try {
            return valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainObjectError("unknown mini-game '%s'".formatted(name.strip()));
        }
    }
}
