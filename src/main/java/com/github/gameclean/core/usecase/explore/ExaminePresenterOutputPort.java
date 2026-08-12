package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code Examine}, co-located with its use case. It carries only
 * {@code examine}'s own terminal outcomes — the reveal of a plain item and the reveal of a container with
 * its contents — plus the inherited catch-all.
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

    /**
     * Happy path for a <em>container</em>: its full description together with what lies inside — the
     * contents fetched by the use case as a query against the contained items' {@code Location.Inside}
     * references. An empty list is the same stripe (the container reveals itself as empty; phrasing is the
     * renderer's). It is a distinct stripe from {@link #presentItemDescription} because the <em>use case</em>
     * decides on the domain fact ({@code Item#isContainer()}) which outcome occurred — a presenter that had
     * to inspect the item to tell "empty container" from "not a container" would be deciding, not rendering.
     */
    void presentContainerContents(Item container, List<Item> contents);
}
