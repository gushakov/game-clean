package com.github.gameclean.infrastructure.terminal;

/**
 * English grammar helpers for the terminal presenters — ordinals ({@code 6 -> "6th"}) and quantities
 * ({@code 1 -> "1 hour"}, {@code 2 -> "2 hours"}). Pure, stateless functions of a number (and a noun).
 *
 * <p>This is the most generic of the three presentation-composition layers (design-notes §7): grammar that
 * knows neither the terminal ({@link Console}) nor the domain ({@link CalendarRenderer}). Turning a count
 * into English is <em>locale/grammar</em>, unambiguously a rendering concern — never on the model, which
 * stays language-neutral (a {@code GameDate} holds 0-based integers, not "6th" or "days"). And because it is
 * a pure function of an {@code int}, its home is a small <em>static helper composed in</em>, not a method on
 * a domain object and not an abstract hook on a presenter base class — composition over inheritance, the same
 * stance §6 takes against presenter base classes.
 */
public final class English {

    private English() {
    }

    /**
     * The English ordinal for a positive integer: {@code 1 -> "1st"}, {@code 2 -> "2nd"}, {@code 3 -> "3rd"},
     * {@code 4 -> "4th"}, with the 11th–13th exception ({@code 11 -> "11th"}, {@code 12 -> "12th"},
     * {@code 13 -> "13th"}, {@code 21 -> "21st"}).
     */
    public static String ordinal(int n) {
        int lastTwo = Math.abs(n) % 100;
        if (lastTwo >= 11 && lastTwo <= 13) {
            return n + "th";
        }
        String suffix = switch (Math.abs(n) % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /**
     * A counted noun with the right number: {@code quantity(1, "hour") -> "1 hour"},
     * {@code quantity(2, "hour") -> "2 hours"}. Naive pluralization (append {@code s}) — enough for the
     * regular units the calendar uses ({@code hour}, {@code second}, {@code day}); an irregular plural would
     * take the two-argument overload when one is first needed.
     */
    public static String quantity(long count, String singular) {
        return count + " " + (count == 1 ? singular : singular + "s");
    }
}
