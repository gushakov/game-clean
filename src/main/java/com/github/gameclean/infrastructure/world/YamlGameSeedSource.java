package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.port.corpse.CorpseBlueprint;
import com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsError;
import com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ContainsEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter over the authored YAML seed, implementing <b>both</b> ports the seed file serves (the
 * {@code YamlCalendarSource} two-ports-one-file precedent):
 *
 * <p><b>{@link GameSeedSourceOperationsOutputPort}</b> — the boot-time pull: it resolves the configured seed
 * location and starting scene, opens the resource, and hands the stream to {@link GameSeedYamlReader} to
 * assemble the {@link GameSeed}. This is where all access to the YAML parsing machinery lives — confined to
 * the infrastructure ring, behind the port the use case pulls through.
 *
 * <p>The reader stays a separate collaborator (it owns the parse + authoring-syntax normalization); this
 * adapter owns the <em>sourcing</em> — config resolution, resource I/O, and translating every technical
 * failure into the unchecked {@link GameSeedSourceOperationsError} the port contract declares. That covers
 * both the {@link IOException} of an unreadable resource <em>and</em> the runtime failures the reader raises
 * on a malformed document (a SnakeYAML parse error, a non-numeric chance fraction, a structurally-wrong
 * node). The catch can be this broad safely because the reader touches no domain model — it returns only
 * {@code *Entry} carriers — so nothing it throws is a domain exception that would be wrongly swallowed. A
 * missing or broken seed therefore reaches the use case's outermost checkpoint and is presented, rather than
 * failing startup from inside this adapter — uniform with how a persistence fault is handled. On this port it
 * does <b>not</b> touch the domain model: it returns the possibly-invalid {@code *Entry} carriers, leaving
 * the validity gate to the use case.
 *
 * <p><b>{@link CorpseBlueprintSourceOperationsOutputPort}</b> — the death-time pull (#93): given a slain
 * NPC's authored corpse ref, it re-reads the seed and assembles the <em>valid-out</em> {@link CorpseBlueprint}
 * — real {@link ItemTemplate}s and {@link Chance}s, not carriers. Constructing model objects in a driven
 * adapter is sanctioned here by provenance (the {@code YamlCalendarSource} rationale): the initialization
 * gate re-validates every corpse ref on every boot, so a blueprint that fails to assemble — an unresolved
 * ref, a ref to a non-container or a ground-spawning template, a construction-gate rejection — signals drift
 * between the persisted world and an edited seed, an integrity fault raised as
 * {@link CorpseBlueprintSourceOperationsError}, never a presented outcome. The blueprint's templates carry
 * <b>no ground-spawn rule</b> (a blueprint is a minting recipe for one death); the corpse's {@code anchored}
 * fact resolves by the same kind-sensitive polarity the gate applies (a container is anchored unless authored
 * {@code portable: true}). The seed is re-read per pull — deaths are rare, the file is small, and the adapter
 * stays stateless; the seed is assumed unedited after boot.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class YamlGameSeedSource
        implements GameSeedSourceOperationsOutputPort, CorpseBlueprintSourceOperationsOutputPort {

    GameSeedYamlReader reader;
    GameConfigurationProperties properties;

    @Override
    public GameSeed loadGameSeed() {
        Resource seed = properties.getWorld().getSeedLocation();
        String startingSceneId = properties.getPlayer().getStartingSceneId();
        int playerMaxHitPoints = properties.getPlayer().getMaxHitPoints();
        log.info("[GameSeed] Loading the authored seed from {} with starting scene {}", seed, startingSceneId);
        try (InputStream in = seed.getInputStream()) {
            return reader.read(in, startingSceneId, playerMaxHitPoints);
        } catch (IOException | RuntimeException e) {
            throw new GameSeedSourceOperationsError(
                    "could not read or parse the game seed from %s".formatted(seed), e);
        }
    }

    @Override
    public CorpseBlueprint loadCorpseBlueprint(String corpseRef) {
        Resource seed = properties.getWorld().getSeedLocation();
        log.debug("[GameSeed] Loading the corpse blueprint '{}' from {}", corpseRef, seed);
        try (InputStream in = seed.getInputStream()) {
            GameSeed gameSeed = reader.read(in, properties.getPlayer().getStartingSceneId(),
                    properties.getPlayer().getMaxHitPoints());
            return assembleBlueprint(corpseRef, gameSeed.getItems(), seed);
        } catch (CorpseBlueprintSourceOperationsError e) {
            throw e;   // already the port's currency — never double-wrapped
        } catch (IOException | RuntimeException e) {
            // An unreadable/malformed seed, or a construction-gate rejection while building the templates
            // (InvalidDomainObjectError is a RuntimeException) — all integrity faults of this port at death
            // time, since the initialization gate validated the same authoring at boot.
            throw new CorpseBlueprintSourceOperationsError(
                    "could not read or assemble the corpse blueprint '%s' from %s".formatted(corpseRef, seed), e);
        }
    }

    /**
     * Assembles the valid-out blueprint from the parsed carriers: resolve the corpse entry, hold it to the
     * corpse-template rules the gate enforces (a container, no ground-spawn rule), then pair each authored
     * {@code contains} entry's template with its odds. Each unmet rule throws the port error with a message
     * naming what drifted.
     */
    private static CorpseBlueprint assembleBlueprint(String corpseRef, List<ItemEntry> items, Resource seed) {
        ItemEntry corpseEntry = findAuthoredItem(items, corpseRef).orElseThrow(
                () -> new CorpseBlueprintSourceOperationsError(
                        "corpse ref '%s' resolves to no authored item in %s".formatted(corpseRef, seed)));
        if (!corpseEntry.isContainer() || corpseEntry.getSpawn() != null) {
            throw new CorpseBlueprintSourceOperationsError(
                    ("corpse ref '%s' must resolve to an authored container without a ground-spawn rule "
                            + "(in %s)").formatted(corpseRef, seed));
        }
        List<ContainsEntry> contains = corpseEntry.getContains() == null ? List.of() : corpseEntry.getContains();
        List<CorpseBlueprint.Loot> loot = new ArrayList<>(contains.size());
        for (ContainsEntry containsEntry : contains) {
            ItemEntry lootEntry = findAuthoredItem(items, containsEntry.getItem()).orElseThrow(
                    () -> new CorpseBlueprintSourceOperationsError(
                            "corpse '%s' declares unresolved loot ref '%s' in %s"
                                    .formatted(corpseRef, containsEntry.getItem(), seed)));
            loot.add(new CorpseBlueprint.Loot(toTemplate(lootEntry),
                    new Chance(containsEntry.getChanceNumerator(), containsEntry.getChanceDenominator())));
        }
        return new CorpseBlueprint(toTemplate(corpseEntry), loot);
    }

    private static Optional<ItemEntry> findAuthoredItem(List<ItemEntry> items, String authoredId) {
        return items.stream().filter(entry -> authoredId.equals(entry.getId())).findFirst();
    }

    /**
     * Builds a blueprint template from its authored entry: the gate's kind-sensitive portability polarity
     * (a plain item is portable, a container anchored, unless {@code portable:} says otherwise), and no
     * ground-spawn rule — a blueprint is a minting recipe for one death, so a loot entry's own authored
     * {@code spawn:} (it may also spawn on the ground elsewhere) is deliberately dropped here.
     */
    private static ItemTemplate toTemplate(ItemEntry entry) {
        boolean anchored = entry.getPortable() != null ? !entry.getPortable() : entry.isContainer();
        return new ItemTemplate(entry.getShortDescription(), entry.getFullDescription(),
                entry.isContainer(), anchored, null);
    }
}
