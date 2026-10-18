/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Dead-Air / Prosody Regression Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

class TranscriptionAudioPlannerTest {
    @Test
    fun preservesConversationalPause() {
        val sampleRate =
            16_000

        val samples =
            concatenate(
                tone(sampleRate, 1.0, 220.0),
                silence(sampleRate, 0.70),
                tone(sampleRate, 1.0, 260.0)
            )

        val plan =
            TranscriptionAudioPlanner()
                .plan(
                    wavFile =
                        placeholderWav(),
                    samples =
                        samples,
                    sampleRate =
                        sampleRate
                )

        assertTrue(
            "Synthetic voiced material must be recognized as activity",
            plan.speechDetected
        )

        assertTrue(
            "A sub-1.25 second conversational pause must not be collapsed",
            plan.inferenceSeconds >=
                2.55
        )
    }

    @Test
    fun reducesOnlyLongDeadAirAndKeepsContext() {
        val sampleRate =
            16_000

        val samples =
            concatenate(
                tone(sampleRate, 1.0, 210.0),
                silence(sampleRate, 3.0),
                tone(sampleRate, 1.0, 280.0)
            )

        val plan =
            TranscriptionAudioPlanner()
                .plan(
                    wavFile =
                        placeholderWav(),
                    samples =
                        samples,
                    sampleRate =
                        sampleRate
                )

        assertTrue(
            "Long dead air should be shortened before Whisper inference",
            plan.deadAirSkippedSeconds >
                1.0
        )

        assertTrue(
            "Compaction must preserve context and a nonzero inter-phrase pause",
            plan.inferenceSeconds >
                3.0
        )
    }

    private fun placeholderWav(): File {
        val directory =
            Files.createTempDirectory(
                "bci-transcription-test"
            ).toFile()

        return File(
            directory,
            "test_isolated.wav"
        )
    }

    private fun tone(
        sampleRate: Int,
        seconds: Double,
        frequencyHz: Double
    ): ShortArray {
        val count =
            (
                sampleRate *
                    seconds
                )
                .toInt()

        return ShortArray(
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
    }

    private fun silence(
        sampleRate: Int,
        seconds: Double
    ): ShortArray =
        ShortArray(
            (
                sampleRate *
                    seconds
                )
                .toInt()
        )

    private fun concatenate(
        vararg arrays: ShortArray
    ): ShortArray {
        val total =
            arrays.sumOf {
                it.size
            }

        val out =
            ShortArray(
                total
            )

        var cursor = 0

        arrays.forEach { array ->
            array.copyInto(
                out,
                cursor
            )

            cursor +=
                array.size
        }

        return out
    }
}
