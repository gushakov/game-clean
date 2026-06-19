package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link GameDate} into the styled line the player reads when they ask the time. The single home of
 * that rendering, injected by the time presenter so the date is written one way.
 *
 * <p>It is the <em>domain-aware</em> layer of the three-layer presentation composition (design-notes §7): it
 * knows {@link GameDate} and {@link GameCalendar}, resolves the month and weekday <em>names</em> against the
 * calendar, turns the value's 0-based indices into the ordinals and counted nouns a reader expects via the
 * pure {@link English} grammar helper, and delegates the actual terminal writing to the domain-agnostic
 * {@link Console}. The model supplies <em>names and numbers</em>; this layer supplies <em>grammar, labels,
 * and 1-based counting</em>; {@code Console} supplies <em>styling</em> — the model never knows any of the
 * three. (Same split, and same composition-not-inheritance choice, that {@code CurrentSceneRenderer} uses.)
 *
 * <p>The clock label is {@code hourIndex} plus {@code secondOfHour} (there is no minutes radix, so it is not
 * forced into an {@code HH:MM} shape that would assume one): "N hours and M seconds into the day", the
 * faithful decomposition of where the day stands.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CalendarRenderer {

    private static final AttributedStyle NAME = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
    private static final AttributedStyle NUMBER = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();

    private final Console console;

    /**
     * Renders the current date: the weekday and month names, the ordinal day-of-month, the absolute year, and
     * the time-of-day as hours-and-seconds into the day. Numbers are highlighted.
     */
    public void renderCurrentTime(GameDate date, GameCalendar calendar) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(NAME).append(calendar.weekdayOf(date).getName())
                .style(AttributedStyle.DEFAULT).append(", the ")
                .style(NUMBER).append(English.ordinal(date.getDayIndex() + 1))
                .style(AttributedStyle.DEFAULT).append(" day of ")
                .style(NAME).append(calendar.monthOf(date).getName())
                .style(AttributedStyle.DEFAULT).append(", year ")
                .style(NUMBER).append(Integer.toString(date.getYear()))
                .style(AttributedStyle.DEFAULT).append(" — ")
                .style(NUMBER).append(English.quantity(date.getHourIndex(), "hour"))
                .style(AttributedStyle.DEFAULT).append(" and ")
                .style(NUMBER).append(English.quantity(date.getSecondOfHour(), "second"))
                .style(AttributedStyle.DEFAULT).append(" into the day.");
        console.write(sb);
    }
}
