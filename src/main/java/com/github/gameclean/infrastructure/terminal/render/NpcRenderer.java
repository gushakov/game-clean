package com.github.gameclean.infrastructure.terminal.render;

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
 * Renders the <em>asynchronous</em> narration of NPC movements the player can witness — the NPC counterpart of
 * {@link CurrentSceneRenderer}, but for the movement narrator rather than the scene listing. A shared,
 * domain-aware collaborator (it knows {@link PerceivedNpcMovement}) over the domain-agnostic {@link Console}.
 *
 * <p>Because these movements are produced by a background actor (the NPC-activity ticker) while the player may
 * be at the {@code game> } prompt, each line is written with {@link Console#printAbove} (above the live prompt),
 * exactly as the day-phase announcer writes. Scene <em>listing</em> — the "Also here:" block when a player
 * looks — stays in {@link CurrentSceneRenderer}; this renderer is only the async movement narrator.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class NpcRenderer {

    private static final AttributedStyle MOVEMENT = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);

    Console console;

    /** Narrates each witnessed movement on its own line, above the live prompt, in the order given. */
    public void renderMovements(List<PerceivedNpcMovement> movements) {
        for (PerceivedNpcMovement movement : movements) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(MOVEMENT).append(phrase(movement));
            console.printAbove(sb);
        }
    }

    private static String phrase(PerceivedNpcMovement movement) {
        // Short descriptions are authored as standalone sentences ("A hooded wanderer."); strip the trailing
        // period so the name reads cleanly embedded in the movement sentence.
        String who = movement.getNpc().getShortDescription().strip();
        if (who.endsWith(".")) {
            who = who.substring(0, who.length() - 1);
        }
        return switch (movement.getKind()) {
            // detail is the exit name the NPC left by.
            case DEPARTED -> "%s leaves to the %s.".formatted(who, movement.getDetail());
            // detail is the name of the scene the NPC came from.
            case ARRIVED -> "%s arrives from the %s.".formatted(who, movement.getDetail());
        };
    }
}
