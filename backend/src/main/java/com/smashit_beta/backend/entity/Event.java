package com.smashit_beta.backend.entity;

import com.smashit_beta.backend.exception.InvalidStateException;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;

// @Entity = Hibernate maps this class to a database table.
// @Table  = the table name (created by V4__create_events_table.sql).
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A RELATIONSHIP instead of a plain "Long organizerId":
    // @ManyToOne  = many events can belong to one user.
    // @JoinColumn = the foreign-key column in the events table.
    // FetchType.LAZY = don't load the whole User from the database until we actually
    //                  use it. (Reading only its id doesn't trigger a load.)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    // OffsetDateTime = a date + time + offset from UTC, e.g. 2026-11-20T19:00+08:00.
    // PostgreSQL's TIMESTAMPTZ stores it as an exact moment in time.
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    // "int" (primitive) because it can never be null.
    @Column(name = "court_count", nullable = false)
    private int courtCount;

    // EnumType.STRING stores the enum's NAME ("SINGLE_GAME") in the column.
    // Never use the default EnumType.ORDINAL: it stores the position (0, 1), and
    // reordering the enum later would silently corrupt every existing row.
    @Enumerated(EnumType.STRING)
    @Column(name = "match_format", nullable = false, length = 20)
    private MatchFormat matchFormat;

    // "Integer" (wrapper class) instead of "int" because this column CAN be null.
    @Column(name = "change_ends_at")
    private Integer changeEndsAt;

    @Column(name = "description")
    private String description;

    // NEW: ACTIVE, COMPLETED or CANCELLED. Stored as text, like matchFormat.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    // NEW (V11): how equal expense shares are rounded up. Changed from the expenses
    // screen, not the event form, so it's not part of updateDetails().
    @Enumerated(EnumType.STRING)
    @Column(name = "share_rounding", nullable = false, length = 10)
    private ShareRounding shareRounding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA: Hibernate uses this empty constructor when loading rows.
    // "protected" stops the rest of our code from creating half-empty Events.
    protected Event() {
    }

    // The constructor our code uses to create a new event.
    public Event(
            User organizer,
            String name,
            String location,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int courtCount,
            MatchFormat matchFormat,
            Integer changeEndsAt,
            String description
    ) {
        this.organizer = organizer;
        // NEW: every new event starts ACTIVE.
        this.status = EventStatus.ACTIVE;
        // NEW: exact shares until the organizer picks a rounding.
        this.shareRounding = ShareRounding.NONE;
        updateDetails(name, location, startsAt, endsAt, courtCount,
                matchFormat, changeEndsAt, description);
    }

    // Instead of 8 separate setters, one method changes the event's details together.
    // Reason: a class should control how its data changes. The organizer is
    // deliberately NOT changeable here: an event can't be handed to someone else.
    public void updateDetails(
            String name,
            String location,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int courtCount,
            MatchFormat matchFormat,
            Integer changeEndsAt,
            String description
    ) {
        this.name = name;
        this.location = location;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.courtCount = courtCount;
        this.matchFormat = matchFormat;
        this.changeEndsAt = changeEndsAt;
        this.description = description;
    }

    // ---------- NEW: status changes ----------
    // The entity protects its own rules, so they apply no matter who calls these
    // methods. Only ACTIVE events can be completed or cancelled.

    // ACTIVE -> COMPLETED: the event finished successfully.
    public void complete() {
        if (status == EventStatus.COMPLETED) {
            throw new InvalidStateException("This event is already completed.");
        }
        if (status == EventStatus.CANCELLED) {
            throw new InvalidStateException(
                    "A cancelled event can't be completed. Reactivate it first.");
        }
        this.status = EventStatus.COMPLETED;
    }

    // ACTIVE -> CANCELLED: the event was called off.
    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidStateException("This event is already cancelled.");
        }
        if (status == EventStatus.COMPLETED) {
            throw new InvalidStateException("A completed event can't be cancelled.");
        }
        this.status = EventStatus.CANCELLED;
    }

    // COMPLETED or CANCELLED -> ACTIVE: undo a mistake.
    public void reactivate() {
        if (status == EventStatus.ACTIVE) {
            throw new InvalidStateException("This event is already active.");
        }
        this.status = EventStatus.ACTIVE;
    }

    // A small helper so callers can write event.isActive()
    // instead of event.getStatus() == EventStatus.ACTIVE.
    public boolean isActive() {
        return status == EventStatus.ACTIVE;
    }

    // NEW (V11): choose how equal expense shares are rounded.
    public void changeShareRounding(ShareRounding shareRounding) {
        this.shareRounding = shareRounding;
    }

    public ShareRounding getShareRounding() {
        return shareRounding;
    }

    // Runs automatically right before the first INSERT.
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Runs automatically right before every UPDATE.
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getOrganizer() {
        return organizer;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public int getCourtCount() {
        return courtCount;
    }

    public MatchFormat getMatchFormat() {
        return matchFormat;
    }

    public Integer getChangeEndsAt() {
        return changeEndsAt;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}