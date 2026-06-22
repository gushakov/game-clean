package com.github.gameclean.core.model.daytime;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * How far day-phase announcements have progressed: a world-singleton aggregate holding the
 * {@link #announcedThroughHour} watermark — the absolute game hour (game seconds since the epoch, divided by
 * the calendar's seconds-per-hour) of the most recent day phase the world has announced, or {@code -1} when
 * none has been announced yet — together with the optimistic-locking {@link #version}.
 *
 * <p><b>The dedup watermark for an asynchronous, polling announcer.</b> The time ticker fires the
 * announcement interaction repeatedly; without a record of "what have we already said", every poll inside a
 * dawn hour would re-announce dawn. This monotonic watermark makes the announcement <em>idempotent per
 * occurrence</em> and restart-safe: a phase at absolute hour H is announced once, and only once H is passed
 * does the next phase become pending. Persisting it (rather than holding "last hour" in the ticker thread)
 * is what survives a {@code bye}/restart mid-phase.
 *
 * <p><b>Why the version is on the model, not just the entity.</b> The transaction boundary gives atomic
 * rollback, not isolation — two concurrent observers can both read this log as pending and both write, so the
 * boundary alone does not stop a double announcement (design-notes §5: atomicity ≠ isolation). The canonical
 * DDD detector for a concurrent modification of one aggregate is optimistic locking, and we carry its
 * {@code version} <em>all the way into the aggregate</em>: the use case reads the log (capturing the version),
 * computes the advance, and the write is checked against that version — the loser's stale write is rejected.
 * Carrying the token on the always-valid model (a knowing, documented departure from §2 minimalism for a
 * singleton whose whole purpose is this guarded watermark) keeps the concurrency contract explicit end to end
 * rather than hidden in the adapter. The aggregate <em>carries</em> the version but never interprets it — it
 * is opaque concurrency metadata, set by persistence on read and checked by persistence on write — so it is
 * deliberately excluded from value equality (two logs with the same watermark are the same domain value
 * whatever their version).
 *
 * <p>Always-valid: the watermark is never below the {@code -1} sentinel and the version is never negative. The
 * watermark only moves forward, so {@link #announceThrough(long)} rejects a non-advancing hour as a caller bug
 * (a plain {@code IllegalArgumentException}, the behaviour-method guard convention — distinct from the
 * construction gate's {@link InvalidDomainObjectError}); it carries the current version onto the new instance
 * so the save can check it. {@link #isPending(long)} is the query the use case branches on before announcing.
 */
@Getter
@EqualsAndHashCode
public class DayPhaseLog {

    /** The watermark value meaning no day phase has been announced yet. */
    public static final long NONE = -1L;

    private final long announcedThroughHour;

    /** Optimistic-locking token — opaque to the domain, managed by persistence, not part of value equality. */
    @EqualsAndHashCode.Exclude
    private final long version;

    @Builder
    public DayPhaseLog(long announcedThroughHour, long version) {
        if (announcedThroughHour < NONE) {
            throw new InvalidDomainObjectError(
                    "announced-through hour must not be below " + NONE + ", got " + announcedThroughHour);
        }
        if (version < 0) {
            throw new InvalidDomainObjectError("day-phase-log version must not be negative, got " + version);
        }
        this.announcedThroughHour = announcedThroughHour;
        this.version = version;
    }

    /**
     * The log of a fresh world: nothing announced yet, at the initial (new-instance) version. Seeded once at
     * game initialization, beside the clock; persistence treats version {@code 0} as a new row to insert.
     *
     * @return a log with its watermark at {@link #NONE} and version {@code 0}
     */
    public static DayPhaseLog initial() {
        return new DayPhaseLog(NONE, 0);
    }

    /**
     * Whether a day phase falling at the given absolute hour still awaits announcement — i.e. the watermark
     * has not yet reached it. The use case branches on this before opening a transaction; the optimistic
     * {@link #version} is what actually arbitrates a concurrent write.
     *
     * @param absoluteHour the absolute game hour a pending phase falls at (must not be negative)
     * @return {@code true} if that hour is beyond the current watermark
     */
    public boolean isPending(long absoluteHour) {
        if (absoluteHour < 0) {
            throw new IllegalArgumentException("absolute hour must not be negative, got " + absoluteHour);
        }
        return absoluteHour > announcedThroughHour;
    }

    /**
     * Advances the watermark to the given absolute hour, yielding a new log that <em>carries the current
     * version</em> so the persisting write is checked against it. The aggregate is immutable, so the original
     * is untouched. The hour must be strictly beyond the current watermark (the only call site has just checked
     * {@link #isPending(long)}); a non-advancing hour is a caller bug.
     *
     * @param absoluteHour the absolute game hour now announced (must be greater than the current watermark)
     * @return a new log whose watermark is {@code absoluteHour}, carrying this log's version
     */
    public DayPhaseLog announceThrough(long absoluteHour) {
        if (absoluteHour <= announcedThroughHour) {
            throw new IllegalArgumentException(
                    "announced-through hour must advance past %d, got %d".formatted(announcedThroughHour, absoluteHour));
        }
        return new DayPhaseLog(absoluteHour, version);
    }
}
