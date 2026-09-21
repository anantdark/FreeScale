package com.anant.freescale.data

/** High-level weigh-in stage for Home animations and copy. */
enum class MeasurePhase {
    /** Disconnected / idle. */
    Idle,
    /** Connected; waiting for step-on. */
    Ready,
    /** Armed / profile sent; waiting for step-on. */
    Armed,
    /** Live weight streaming (feet on scale, not yet stable). */
    Weighing,
    /** Weight locked; waiting for handlebars / BIA window. */
    WeightStable,
    /** Impedance / handlebar BIA packets arriving. */
    MeasuringBia,
    /** Final measurement published. */
    Complete,
    ;

    /**
     * True while the scale is actively taking a reading — feet on the platform
     * through to the last impedance packet.
     *
     * Excludes [Ready] and [Armed] on purpose: those are waiting states that can
     * sit there indefinitely if nobody steps on, so anything gated on this (such
     * as holding the display awake) would never let go.
     */
    val isMeasuring: Boolean
        get() = this == Weighing || this == WeightStable || this == MeasuringBia
}
