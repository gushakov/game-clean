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
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsError;
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter over the authored YAML seed, implementing <b>all three</b> ports the seed file serves (the
 * {@code YamlCalendarSource} two-ports-one-file precedent, extended to a third):
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
 * <b>no ground-spawn rule</b> (a blueprint is a minting recipe for one death) but <b>every instance-copied
 * fact</b> the entry holds — the {@code opens} handle and the {@code currency} flag included, since the minted
 * instance takes them from the template; the corpse's {@code anchored} fact resolves by the same kind-sensitive
 * polarity the gate applies (a container is anchored unless authored {@code portable: true}).
 *
 * <p><b>{@link WareTemplateSourceOperationsOutputPort}</b> — the purchase-time pull (#119): given a chosen
 * offer's authored ware ref, it re-reads the same memoized seed and assembles the <em>valid-out</em>
 * {@link ItemTemplate} the ware is minted from — every instance-copied fact ({@code opens} and
 * {@code currency} included), no ground-spawn rule (a bought ware appears in the buyer's hands, never on the
 * ground its spawn rule might place it). The same provenance sanctions the model construction here: the
 * initialization gate re-validates every offer's item ref on every boot (resolves to an authored, non-container
 * item), so a ref that fails to assemble — unresolved, a container, an anchored item — signals drift between
 * the persisted world and an edited seed, an integrity fault raised as {@link WareTemplateSourceOperationsError},
 * never a presented outcome.
 *
 * <p><b>One parse per run (#95).</b> The seed is parsed once and memoized — lazily, in a {@code volatile}
 * field shared by all three ports — so the death-time blueprint and the purchase-time template are assembled
 * from the very parse the boot gate validated: what was "the seed is assumed unedited after boot" is now an
 * invariant. Lazy, not eager, on purpose: the first parse still happens inside {@link #loadGameSeed()} under
 * the boot use case's checkpoint, so a broken seed stays a <em>presented</em> outcome — the calendar's
 * fail-fast boot-fault deviation is deliberately not taken here. The memoizer sits <em>below</em> each port's
 * error translation (the accessor throws raw, each method wraps into its own currency), and a failed parse is
 * never cached — the next pull retries. {@code volatile} is safe publication for a future consumer pulling
 * from another thread (today all pulls ride the request thread); no locking, because a racy duplicate parse of
 * the immutable carriers is benign.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class YamlGameSeedSource
        implements GameSeedSourceOperationsOutputPort, CorpseBlueprintSourceOperationsOutputPort,
        WareTemplateSourceOperationsOutputPort {

    GameSeedYamlReader reader;
    GameConfigurationProperties properties;

    /**
     * The one-parse-per-run snapshot all three ports serve from. Written at most once per successful parse (a
     * failed parse leaves it {@code null}, so the next pull retries); {@code volatile} for safe publication
     * across threads, with no locking — a racy duplicate parse of the immutable carriers is benign.
     */
    @NonFinal
    volatile GameSeed authoredSeed;

    @Override
    public GameSeed loadGameSeed() {
        Resource seed = properties.getWorld().getSeedLocation();
        log.info("[GameSeed] Loading the authored seed from {} with starting scene {}", seed,
                properties.getPlayer().getStartingSceneId());
        try {
            return authoredSeed();
        } catch (IOException | RuntimeException e) {
            throw new GameSeedSourceOperationsError(
                    "could not read or parse the game seed from %s".formatted(seed), e);
        }
    }

    @Override
    public CorpseBlueprint loadCorpseBlueprint(String corpseRef) {
        Resource seed = properties.getWorld().getSeedLocation();
        log.debug("[GameSeed] Loading the corpse blueprint '{}' from {}", corpseRef, seed);
        try {
            return assembleBlueprint(corpseRef, authoredSeed().getItems(), seed);
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

    @Override
    public ItemTemplate loadWareTemplate(String itemRef) {
        Resource seed = properties.getWorld().getSeedLocation();
        log.debug("[GameSeed] Loading the ware template '{}' from {}", itemRef, seed);
        try {
            return assembleWare(itemRef, authoredSeed().getItems(), seed);
        } catch (WareTemplateSourceOperationsError e) {
            throw e;   // already the port's currency — never double-wrapped
        } catch (IOException | RuntimeException e) {
            // An unreadable/malformed seed, or a construction-gate rejection while building the template
            // (InvalidDomainObjectError is a RuntimeException) — all integrity faults of this port at purchase
            // time, since the initialization gate validated the same authoring at boot.
            throw new WareTemplateSourceOperationsError(
                    "could not read or assemble the ware template '%s' from %s".formatted(itemRef, seed), e);
        }
    }

    /**
     * Serves the memoized snapshot, parsing it on first pull — whichever port asks first materializes it.
     * Throws raw ({@link IOException} or the reader's runtime failures): the currency translation stays in
     * the port methods above, preserving one file, two currencies.
     */
    private GameSeed authoredSeed() throws IOException {
        GameSeed cached = authoredSeed;
        if (cached != null) {
            return cached;
        }
        Resource seed = properties.getWorld().getSeedLocation();
        try (InputStream in = seed.getInputStream()) {
            GameSeed parsed = reader.read(in, properties.getPlayer().getStartingSceneId(),
                    properties.getPlayer().getMaxHitPoints());
            authoredSeed = parsed;
            return parsed;
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

    /**
     * Assembles the valid-out ware template from the parsed carriers: resolve the entry, hold it to the
     * offered-ware rules the gate enforces (not a container, not anchored — the anchored fact resolved as
     * {@link #toTemplate} resolves it), then build the template. Each unmet rule throws the port error with a
     * message naming what drifted.
     */
    private static ItemTemplate assembleWare(String itemRef, List<ItemEntry> items, Resource seed) {
        ItemEntry wareEntry = findAuthoredItem(items, itemRef).orElseThrow(
                () -> new WareTemplateSourceOperationsError(
                        "ware ref '%s' resolves to no authored item in %s".formatted(itemRef, seed)));
        boolean anchored = wareEntry.getPortable() != null ? !wareEntry.getPortable() : wareEntry.isContainer();
        if (wareEntry.isContainer() || anchored) {
            throw new WareTemplateSourceOperationsError(
                    ("ware ref '%s' must resolve to an authored item that is neither a container nor anchored "
                            + "(in %s)").formatted(itemRef, seed));
        }
        return toTemplate(wareEntry);
    }

    private static Optional<ItemEntry> findAuthoredItem(List<ItemEntry> items, String authoredId) {
        return items.stream().filter(entry -> authoredId.equals(entry.getId())).findFirst();
    }

    /**
     * Builds a blueprint template from its authored entry: the gate's kind-sensitive portability polarity
     * (a plain item is portable, a container anchored, unless {@code portable:} says otherwise), and no
     * ground-spawn rule — a blueprint is a minting recipe for one death, so a loot entry's own authored
     * {@code spawn:} (it may also spawn on the ground elsewhere) is deliberately dropped here.
     *
     * <p>Every <em>instance-copied</em> fact the entry carries rides onto the template, because the minted instance
     * copies them from it: the {@code opens} handle (a key minted as loot opens its lock exactly like one spawned
     * from the ground) and the {@code currency} flag (a coin minted as loot is still money). The blueprint
     * drops the spawn rule, never an instance fact.
     */
    private static ItemTemplate toTemplate(ItemEntry entry) {
        boolean anchored = entry.getPortable() != null ? !entry.getPortable() : entry.isContainer();
        return ItemTemplate.builder()
                .shortDescription(entry.getShortDescription())
                .fullDescription(entry.getFullDescription())
                .container(entry.isContainer())
                .anchored(anchored)
                .currency(entry.isCurrency())
                .opens(entry.getOpens())
                .build();
    }
}
