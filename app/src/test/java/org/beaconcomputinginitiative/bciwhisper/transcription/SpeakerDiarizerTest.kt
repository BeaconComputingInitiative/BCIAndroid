/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Speaker Diarization Regression Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpeakerDiarizerTest {
    @Test
    fun keepsSimilarTurnsTogetherAndSeparatesDifferentVoiceSignature() {
        val sampleRate =
            16_000

        val audio =
            FloatArray(
                sampleRate *
                    4
            )

        writeVoice(
            audio = audio,
            sampleRate = sampleRate,
            startSeconds = 0.0,
            endSeconds = 1.0,
            components =
                doubleArrayOf(
                    180.0,
                    720.0,
                    1300.0,
                    2600.0
                )
        )

        writeVoice(
            audio = audio,
            sampleRate = sampleRate,
            startSeconds = 1.2,
            endSeconds = 2.2,
            components =
                doubleArrayOf(
                    180.0,
                    720.0,
                    1300.0,
                    2600.0
                )
        )

        writeVoice(
            audio = audio,
            sampleRate = sampleRate,
            startSeconds = 2.4,
            endSeconds = 3.4,
            components =
                doubleArrayOf(
                    310.0,
                    1050.0,
                    2100.0,
                    4300.0
                )
        )

        val segments =
            listOf(
                SpeakerDiarizer.Segment(
                    text = "first turn",
                    start10ms = 0,
                    end10ms = 100
                ),
                SpeakerDiarizer.Segment(
                    text = "second turn",
                    start10ms = 120,
                    end10ms = 220
                ),
                SpeakerDiarizer.Segment(
                    text = "different turn",
                    start10ms = 240,
                    end10ms = 340
                )
            )

        val labeled =
            SpeakerDiarizer(
                sampleRate
            )
                .label(
                    audio,
                    segments
                )

        assertTrue(
            "Diarization should retain all turns",
            labeled.size >=
                3
        )

        assertEquals(
            "Acoustically similar turns should share a speaker label",
            labeled[0].speaker,
            labeled[1].speaker
        )

        assertTrue(
            "Clearly different acoustic signatures should not collapse into one speaker",
            labeled[2].speaker !=
                labeled[0].speaker
        )
    }

    private fun writeVoice(
        audio: FloatArray,
        sampleRate: Int,
        startSeconds: Double,
        endSeconds: Double,
        components: DoubleArray
    ) {
        val start =
            (
                startSeconds *
                    sampleRate
                )
                .toInt()

        val end =
            (
                endSeconds *
                    sampleRate
                )
                .toInt()
                .coerceAtMost(
                    audio.size
                )

        for (
            i in
            start until end
        ) {
            val t =
                i.toDouble() /
                    sampleRate

            var value =
                0.0

            components.forEachIndexed {
                    index,
                    frequency ->

                value +=
                    (
                        0.09 /
                            (
                                index +
                                    1
                                )
                        ) *
                        sin(
                            2.0 *
                                PI *
                                frequency *
                                t
                        )
            }

            audio[i] =
                value
                    .coerceIn(
                        -1.0,
                        1.0
                    )
                    .toFloat()
        }
    }
}
