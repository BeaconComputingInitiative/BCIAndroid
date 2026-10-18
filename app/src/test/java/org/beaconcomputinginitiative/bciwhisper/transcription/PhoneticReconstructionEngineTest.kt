/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Phonetic Reconstruction Regression Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class PhoneticReconstructionEngineTest {
    @Test
    fun preservesVowelAndConsonantCuesWhileRemainingStable() {
        val sampleRate =
            16_000

        val seconds =
            4.0

        val count =
            (
                sampleRate *
                    seconds
                )
                .toInt()

        val input =
            FloatArray(
                count
            )

        var state =
            0x13579bdf

        for (
            i in
            input.indices
        ) {
            state =
                state *
                    1103515245 +
                    12345

            val noise =
                (
                    (
                        state ushr 8
                    ) and
                        0xffff
                    ) /
                    32768.0 -
                    1.0

            val t =
                i.toDouble() /
                    sampleRate

            val voiced =
                if (
                    t >= 0.8 &&
                    t <= 3.4
                ) {
                    0.10 *
                        sin(
                            2.0 *
                                PI *
                                160.0 *
                                t
                        ) +
                        0.08 *
                        sin(
                            2.0 *
                                PI *
                                700.0 *
                                t
                        ) +
                        0.07 *
                        sin(
                            2.0 *
                                PI *
                                1250.0 *
                                t
                        ) +
                        0.05 *
                        sin(
                            2.0 *
                                PI *
                                2600.0 *
                                t
                        ) +
                        0.025 *
                        sin(
                            2.0 *
                                PI *
                                6200.0 *
                                t
                        )
                } else {
                    0.0
                }

            input[i] =
                (
                    voiced +
                        0.025 *
                            noise
                    )
                    .coerceIn(
                        -1.0,
                        1.0
                    )
                    .toFloat()
        }

        val result =
            PhoneticReconstructionEngine()
                .enhance(
                    input,
                    sampleRate
                )

        assertTrue(
            "Output length must match input length",
            result.samples.size ==
                input.size
        )

        assertTrue(
            "Output must contain only finite samples",
            result.samples.all {
                it.isFinite()
            }
        )

        val start =
            (
                1.2 *
                    sampleRate
                )
                .toInt()

        val end =
            (
                3.0 *
                    sampleRate
                )
                .toInt()

        val formant700Ratio =
            projection(
                result.samples,
                start,
                end,
                700.0,
                sampleRate
            ) /
                projection(
                    input,
                    start,
                    end,
                    700.0,
                    sampleRate
                )
                    .coerceAtLeast(
                        1e-9
                    )

        val formant1250Ratio =
            projection(
                result.samples,
                start,
                end,
                1250.0,
                sampleRate
            ) /
                projection(
                    input,
                    start,
                    end,
                    1250.0,
                    sampleRate
                )
                    .coerceAtLeast(
                        1e-9
                    )

        val articulation2600Ratio =
            projection(
                result.samples,
                start,
                end,
                2600.0,
                sampleRate
            ) /
                projection(
                    input,
                    start,
                    end,
                    2600.0,
                    sampleRate
                )
                    .coerceAtLeast(
                        1e-9
                    )

        val frication6200Ratio =
            projection(
                result.samples,
                start,
                end,
                6200.0,
                sampleRate
            ) /
                projection(
                    input,
                    start,
                    end,
                    6200.0,
                    sampleRate
                )
                    .coerceAtLeast(
                        1e-9
                    )

        assertTrue(
            "Primary vowel formant energy must survive reconstruction",
            formant700Ratio >
                0.45
        )

        assertTrue(
            "Secondary vowel formant energy must survive reconstruction",
            formant1250Ratio >
                0.45
        )

        assertTrue(
            "Consonant articulation energy must survive reconstruction",
            articulation2600Ratio >
                0.40
        )

        assertTrue(
            "Fricative energy must retain a conservative floor",
            frication6200Ratio >
                0.20
        )

        assertTrue(
            "Formant protection must actually engage",
            result.metrics
                .formantProtectedBins >
                0L
        )

        assertTrue(
            "Harmonic protection must actually engage",
            result.metrics
                .harmonicProtectedBins >
                0L
        )

        assertTrue(
            "Output must retain meaningful speech-band RMS",
            rms(
                result.samples,
                start,
                end
            ) >
                0.02
        )
    }

    private fun projection(
        samples: FloatArray,
        start: Int,
        end: Int,
        frequencyHz: Double,
        sampleRate: Int
    ): Double {
        var real =
            0.0

        var imag =
            0.0

        val safeStart =
            start.coerceIn(
                0,
                samples.size
            )

        val safeEnd =
            end.coerceIn(
                safeStart,
                samples.size
            )

        val count =
            safeEnd -
                safeStart

        if (
            count <=
            0
        ) {
            return 0.0
        }

        for (
            index in
            safeStart until safeEnd
        ) {
            val phase =
                2.0 *
                    PI *
                    frequencyHz *
                    index /
                    sampleRate

            val value =
                samples[index]
                    .toDouble()

            real +=
                value *
                    kotlin.math.cos(
                        phase
                    )

            imag -=
                value *
                    kotlin.math.sin(
                        phase
                    )
        }

        return (
            2.0 *
                sqrt(
                    real *
                        real +
                        imag *
                            imag
                ) /
                count
            )
    }

    private fun rms(
        samples: FloatArray,
        start: Int,
        end: Int
    ): Double {
        var sum =
            0.0

        var count =
            0

        for (
            i in
            start.coerceAtLeast(0)
                until
                end.coerceAtMost(
                    samples.size
                )
        ) {
            val x =
                samples[i]
                    .toDouble()

            sum +=
                x *
                    x

            count++
        }

        return if (
            count >
            0
        ) {
            sqrt(
                sum /
                    count
            )
        } else {
            0.0
        }
    }
}
