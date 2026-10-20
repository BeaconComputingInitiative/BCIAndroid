/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Transcription Quality Gate Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TranscriptionQualityGateTest {
    @Test
    fun cleanSpeechUsesFastPath() {
        val sampleRate =
            16_000

        val samples =
            FloatArray(
                sampleRate *
                    3
            ) { i ->
                val t =
                    i.toDouble() /
                        sampleRate

                val voiced =
                    0.22 *
                        sin(
                            2.0 *
                                PI *
                                180.0 *
                                t
                        ) +
                        0.08 *
                        sin(
                            2.0 *
                                PI *
                                900.0 *
                                t
                        )

                val pause =
                    i in
                        sampleRate..
                            (
                                sampleRate +
                                    sampleRate /
                                    2
                                )

                if (pause) {
                    0.0005f
                } else {
                    voiced.toFloat()
                }
            }

        val result =
            TranscriptionQualityGate
                .assess(
                    samples,
                    sampleRate
                )

        assertFalse(
            "Clean isolated speech should bypass redundant reconstruction",
            result.reconstruct
        )
    }

    @Test
    fun noisyLowContrastSpeechKeepsFullReconstruction() {
        val sampleRate =
            16_000

        var state =
            0x13579bdf

        val samples =
            FloatArray(
                sampleRate *
                    3
            ) { i ->
                state =
                    state *
                        1103515245 +
                        12345

                val noise =
                    (
                        (
                            state ushr
                                8
                            ) and
                            0xffff
                        ) /
                        32768.0 -
                        1.0

                val t =
                    i.toDouble() /
                        sampleRate

                (
                    0.025 *
                        sin(
                            2.0 *
                                PI *
                                190.0 *
                                t
                        ) +
                        0.08 *
                            noise
                    )
                    .toFloat()
            }

        val result =
            TranscriptionQualityGate
                .assess(
                    samples,
                    sampleRate
                )

        assertTrue(
            "Noisy low-contrast speech should retain Wiener/LPC reconstruction",
            result.reconstruct
        )
    }
}
