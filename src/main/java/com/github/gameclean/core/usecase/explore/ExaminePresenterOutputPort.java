package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Examine}, co-located with its use case. It carries only
 * {@code examine}'s own terminal outcome — the item's full description — plus the inherited catch-all.
 *
 * <p><b>The disambiguation outcomes are not here.</b> They moved to the {@code select} subcase's
 * {@link com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort} when the dialogue was
 * factored out: presenting "nothing matches" / the ambiguity menu / "no longer here" / "nothing offered" /
 * "no such option" is the <em>subcase's</em> business, shared with {@code take}/{@code drop}. {@code examine}
 * keeps only what is peculiar to examining — describing the resolved item. The concrete terminal presenter
 * implements this port, the select port and the orient port as three flat interfaces (composition, not a
 * presenter base class).
 *
 * <p>Domain objects pass straight through ({@link Item}) — no response DTOs.
 */
public interface ExaminePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the matched item's full description (reached by either designation — fragment or choice). */
    void presentItemDescription(Item item);
}
