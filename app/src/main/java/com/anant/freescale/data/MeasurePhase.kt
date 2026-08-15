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
}
