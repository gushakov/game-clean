package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to take a specific thing <em>out of a container</em>, both designated by
 * description — produced by {@code take <item> from <container>} and {@code get <item> from <container>}.
 * The parser splits the line remainder on its last standalone {@code from}; each half is a possibly
 * multi-word fragment handed inward as a primitive, and the {@code TakeFrom} use case does the matching.
 * The disambiguation follow-up is a bare {@link SelectCommand}, routed by the armed selection kind.
 */
@Value
public class TakeFromCommand implements Command {
    String itemTarget;
    String containerTarget;
}
