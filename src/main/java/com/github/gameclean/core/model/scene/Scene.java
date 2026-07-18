package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A location in the game world — the aggregate root of the scene aggregate.
 *
 * <p>Immutable and always-valid: a {@code Scene} cannot be constructed with a null id, a blank
 * name or description, or two exits sharing the same name (an intra-aggregate invariant). Exit
 * <em>targets</em> reference other scene aggregates by {@link SceneId}; whether those targets
 * resolve to real scenes is an <em>inter-aggregate</em> world-consistency rule, checked by the
 * world-construction use case rather than on the entity.
 *
 * <p>Equality is by identity (id) only — two scenes are the same scene when their ids match.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Scene {

    @EqualsAndHashCode.Include
    SceneId id;
    String name;
    String shortDescription;
    String fullDescription;
    List<Exit> exits;
    Set<MiniGame> miniGames;

    @Builder
    public Scene(SceneId id, String name, String shortDescription, String fullDescription, List<Exit> exits,
                 Set<MiniGame> miniGames) {
        this.id = DomainValidation.requireNonNull(id, "scene id must not be null");
        this.name = requireNonBlank(name, "scene name");
        this.shortDescription = requireNonBlank(shortDescription, "scene short description");
        this.fullDescription = requireNonBlank(fullDescription, "scene full description");
        this.exits = List.copyOf(DomainValidation.requireNonNull(exits, "scene exits must not be null"));
        requireUniqueExitNames(this.exits);
        // Most scenes offer no games — authored absence arrives as null as naturally as an empty set, so
        // both normalize to "none" rather than making every construction site spell out Set.of().
        this.miniGames = miniGames == null ? Set.of() : Set.copyOf(miniGames);
    }

    /**
     * Finds the exit leaving this scene by name — the lookup {@code move} uses to resolve the direction
     * the player chose. Matching is case-insensitive and ignores surrounding whitespace, so
     * {@code "East"}, {@code "east"} and {@code "  east  "} all select the same exit. Exit names are
     * unique within a scene (an enforced invariant), so at most one exit can match.
     *
     * @return the matching exit, or empty if no exit of that name leaves this scene
     */
    public Optional<Exit> exitNamed(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.strip();
        return exits.stream()
                .filter(exit -> exit.getName().equalsIgnoreCase(wanted))
                .findFirst();
    }

    /**
     * Whether this scene offers the given mini-game — the grounding check a play-a-game interaction makes
     * before dealing anything (Tell-Don't-Ask: the use case asks the scene, never reads the set out to
     * filter). A side-effect-free function over authored state.
     *
     * @param game the mini-game asked about (must not be null — a caller programming error)
     * @return {@code true} when this scene's authored offering includes the game
     */
    public boolean offers(MiniGame game) {
        Objects.requireNonNull(game, "mini-game must not be null");
        return miniGames.contains(game);
    }

    /**
     * The exits of this scene whose target resolves to none of the given known scene ids — this scene's
     * contribution to the inter-aggregate world-consistency check the initialization use case performs.
     * Expressed in identities ({@link SceneId}), so the scene never reaches into other scene aggregates: the
     * use case asks each scene about its own exits rather than reading them out and filtering. A
     * side-effect-free function returning the dangling exits — empty when every target resolves.
     *
     * @param knownSceneIds the identities of the scenes that actually exist in the world being built
     * @return this scene's exits with an unresolved target, in declaration order
     */
    public List<Exit> exitsWithTargetNotIn(Set<SceneId> knownSceneIds) {
        Objects.requireNonNull(knownSceneIds, "known scene ids must not be null");
        return exits.stream()
                .filter(exit -> !knownSceneIds.contains(exit.getTarget()))
                .toList();
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }

    private static void requireUniqueExitNames(List<Exit> exits) {
        Set<String> names = new HashSet<>();
        for (Exit exit : exits) {
            if (!names.add(exit.getName())) {
                throw new InvalidDomainObjectError(
                        "duplicate exit name '%s' within scene".formatted(exit.getName()));
            }
        }
    }
}
