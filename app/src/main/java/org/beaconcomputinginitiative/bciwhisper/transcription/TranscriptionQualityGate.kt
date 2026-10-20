/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Transcription Quality Gate
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cheap O(n) quality estimate used only to decide whether the expensive
 * Wiener/LPC reconstruction stage is likely to add value.
 *
 * The three-stage phonetic pipeline remains available. Clean isolated speech
 * can bypass redundant offline reconstruction; noisy/low-contrast speech
 * still receives the full Wiener + harmonic + LPC/formant path.
 */
object TranscriptionQualityGate {
    data class Assessment(
        val noiseFloorDbfs: Double,
        val speechLevelDbfs: Double,
        val contrastDb: Double,
        val activeRatio: Double,
        val reconstruct: Boolean
    )

    fun assess(
        samples: FloatArray,
        sampleRate: Int
    ): Assessment {
        if (samples.isEmpty()) {
            return Assessment(
                noiseFloorDbfs = -120.0,
                speechLevelDbfs = -120.0,
                contrastDb = 0.0,
                activeRatio = 0.0,
                reconstruct = false
            )
        }

        val frameSize =
            (
                sampleRate *
                    FRAME_SECONDS
                )
                .toInt()
                .coerceAtLeast(128)

        val frameCount =
            (
                samples.size +
                    frameSize -
                    1
                ) /
                frameSize

        val levels =
            DoubleArray(
                frameCount
            )

        var frame =
            0

        var offset =
            0

        while (offset < samples.size) {
            val count =
                minOf(
                    frameSize,
                    samples.size - offset
                )

            levels[frame] =
                rmsDbfs(
                    samples,
                    offset,
                    count
                )

            frame++
            offset +=
                count
        }

        levels.sort()

        val noise =
            percentileSorted(
                levels,
                NOISE_PERCENTILE
            )

        val speech =
            percentileSorted(
                levels,
                SPEECH_PERCENTILE
            )

        val contrast =
            (
                speech -
                    noise
                )
                .coerceAtLeast(0.0)

        val activityThreshold =
            maxOf(
                noise +
                    ACTIVE_MARGIN_DB,
                MIN_ACTIVE_DBFS
            )

        var active =
            0

        for (level in levels) {
            if (
                level >=
                activityThreshold
            ) {
                active++
            }
        }

        val activeRatio =
            active.toDouble() /
                levels.size
                    .coerceAtLeast(1)

        val reconstruct =
            noise >
                MAX_CLEAN_NOISE_FLOOR_DBFS ||
                contrast <
                MIN_CLEAN_CONTRAST_DB ||
                speech <
                MIN_CLEAN_SPEECH_LEVEL_DBFS

        return Assessment(
            noiseFloorDbfs = noise,
            speechLevelDbfs = speech,
            contrastDb = contrast,
            activeRatio = activeRatio,
            reconstruct = reconstruct
        )
    }

    private fun percentileSorted(
        values: DoubleArray,
        q: Double
    ): Double {
        if (values.isEmpty()) {
            return -120.0
        }

        val index =
            (
                (
                    values.size -
                        1
                    ) *
                    q
                )
                .toInt()
                .coerceIn(
                    0,
                    values.lastIndex
                )

        return values[
            index
        ]
    }

    private fun rmsDbfs(
        samples: FloatArray,
        offset: Int,
        count: Int
    ): Double {
        if (count <= 0) {
            return -120.0
        }

        var sumSq =
            0.0

        for (i in 0 until count) {
            val x =
                samples[
                    offset + i
                ]
                    .toDouble()

            sumSq +=
                x * x
        }

        val rms =
            sqrt(
                sumSq /
                    count
            )

        return if (rms > 1e-12) {
            20.0 *
                log10(rms)
        } else {
            -120.0
        }
    }

    companion object {
        private const val FRAME_SECONDS =
            0.020

        private const val NOISE_PERCENTILE =
            0.12

        private const val SPEECH_PERCENTILE =
            0.78

        private const val ACTIVE_MARGIN_DB =
            6.0

        private const val MIN_ACTIVE_DBFS =
            -55.0

        private const val MAX_CLEAN_NOISE_FLOOR_DBFS =
            -42.0

        private const val MIN_CLEAN_CONTRAST_DB =
            10.0

        private const val MIN_CLEAN_SPEECH_LEVEL_DBFS =
            -38.0
    }
}
