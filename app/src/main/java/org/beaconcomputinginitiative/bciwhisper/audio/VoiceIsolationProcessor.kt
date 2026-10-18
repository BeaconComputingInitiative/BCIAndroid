/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Voice-Aware Isolation Processor
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Produces the derivative isolated track.
 *
 * Raw recording is never processed by this class. The spectral lock controls
 * the isolation window, while speech-preservation guards keep the fundamental,
 * formant, and articulation regions from being narrowed away.
 */
class VoiceIsolationProcessor(
    private val sampleRate: Int
) {
    private val hp1 = Biquad()
    private val hp2 = Biquad()
    private val lp1 = Biquad()
    private val lp2 = Biquad()
    private val hum60 = Biquad()
    private val presence = Biquad()
    private val air = Biquad()

    private var lowHz = 90.0
    private var highHz = 8000.0

    private var gateGain = 1.0

    fun reset(
        initialLowHz: Double,
        initialHighHz: Double
    ) {
        lowHz =
            targetLow(
                initialLowHz
            )

        highHz =
            targetHigh(
                initialHighHz
            )

        gateGain = 1.0

        hp1.reset()
        hp2.reset()
        lp1.reset()
        lp2.reset()
        hum60.reset()
        presence.reset()
        air.reset()

        configure()
    }

    fun process(
        input: ShortArray,
        count: Int,
        lockLowHz: Double,
        lockHighHz: Double,
        signalActive: Boolean
    ): ShortArray {
        val targetLow =
            targetLow(
                lockLowHz
            )

        val targetHigh =
            targetHigh(
                lockHighHz
            )

        lowHz =
            trackEdge(
                lowHz,
                targetLow
            )

        highHz =
            trackEdge(
                highHz,
                targetHigh
            )

        if (
            highHz - lowHz <
            MIN_PASSBAND_HZ
        ) {
            val center =
                (
                    highHz +
                        lowHz
                    ) * 0.5

            lowHz =
                (
                    center -
                        MIN_PASSBAND_HZ /
                            2.0
                    )
                    .coerceAtLeast(
                        MIN_LOW_HZ
                    )

            highHz =
                (
                    center +
                        MIN_PASSBAND_HZ /
                            2.0
                    )
                    .coerceAtMost(
                        sampleRate *
                            0.47
                    )
        }

        configure()

        val n =
            count.coerceIn(
                0,
                input.size
            )

        val output =
            ShortArray(n)

        val gateTarget =
            if (signalActive) {
                1.0
            } else {
                DEAD_AIR_GAIN
            }

        val gateCoefficient =
            if (signalActive) {
                GATE_ATTACK
            } else {
                GATE_RELEASE
            }

        for (i in 0 until n) {
            gateGain +=
                gateCoefficient *
                    (
                        gateTarget -
                            gateGain
                        )

            var x =
                input[i]
                    .toDouble() /
                    32768.0

            // Narrow mains rejection before the voice band.
            x = hum60.process(x)

            // Fourth-order adaptive band-pass follows the acquired source.
            x = hp1.process(x)
            x = hp2.process(x)
            x = lp1.process(x)
            x = lp2.process(x)

            // Gentle intelligibility shaping: enough to improve consonants
            // without turning the isolated derivative into a mastered track.
            x = presence.process(x)
            x = air.process(x)

            x *= gateGain

            output[i] =
                (
                    x.coerceIn(
                        -1.0,
                        1.0
                    ) *
                        32767.0
                    )
                    .roundToInt()
                    .toShort()
        }

        return output
    }

    private fun targetLow(
        lockLowHz: Double
    ): Double {
        val lockTarget =
            lockLowHz -
                LOW_GUARD_HZ

        // Never let a narrow lock erase the voice fundamental region.
        return minOf(
            lockTarget,
            SPEECH_FUNDAMENTAL_GUARD_HZ
        )
            .coerceIn(
                MIN_LOW_HZ,
                sampleRate *
                    0.40
            )
    }

    private fun targetHigh(
        lockHighHz: Double
    ): Double {
        val lockTarget =
            lockHighHz +
                HIGH_GUARD_HZ

        // Preserve upper formants and consonant energy even when the lock
        // candidate itself is narrower.
        return maxOf(
            lockTarget,
            SPEECH_ARTICULATION_GUARD_HZ
        )
            .coerceIn(
                MIN_HIGH_HZ,
                sampleRate *
                    0.47
            )
    }

    private fun trackEdge(
        current: Double,
        candidate: Double
    ): Double {
        val desired =
            current +
                EDGE_TRACKING *
                    (
                        candidate -
                            current
                        )

        val step =
            (
                desired -
                    current
                )
                .coerceIn(
                    -MAX_EDGE_STEP_HZ,
                    MAX_EDGE_STEP_HZ
                )

        return current + step
    }

    private fun configure() {
        hp1.highPass(
            sampleRate,
            lowHz,
            Q_BUTTERWORTH
        )
        hp2.highPass(
            sampleRate,
            lowHz,
            Q_BUTTERWORTH
        )

        lp1.lowPass(
            sampleRate,
            highHz,
            Q_BUTTERWORTH
        )
        lp2.lowPass(
            sampleRate,
            highHz,
            Q_BUTTERWORTH
        )

        hum60.notch(
            sampleRate,
            MAINS_HUM_HZ,
            HUM_NOTCH_Q
        )

        presence.peaking(
            sampleRate,
            PRESENCE_HZ,
            PRESENCE_Q,
            PRESENCE_GAIN_DB
        )

        air.peaking(
            sampleRate,
            AIR_HZ,
            AIR_Q,
            AIR_GAIN_DB
        )
    }

    companion object {
        private const val MIN_LOW_HZ =
            65.0

        private const val MIN_HIGH_HZ =
            3600.0

        private const val SPEECH_FUNDAMENTAL_GUARD_HZ =
            180.0

        private const val SPEECH_ARTICULATION_GUARD_HZ =
            5200.0

        private const val LOW_GUARD_HZ =
            90.0

        private const val HIGH_GUARD_HZ =
            160.0

        private const val MIN_PASSBAND_HZ =
            1200.0

        private const val EDGE_TRACKING =
            0.18

        private const val MAX_EDGE_STEP_HZ =
            90.0

        private const val MAINS_HUM_HZ =
            60.0

        private const val HUM_NOTCH_Q =
            18.0

        private const val PRESENCE_HZ =
            2800.0

        private const val PRESENCE_Q =
            0.95

        private const val PRESENCE_GAIN_DB =
            1.8

        private const val AIR_HZ =
            5600.0

        private const val AIR_Q =
            0.75

        private const val AIR_GAIN_DB =
            0.8

        // Dead air is reduced, not muted. Keeping a residual floor avoids
        // abrupt gates and preserves natural word tails for later analysis.
        private const val DEAD_AIR_GAIN =
            0.18

        private const val GATE_ATTACK =
            0.008

        private const val GATE_RELEASE =
            0.00006

        private const val Q_BUTTERWORTH =
            0.70710678
    }
}

private class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1 = 0.0
    private var z2 = 0.0

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun highPass(
        sampleRate: Int,
        frequency: Double,
        q: Double
    ) {
        configurePass(
            sampleRate,
            frequency,
            q,
            highPass = true
        )
    }

    fun lowPass(
        sampleRate: Int,
        frequency: Double,
        q: Double
    ) {
        configurePass(
            sampleRate,
            frequency,
            q,
            highPass = false
        )
    }

    fun notch(
        sampleRate: Int,
        frequency: Double,
        q: Double
    ) {
        val f =
            safeFrequency(
                sampleRate,
                frequency
            )

        val w0 =
            2.0 *
                PI *
                f /
                sampleRate

        val c =
            cos(w0)

        val alpha =
            sin(w0) /
                (
                    2.0 *
                        q
                    )

        normalize(
            rawB0 = 1.0,
            rawB1 = -2.0 * c,
            rawB2 = 1.0,
            rawA0 = 1.0 + alpha,
            rawA1 = -2.0 * c,
            rawA2 = 1.0 - alpha
        )
    }

    fun peaking(
        sampleRate: Int,
        frequency: Double,
        q: Double,
        gainDb: Double
    ) {
        val f =
            safeFrequency(
                sampleRate,
                frequency
            )

        val a =
            10.0.pow(
                gainDb /
                    40.0
            )

        val w0 =
            2.0 *
                PI *
                f /
                sampleRate

        val c =
            cos(w0)

        val alpha =
            sin(w0) /
                (
                    2.0 *
                        q
                    )

        normalize(
            rawB0 =
                1.0 +
                    alpha *
                        a,
            rawB1 =
                -2.0 *
                    c,
            rawB2 =
                1.0 -
                    alpha *
                        a,
            rawA0 =
                1.0 +
                    alpha /
                        a,
            rawA1 =
                -2.0 *
                    c,
            rawA2 =
                1.0 -
                    alpha /
                        a
        )
    }

    private fun configurePass(
        sampleRate: Int,
        frequency: Double,
        q: Double,
        highPass: Boolean
    ) {
        val f =
            safeFrequency(
                sampleRate,
                frequency
            )

        val w0 =
            2.0 *
                PI *
                f /
                sampleRate

        val c =
            cos(w0)

        val alpha =
            sin(w0) /
                (
                    2.0 *
                        q
                    )

        val rawB0: Double
        val rawB1: Double
        val rawB2: Double

        if (highPass) {
            rawB0 =
                (
                    1.0 +
                        c
                    ) / 2.0

            rawB1 =
                -(
                    1.0 +
                        c
                    )

            rawB2 =
                (
                    1.0 +
                        c
                    ) / 2.0
        } else {
            rawB0 =
                (
                    1.0 -
                        c
                    ) / 2.0

            rawB1 =
                1.0 -
                    c

            rawB2 =
                (
                    1.0 -
                        c
                    ) / 2.0
        }

        normalize(
            rawB0,
            rawB1,
            rawB2,
            1.0 + alpha,
            -2.0 * c,
            1.0 - alpha
        )
    }

    private fun safeFrequency(
        sampleRate: Int,
        frequency: Double
    ): Double =
        frequency.coerceIn(
            20.0,
            sampleRate *
                0.48
        )

    private fun normalize(
        rawB0: Double,
        rawB1: Double,
        rawB2: Double,
        rawA0: Double,
        rawA1: Double,
        rawA2: Double
    ) {
        b0 =
            rawB0 /
                rawA0
        b1 =
            rawB1 /
                rawA0
        b2 =
            rawB2 /
                rawA0
        a1 =
            rawA1 /
                rawA0
        a2 =
            rawA2 /
                rawA0
    }

    fun process(
        input: Double
    ): Double {
        val output =
            b0 *
                input +
                z1

        z1 =
            b1 *
                input -
                a1 *
                    output +
                z2

        z2 =
            b2 *
                input -
                a2 *
                    output

        return if (
            output.isFinite()
        ) {
            output
        } else {
            0.0
        }
    }
}
