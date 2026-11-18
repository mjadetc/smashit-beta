package com.smashit_beta.backend.entity;

// The life-cycle state of an event. Matches the CHECK constraint in V9__add_event_status.sql.
//
//            complete()             cancel()
//  COMPLETED <────────── ACTIVE ──────────> CANCELLED
//      │                 ▲    ▲                 │
//      └─ reactivate() ──┘    └─ reactivate() ──┘
//
// There is no REMOVED value: a removed event is deleted from the database entirely.
public enum EventStatus {

    // Upcoming or in progress: can be edited and have matches set up.
    ACTIVE,

    // Finished successfully: read-only, shown only when filtering by COMPLETED.
    COMPLETED,

    // Called off: read-only, shown only when filtering by CANCELLED.
    CANCELLED
}