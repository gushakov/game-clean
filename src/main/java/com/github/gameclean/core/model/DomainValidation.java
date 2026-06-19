package com.github.gameclean.core.model;

/**
 * Validation primitives for the always-valid model's construction gate. A constructor or static factory
 * that is handed state it cannot accept signals it by throwing {@link InvalidDomainObjectError} through
 * these helpers, so every value object and aggregate rejects invalid construction uniformly — one named
 * failure of the validity gate rather than a raw {@code NullPointerException} each caller would have to
 * translate.
 *
 * <p>Use the qualified call ({@code DomainValidation.requireNonNull(...)}) so it reads distinctly from
 * {@link java.util.Objects#requireNonNull(Object, String)}, which the model keeps for behaviour-method
 * argument guards (a null there is a caller bug, not invalid domain input, so it stays a plain
 * {@code NullPointerException}).
 */
public final class DomainValidation {

    private DomainValidation() {
    }

    /**
     * Returns {@code value} if non-null, otherwise throws {@link InvalidDomainObjectError}. Drop-in for
     * {@link java.util.Objects#requireNonNull(Object, String)} inside a constructor or static factory.
     *
     * @param value   the field value being validated
     * @param message describes which field was null
     * @return {@code value}, guaranteed non-null
     */
    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new InvalidDomainObjectError(message);
        }
        return value;
    }
}
