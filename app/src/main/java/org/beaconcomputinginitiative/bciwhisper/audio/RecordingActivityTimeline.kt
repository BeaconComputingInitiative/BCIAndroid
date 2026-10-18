/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Recording Activity Timeline
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.audio

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Builds a non-destructive activity map alongside each preserved recording.
 *
 * The WAV remains untouched. This timeline tells downstream transcription
 * which regions contain meaningful acoustic activity and which regions are
 * dead air that can be compacted before inference.
 */
class RecordingActivityTimeline(
    private val sampleRate: Int
) {
    data class Span(
        val startFrame: Long,
        val endFrame: Long
    )

    private val spans =
        ArrayList<Span>()

    private var cursorFrames = 0L
    private var activeStartFrame: Long? = null
    private var lastActiveFrame = 0L
    private var noiseFloorDbfs = -90.0
    private var noiseFloorInitialized = false

    private val analysisFrames =
        (sampleRate * ANALYSIS_WINDOW_SECONDS)
            .toInt()
            .coerceAtLeast(128)

    private val hangoverFrames =
        (sampleRate * ACTIVITY_HANGOVER_SECONDS)
            .toLong()

    fun reset() {
        spans.clear()
        cursorFrames = 0L
        activeStartFrame = null
        lastActiveFrame = 0L
        noiseFloorDbfs = -90.0
        noiseFloorInitialized = false
    }

    fun process(
        samples: ShortArray,
        count: Int,
        spectralSignalActive: Boolean
    ) {
        val limit =
            count.coerceIn(
                0,
                samples.size
            )

        var offset = 0

        while (offset < limit) {
            val chunk =
                minOf(
                    analysisFrames,
                    limit - offset
                )

            val dbfs =
                rmsDbfs(
                    samples,
                    offset,
                    chunk
                )

            if (!noiseFloorInitialized) {
                noiseFloorDbfs = dbfs
                noiseFloorInitialized = true
            }

            val threshold =
                noiseFloorDbfs +
                    if (spectralSignalActive) {
                        ACTIVE_MARGIN_WHILE_LOCKED_DB
                    } else {
                        ACTIVE_MARGIN_IDLE_DB
                    }

            val energyActive =
                dbfs >= threshold &&
                    dbfs >= ABSOLUTE_MIN_ACTIVITY_DBFS

            val chunkStart =
                cursorFrames

            val chunkEnd =
                cursorFrames + chunk

            if (energyActive) {
                if (activeStartFrame == null) {
                    activeStartFrame =
                        chunkStart
                }

                lastActiveFrame =
                    chunkEnd
            } else {
                val withinHangover =
                    activeStartFrame != null &&
                        chunkStart -
                            lastActiveFrame <=
                        hangoverFrames

                if (!withinHangover) {
                    closeActiveSpan(
                        lastActiveFrame
                    )
                }

                if (!spectralSignalActive) {
                    val delta =
                        (dbfs - noiseFloorDbfs)
                            .coerceIn(
                                -NOISE_FLOOR_MAX_STEP_DB,
                                NOISE_FLOOR_MAX_STEP_DB
                            )

                    noiseFloorDbfs +=
                        NOISE_FLOOR_SMOOTHING *
                            delta
                }
            }

            cursorFrames =
                chunkEnd

            offset += chunk
        }
    }

    fun writeManifest(
        isolatedFile: File,
        totalFrames: Long,
        lockedFrames: Long,
        reason: String
    ): File {
        closeActiveSpan(
            cursorFrames
        )

        val manifest =
            File(
                isolatedFile.parentFile,
                isolatedFile.nameWithoutExtension +
                    ".activity.json"
            )

        val spansJson =
            JSONArray()

        var activityFrames = 0L

        spans.forEach { span ->
            val duration =
                (span.endFrame - span.startFrame)
                    .coerceAtLeast(0L)

            if (
                duration <
                (sampleRate *
                    MIN_ACTIVITY_SPAN_SECONDS)
                    .toLong()
            ) {
                return@forEach
            }

            activityFrames += duration

            spansJson.put(
                JSONObject()
                    .put(
                        "startFrame",
                        span.startFrame
                    )
                    .put(
                        "endFrame",
                        span.endFrame
                    )
            )
        }

        val deadAirFrames =
            (
                totalFrames -
                    activityFrames
                )
                .coerceAtLeast(0L)

        val root =
            JSONObject()
                .put(
                    "schema",
                    "bci-recording-activity-v1"
                )
                .put(
                    "sampleRate",
                    sampleRate
                )
                .put(
                    "totalFrames",
                    totalFrames
                )
                .put(
                    "lockedFrames",
                    lockedFrames
                )
                .put(
                    "activityFrames",
                    activityFrames
                )
                .put(
                    "deadAirFrames",
                    deadAirFrames
                )
                .put(
                    "noiseFloorDbfs",
                    noiseFloorDbfs
                )
                .put(
                    "reason",
                    reason
                )
                .put(
                    "spans",
                    spansJson
                )

        manifest.writeText(
            root.toString()
        )

        return manifest
    }

    private fun closeActiveSpan(
        requestedEndFrame: Long
    ) {
        val start =
            activeStartFrame
                ?: return

        val end =
            maxOf(
                start,
                minOf(
                    requestedEndFrame,
                    lastActiveFrame +
                        hangoverFrames
                )
            )

        if (end > start) {
            val previous =
                spans.lastOrNull()

            if (
                previous != null &&
                start -
                    previous.endFrame <=
                (
                    sampleRate *
                        MERGE_GAP_SECONDS
                    ).toLong()
            ) {
                spans[spans.lastIndex] =
                    Span(
                        previous.startFrame,
                        maxOf(
                            previous.endFrame,
                            end
                        )
                    )
            } else {
                spans +=
                    Span(
                        start,
                        end
                    )
            }
        }

        activeStartFrame = null
    }

    private fun rmsDbfs(
        samples: ShortArray,
        offset: Int,
        count: Int
    ): Double {
        if (count <= 0) {
            return -120.0
        }

        var sumSq = 0.0

        for (i in 0 until count) {
            val value =
                samples[offset + i]
                    .toDouble() /
                    32768.0

            sumSq +=
                value * value
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
        private const val ANALYSIS_WINDOW_SECONDS =
            0.020

        private const val ACTIVITY_HANGOVER_SECONDS =
            0.35

        private const val MERGE_GAP_SECONDS =
            0.45

        private const val MIN_ACTIVITY_SPAN_SECONDS =
            0.20

        private const val ACTIVE_MARGIN_WHILE_LOCKED_DB =
            4.0

        private const val ACTIVE_MARGIN_IDLE_DB =
            6.0

        private const val ABSOLUTE_MIN_ACTIVITY_DBFS =
            -90.0

        private const val NOISE_FLOOR_SMOOTHING =
            0.025

        private const val NOISE_FLOOR_MAX_STEP_DB =
            8.0
    }
}
