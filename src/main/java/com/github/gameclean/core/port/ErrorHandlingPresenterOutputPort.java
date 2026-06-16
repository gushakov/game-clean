package com.github.gameclean.core.port;

/**
 * Base presenter (driven) output port: the single generic fallback every use-case presenter must
 * provide. A use case's outermost {@code catch (Exception e)} checkpoint routes any unhandled
 * failure here, so no exception ever escapes back to the caller — control flows one way,
 * Controller → Use Case → Presenter.
 *
 * <p>Each use case declares its own {@code {Name}PresenterOutputPort} that <em>extends</em> this
 * one and adds fine-grained {@code present + Outcome + Qualifier} methods for the outcomes it
 * handles explicitly. {@link #presentError(Exception)} is the humble catch-all for everything else.
 *
 * <p>This is the one presenter-related interface that lives in {@code core/port/} rather than beside
 * a use case, precisely because it is shared by every use case's presenter port.
 */
public interface ErrorHandlingPresenterOutputPort {

    /**
     * Catch-all for any outcome a use case did not present through a more specific method —
     * invoked from the outermost checkpoint. Implementations may inspect the exception for
     * <em>formatting</em> purposes only; they make no business or flow decisions.
     */
    void presentError(Exception e);
}
