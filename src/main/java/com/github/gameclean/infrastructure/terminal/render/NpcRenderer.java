package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.usecase.npc.PerceivedNpcMovement;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renders NPC-facing output — the <em>asynchronous</em> narration of NPC movements the player can witness, and
 * the synchronous outcomes of a {@code hit}: the strike confirmations and the NPC-flavored disambiguation
 * dialogue. The NPC counterpart of {@link ItemRenderer} and {@link CurrentSceneRenderer}: a shared,
 * domain-aware collaborator (it knows {@link Npc} and {@link PerceivedNpcMovement}) over the domain-agnostic
 * {@link Console}.
 *
 * <p>Movements are produced by a background actor (the NPC-activity ticker) while the player may be at the
 * {@code game> } prompt, so each is written with {@link Console#printAbove} (above the live prompt), exactly as
 * the day-phase announcer writes. The {@code hit} outcomes, by contrast, are the synchronous response to a
 * player command, so they use {@link Console#write}/{@link Console#printError}, like the item outcomes. Scene
 * <em>listing</em> — the "Also here:" block when a player looks — stays in {@link CurrentSceneRenderer}.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class NpcRenderer {

    private static final AttributedStyle MOVEMENT = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle STRUCK = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle SLAIN = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();

    Console console;

    /** Narrates each witnessed movement on its own line, above the live prompt, in the order given. */
    public void renderMovements(List<PerceivedNpcMovement> movements) {
        for (PerceivedNpcMovement movement : movements) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(MOVEMENT).append(phrase(movement));
            console.printAbove(sb);
        }
    }

    /** Confirmation of a landed, non-lethal strike: whom, for how much, and the NPC's remaining health. */
    public void renderNpcStruck(Npc npc, int damage) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STRUCK).append("You strike %s for %d damage. (%d/%d)".formatted(
                bareName(npc.getShortDescription()), damage,
                npc.getHitPoints().getCurrent(), npc.getHitPoints().getMax()));
        console.write(sb);
    }

    /** Confirmation of a lethal strike: the NPC is down. */
    public void renderNpcSlain(Npc npc) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(SLAIN).append("You strike down %s.".formatted(bareName(npc.getShortDescription())));
        console.write(sb);
    }

    /**
     * The strike lost a concurrent race — the NPC was there when chosen but moved or changed under the player's
     * blow, so the versioned write was rejected. The write-side twin of {@link #renderTargetNoLongerAvailable};
     * reads the same to the player.
     */
    public void renderNpcGotAway(NpcId npcId) {
        console.printError("It slips away before your blow lands.");
    }

    /** Nothing present is designated by the fragment the player typed — the NPC-flavored no-such-target. */
    public void renderNoSuchTarget(String target) {
        console.printError("You see no one like '%s' here.".formatted(target));
    }

    /**
     * The disambiguation menu: a prompt naming the ambiguous fragment, then the candidates numbered 1..N in the
     * order given (the presenter's stable order), then how to choose. The NPC twin of
     * {@link ItemRenderer#renderAmbiguousTarget}.
     */
    public void renderAmbiguousTarget(String target, List<Npc> orderedCandidates) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("Which '%s' do you mean?".formatted(target))
                .style(AttributedStyle.DEFAULT);
        int number = 1;
        for (Npc candidate : orderedCandidates) {
            sb.append(System.lineSeparator())
                    .append("  %d. ".formatted(number++))
                    .append(candidate.getShortDescription());
        }
        sb.append(System.lineSeparator()).append("Type the number to choose.");
        console.write(sb);
    }

    /** A chosen NPC no longer in the scene (wandered off or slain since it was offered) — the read-side miss. */
    public void renderTargetNoLongerAvailable(String idToken) {
        console.printError("They are no longer here.");
    }

    /** The player picked a number outside the offered menu; the menu still stands. */
    public void renderNoSuchOption(int ordinal) {
        console.printError("There is no option %d. Type one of the numbers shown.".formatted(ordinal));
    }

    private static String phrase(PerceivedNpcMovement movement) {
        String who = bareName(movement.getNpc().getShortDescription());
        return switch (movement.getKind()) {
            // detail is the exit name the NPC left by.
            case DEPARTED -> "%s leaves to the %s.".formatted(who, movement.getDetail());
            // detail is the name of the scene the NPC came from.
            case ARRIVED -> "%s arrives from the %s.".formatted(who, movement.getDetail());
        };
    }

    /**
     * Short descriptions are authored as standalone sentences ("A hooded wanderer."); strip the trailing period
     * so the name reads cleanly embedded in a sentence ("A hooded wanderer leaves ...", "You strike A hooded
     * wanderer ...").
     */
    private static String bareName(String shortDescription) {
        String who = shortDescription.strip();
        return who.endsWith(".") ? who.substring(0, who.length() - 1) : who;
    }
}
