package com.github.gameclean.core.model;

/**
 * Unchecked failure of the always-valid model's construction gate: a value object or aggregate was handed
 * state it cannot legally hold — a null required field, a blank name, a malformed id, an out-of-range
 * probability, and so on. Thrown by constructors and static factories (through {@link DomainValidation}),
 * it is the single named type for "this would not be a valid domain object", replacing the raw
 * {@code NullPointerException}/{@code IllegalArgumentException} the model used to throw.
 *
 * <p>Unchecked on purpose, like {@code PersistenceOperationsError}: construction runs <em>outside</em> any
 * transaction, at the use case's validity gate, where it is caught and presented as an invalid-input
 * outcome. On a persistence read the same throw signals corrupt stored data and rides the use case's
 * outermost {@code catch} to {@code presentError} — no separate corruption type is minted (see design notes).
 *
 * <p>It guards <em>construction</em> only. A null argument to a behaviour method (a side-effect-free function
 * taking a collaborator) stays a plain {@code NullPointerException}: that is a programming error in the
 * caller, not invalid domain input.
 */
public class InvalidDomainObjectError extends RuntimeException {

    public InvalidDomainObjectError(String message) {
        super(message);
    }

    public InvalidDomainObjectError(String message, Throwable cause) {
        super(message, cause);
    }
}
