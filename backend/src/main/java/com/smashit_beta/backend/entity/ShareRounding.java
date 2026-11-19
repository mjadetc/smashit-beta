package com.smashit_beta.backend.entity;

// How each equal share is rounded UP, so people can pay in cash easily.
// Names match chk_events_share_rounding in V11.
public enum ShareRounding {

    // Exact to the centavo: 53.33 / 53.34.
    NONE(1),

    // Up to a whole peso: 53.33 -> 54.
    PESO_1(100),

    // Up to a multiple of 5 pesos: 53.33 -> 55.
    PESO_5(500),

    // Up to a multiple of 10 pesos: 53.33 -> 60.
    PESO_10(1000);

    // An enum can carry data: each value knows its step, in centavos.
    private final long unitCents;

    ShareRounding(long unitCents) {
        this.unitCents = unitCents;
    }

    public long unitCents() {
        return unitCents;
    }
}