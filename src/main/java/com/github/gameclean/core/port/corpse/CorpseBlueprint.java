package com.github.gameclean.core.port.corpse;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.item.ItemTemplate;
import lombok.Value;

import java.util.List;

/**
 * The always-valid minting recipe for one authored corpse: the corpse's own {@link ItemTemplate} plus the
 * authored loot that may lie on the body — each loot entry pairing a contained item's template with the odds
 * it appears, rolled once per slaying (the containment-fill semantics, replayed at death time). The return
 * contract of {@link CorpseBlueprintSourceOperationsOutputPort}.
 *
 * <p><b>Valid-out, unlike the seed carriers.</b> The seed source returns invalid-capable {@code *Entry}
 * carriers because authored world data's invalidity is a <em>presented</em> initialization outcome. This
 * carrier holds real, already-valid model types instead, on the calendar port's provenance rationale: the
 * initialization gate re-validates every corpse ref on every boot, so by the time a death asks for the
 * blueprint the authoring is known-good — a blueprint that fails to assemble signals drift between the
 * persisted world and an edited seed, an integrity fault the adapter raises as
 * {@link CorpseBlueprintSourceOperationsError}, never a presented outcome.
 *
 * <p>The templates carried here hold <b>no ground-spawn rule</b> — a blueprint is a minting recipe for one
 * death, not a world-population plan; a loot template's own authored {@code spawn:} (it may also spawn on the
 * ground elsewhere) is deliberately not this carrier's business.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class CorpseBlueprint {

    /** The corpse item's template — a container, anchored unless authored portable, never ground-spawning. */
    ItemTemplate corpseTemplate;

    /** The authored loot that may lie on the body — empty for a corpse that carries nothing. */
    List<Loot> loot;

    /**
     * One authored loot declaration: the contained template and the odds it appears inside the corpse —
     * the corpse's authored fact, handed to the contained template as a value at mint time (exactly as
     * containment fill hands odds to {@code ItemTemplate.spawnInside}).
     */
    @Value
    public static class Loot {
        ItemTemplate template;
        Chance chance;
    }
}
