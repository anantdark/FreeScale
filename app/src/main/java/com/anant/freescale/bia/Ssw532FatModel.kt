package com.anant.freescale.bia

import com.anant.freescale.data.GenderType

/**
 * Body-fat estimation for the Dr. Trust SSW532 / ICOMON FG2211WB.
 *
 * ## Why this exists
 *
 * Every other metric this app reports is derived from fat-free mass by [Wla25],
 * and that chain reproduces the vendor app exactly. Body fat is the one number
 * that has to come from impedance, and the vendor's own regression
 * ([Wla25.fatMassFromImpedances]) cannot produce the vendor app's answers on this
 * hardware — see that function's KDoc. So body fat is computed here instead, from
 * a whole-body resistance and the Sun 2003 fat-free-mass equation in
 * [StandardImpedanceLib], which is openScale's science-based fallback for scales
 * whose vendor formulas can't be reproduced.
 *
 * ## Which impedance, and why not channel A alone
 *
 * The scale reports ten resistances: whole-body channels A and B in measurement
 * packet 0, and eight segmental values Z1-Z8 in packet 1.
 *
 * Channel A alone was tried first and is wrong. Two weigh-ins 33 minutes apart
 * produced an *identical* channel A (286.7 ohm) and an identical weight
 * (71.15 kg), yet the vendor app returned 15.3 % and 15.4 %. A model reading only
 * channel A cannot tell those apart — it is too insensitive to track the vendor
 * app at all, capping accuracy at 0.50 pp.
 *
 * The fix is to pair it with Z6, a cross-body diagonal. Channel A supplies a
 * stable baseline (1.4 % spread over 17 readings, by far the steadiest channel)
 * and Z6 supplies the sensitivity it lacks, while still being one of the two
 * quietest segmentals:
 *
 * ```
 *   channel   range (ohm)     rel spread
 *   A         282.8-286.9        1.4 %   <- baseline, used here
 *   Z5        230.8-254.4        9.8 %
 *   Z6        223.0-250.4       11.6 %   <- sensitivity, used here
 *   B         262.7-301.7       13.4 %   (unexplained step 301 -> 262 in Sept)
 *   Z4        231.0-280.1       19.0 %
 *   Z1        243.0-303.4       22.1 %
 *   Z2        233.1-304.6       26.0 %
 *   Z7        209.0-279.7       29.5 %
 *   Z3         10.3-49.3       138.1 %
 *   Z8          2.6-24.6       171.8 %
 * ```
 *
 * Both parts are long paths through the torso and limbs — a cross-body diagonal
 * is the classic hand-to-foot BIA route — so the sum is physically a whole-body
 * resistance, which is what Sun 2003 wants. The noisy short paths (Z3, Z8) and
 * the erratic ones (Z1, Z2, Z7, B) are deliberately excluded.
 *
 * ## The calibration factor
 *
 * Sun 2003 was fitted on hand-to-foot resistance at 50 kHz, which lands near
 * 500 ohm for an adult male; [StandardImpedanceLib] says as much. Z6 + A sums to
 * ~525-533 ohm, close but not identical, so it is scaled into that band.
 *
 * [WHOLE_BODY_SCALE] is that factor, solved rather than guessed: for each
 * reference weigh-in, invert Sun 2003 for the resistance that yields the vendor
 * app's fat-free mass, divide by Z6 + A, and average.
 *
 * ```
 *   175 cm, 26 y, male
 *   weigh-in   weight   Z6+A    vendor fat   needed R   ratio
 *   14:03      71.20    525.4       14.6 %      453.4   0.86295
 *   15:08      71.15    530.4       15.3 %      460.4   0.86802
 *   15:41      71.15    533.0       15.4 %      461.4   0.86560
 *                                          K (mean) = 0.8655
 * ```
 *
 * ## Weight correction
 *
 * Sun 2003's own weight term (+0.26·W in FFM) does not match how the vendor app
 * moves body fat when weight changes at nearly fixed impedance. The 18:30
 * weigh-in is the clearest example: Z6+A was identical to 15:08 (530.4 ohm),
 * weight rose 0.60 kg, and the vendor app went *down* from 15.3 % to 15.0 %,
 * while bare Sun 2003 went *up* to 15.7 %.
 *
 * [WEIGHT_FAT_CORRECTION_PER_KG] is the residual slope of
 * `(vendorFat − sunFat)` against `(weight − [REF_WEIGHT_KG])` over the four
 * official weigh-ins below. Applied after Sun 2003, it brings every reference
 * — including 18:30 — within 0.15 pp of the vendor app:
 *
 * ```
 *   weigh-in   weight   vendor   sun(K·(Z6+A))   after correction
 *   14:03      71.20     14.6 %         14.74 %            14.74 %
 *   15:08      71.15     15.3 %         15.16 %            15.22 %
 *   15:41      71.15     15.4 %         15.39 %            15.45 %
 *   18:30      71.75     15.0 %         15.65 %            15.00 %
 * ```
 *
 * ## Accuracy
 *
 * Against the four reference weigh-ins: within 0.15 pp, so the displayed
 * one-decimal figure matches the vendor app. Across all stored readings body
 * fat spans roughly 13.1-16.4 % (mean ~14.4) with the model input at 439-465 ohm,
 * inside Sun 2003's valid range.
 *
 * An independent US Navy circumference estimate for the same subject (neck
 * 38.1 cm, waist 80.8 cm, height 175 cm) gives 13.4 %, which this model's mean
 * sits ~1 pp from. openScale's own SSW532 handler, which feeds the foot-to-foot
 * path into the same equation unscaled, gives 21.3 % — 7.9 pp from the tape and
 * outside its +/-3 pp band, which is why that path is only a last-resort
 * fallback here.
 *
 * ## Limits
 *
 * Exact agreement on every reading is not achievable and not worth chasing. The
 * vendor app returned 14.6 % and 15.3 % on an unchanged body 65 minutes apart,
 * and its own scale display read 15.6 % where the app read 15.3 %, so the target
 * itself moves by more than the residual error here.
 *
 * Z6 + A is also not proven to be what the vendor app reads. Of 154 channel
 * combinations, 15 fit all three early reference points within 0.25 pp; Z6 + A was
 * chosen from those on channel stability, physical interpretability and agreement
 * with the tape measurement. The weight correction is a single slope against the
 * same references plus the 18:30 official 15.0 % reading. Recalibrating against a
 * better reference (a DEXA scan, say) is a one-line change to [WHOLE_BODY_SCALE]
 * and/or [WEIGHT_FAT_CORRECTION_PER_KG].
 */
object Ssw532FatModel {

    /**
     * Ratio of the hand-to-foot resistance Sun 2003 expects to this scale's
     * `Z6 + channel A`. Least-squares over the three early reference weigh-ins in
     * the class KDoc, which also explains how to recompute it.
     */
    const val WHOLE_BODY_SCALE = 0.8655

    /**
     * Fallback factor for channel A on its own, used when Z6 is unavailable.
     * Same derivation, same three reference points. Worse than [WHOLE_BODY_SCALE]
     * (0.50 pp rather than 0.14) because channel A barely responds.
     */
    const val CHANNEL_A_ONLY_SCALE = 1.5994

    /**
     * Anchor weight for [WEIGHT_FAT_CORRECTION_PER_KG]. The 14:03 official
     * weigh-in; corrections are relative to this so a 71.20 kg reading is
     * unchanged by the slope term.
     */
    const val REF_WEIGHT_KG = 71.20

    /**
     * Added to Sun 2003 body fat, in percentage points per kg above
     * [REF_WEIGHT_KG]. Negative because the vendor app's fat % falls with
     * weight at fixed impedance while bare Sun 2003 rises. Fitted on the four
     * official weigh-ins in the class KDoc.
     */
    const val WEIGHT_FAT_CORRECTION_PER_KG = -1.19

    private val CHANNEL_A_RANGE = 100.0..600.0
    private val Z6_RANGE = 100.0..600.0

    /**
     * Foot-to-foot path (trunk + both legs) already sits near 500 ohm, so it goes
     * into Sun 2003 unscaled — this is exactly what openScale's SSW532 handler
     * does. It reads ~6 pp above the vendor app and ~8 pp above the tape estimate,
     * so it is a degraded last resort, not an equivalent.
     */
    private val FOOT_PATH_RANGE = 300.0..800.0

    /** Which impedance path produced a body-fat estimate, best first. */
    enum class Source(val label: String) {
        Z6_PLUS_CHANNEL_A("Z6+channel A, device-calibrated"),
        CHANNEL_A_ONLY("channel A only, device-calibrated"),
        FOOT_PATH("foot-to-foot Z3+Z4+Z5, uncalibrated"),
    }

    data class Estimate(
        /** Raw body fat as a percentage of body weight, before [Wla25] clamps and rounds it. */
        val bodyFatPercent: Double,
        /** Whole-body resistance handed to Sun 2003, in ohm. */
        val wholeBodyOhm: Double,
        val source: Source,
    )

    /**
     * Best available body-fat estimate, or `null` if no impedance path is usable
     * (in which case the caller should publish a weight-only measurement).
     *
     * @param weightKg the weight the scale settled on, unrounded.
     * @param channelAOhm pkt0 channel A, ohm.
     * @param z6Ohm pkt1 Z6 cross-body diagonal, ohm.
     * @param footPathOhm Z3 + Z4 + Z5, ohm. Last resort only.
     */
    fun estimate(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        gender: GenderType,
        channelAOhm: Double,
        z6Ohm: Double,
        footPathOhm: Double,
    ): Estimate? {
        val attempts = buildList {
            if (channelAOhm in CHANNEL_A_RANGE && z6Ohm in Z6_RANGE) {
                add((z6Ohm + channelAOhm) * WHOLE_BODY_SCALE to Source.Z6_PLUS_CHANNEL_A)
            }
            if (channelAOhm in CHANNEL_A_RANGE) {
                add(channelAOhm * CHANNEL_A_ONLY_SCALE to Source.CHANNEL_A_ONLY)
            }
            if (footPathOhm in FOOT_PATH_RANGE) {
                add(footPathOhm to Source.FOOT_PATH)
            }
        }
        for ((ohm, source) in attempts) {
            fatPercent(weightKg, heightCm, age, gender, ohm)?.let {
                return Estimate(it, ohm, source)
            }
        }
        return null
    }

    /**
     * Sun 2003 fat-free mass via [StandardImpedanceLib], as a fat percentage,
     * then adjusted by [WEIGHT_FAT_CORRECTION_PER_KG].
     */
    private fun fatPercent(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        gender: GenderType,
        wholeBodyOhm: Double,
    ): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0 || wholeBodyOhm <= 0.0) return null
        val lib = StandardImpedanceLib(
            gender = gender,
            age = age,
            weightKg = weightKg,
            heightM = heightCm / 100.0,
            impedance = wholeBodyOhm,
        )
        val pct = lib.totalFatPercentage +
            WEIGHT_FAT_CORRECTION_PER_KG * (weightKg - REF_WEIGHT_KG)
        // Fat-free mass above body weight means the resistance is far outside the
        // equation's range; fall through rather than report nonsense.
        return if (pct.isFinite() && pct > 0.0) pct else null
    }
}
