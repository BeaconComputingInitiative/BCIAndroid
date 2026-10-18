/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Voice Isolation Regression Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceIsolationProcessorTest {
    @Test
    fun preservesPhoneticBandsMoreThanOutOfBandEnergy() {
        val fundamental =
            gainAt(160.0)

        val vowelFormant =
            gainAt(900.0)

        val articulation =
            gainAt(3200.0)

        val frication =
            gainAt(6500.0)

        val rumble =
            gainAt(30.0)

        val ultrasonicLike =
            gainAt(12000.0)

        assertTrue(
            "Fundamental region must survive isolation",
            fundamental > 0.45
        )

        assertTrue(
            "Vowel formant region must survive isolation",
            vowelFormant > 0.55
        )

        assertTrue(
            "Consonant articulation region must survive isolation",
            articulation > 0.50
        )

        assertTrue(
            "High-frequency frication must survive isolation",
            frication > 0.30
        )

        assertTrue(
            "Rumble should be attenuated relative to vowel energy",
            rumble < vowelFormant * 0.45
        )

        assertTrue(
            "Very high-frequency energy should be attenuated relative to articulation",
            ultrasonicLike < articulation * 0.55
        )
    }

    private fun gainAt(
        frequencyHz: Double
    ): Double {
        val sampleRate =
            44_100

        val processor =
            VoiceIsolationProcessor(
                sampleRate
            )

        processor.reset(
            initialLowHz = 300.0,
            initialHighHz = 3200.0
        )

        val seconds =
            1.5

        val count =
            (
                sampleRate *
                    seconds
                )
                .toInt()

        val input =
            ShortArray(
                count
            ) { i ->
                (
                    0.25 *
                        sin(
                            2.0 *
                                PI *
                                frequencyHz *
                                i /
                                sampleRate
                        ) *
                        32767.0
                    )
                    .toInt()
                    .toShort()
            }

        val output =
            processor.process(
                input = input,
                count = input.size,
                lockLowHz = 300.0,
                lockHighHz = 3200.0,
                signalActive = true
            )

        val warmup =
            sampleRate / 4

        val inputRms =
            rms(
                input,
                warmup
            )

        val outputRms =
            rms(
                output,
                warmup
            )

        return if (inputRms > 1e-12) {
            outputRms /
                inputRms
        } else {
            0.0
        }
    }

    private fun rms(
        samples: ShortArray,
        start: Int
    ): Double {
        var sum =
            0.0

        var count =
            0

        for (
            i in
            start.coerceAtLeast(0)
                until samples.size
        ) {
            val x =
                samples[i]
                    .toDouble() /
                    32768.0

            sum +=
                x * x

            count++
        }

        return if (count > 0) {
            sqrt(
                sum /
                    count
            )
        } else {
            0.0
        }
    }
}
