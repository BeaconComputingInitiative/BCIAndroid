/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Transcription Audio Planner
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.json.JSONObject
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Converts preserved recording audio into a transcription-only inference stream.
 *
 * Long dead-air intervals are removed only from the inference copy. Original
 * WAV files remain byte-for-byte untouched. Short pauses are retained and
 * long gaps are reduced conservatively so phoneme boundaries, speech rhythm,
 * stress, and prosodic timing remain useful to the recognizer.
 */
class TranscriptionAudioPlanner {
    data class Plan(
        val samples: ShortArray,
        val sourceSeconds: Double,
        val inferenceSeconds: Double,
        val deadAirSkippedSeconds: Double,
        val activitySeconds: Double,
        val speechDetected: Boolean,
        val usedRecorderTimeline: Boolean
    )

    private data class Range(
        val start: Int,
        val end: Int
    )

    fun plan(
        wavFile: File,
        samples: ShortArray,
        sampleRate: Int
    ): Plan {
        val sourceSeconds =
            samples.size.toDouble() /
                sampleRate

        if (samples.isEmpty()) {
            return Plan(
                samples = ShortArray(0),
                sourceSeconds = 0.0,
                inferenceSeconds = 0.0,
                deadAirSkippedSeconds = 0.0,
                activitySeconds = 0.0,
                speechDetected = false,
                usedRecorderTimeline = false
            )
        }

        val manifestRanges =
            readTimelineRanges(
                wavFile,
                samples.size,
                sampleRate
            )

        val usedRecorderTimeline =
            manifestRanges != null

        val activityRanges =
            manifestRanges
                ?: detectFallbackActivity(
                    samples,
                    sampleRate
                )

        val activityFrames =
            activityRanges.sumOf {
                (
                    it.end -
                        it.start
                    )
                    .coerceAtLeast(0)
            }

        val activitySeconds =
            activityFrames.toDouble() /
                sampleRate

        if (
            activitySeconds <
            MIN_TRANSCRIBABLE_ACTIVITY_SECONDS
        ) {
            return Plan(
                samples = ShortArray(0),
                sourceSeconds = sourceSeconds,
                inferenceSeconds = 0.0,
                deadAirSkippedSeconds = sourceSeconds,
                activitySeconds = activitySeconds,
                speechDetected = false,
                usedRecorderTimeline = usedRecorderTimeline
            )
        }

        val padded =
            padAndMerge(
                activityRanges,
                samples.size,
                sampleRate
            )

        val compacted =
            compact(
                samples,
                padded,
                sampleRate
            )

        val inferenceSeconds =
            compacted.size.toDouble() /
                sampleRate

        return Plan(
            samples = compacted,
            sourceSeconds = sourceSeconds,
            inferenceSeconds =
                inferenceSeconds,
            deadAirSkippedSeconds =
                (
                    sourceSeconds -
                        inferenceSeconds
                    )
                    .coerceAtLeast(0.0),
            activitySeconds =
                activitySeconds,
            speechDetected =
                true,
            usedRecorderTimeline =
                usedRecorderTimeline
        )
    }

    private fun readTimelineRanges(
        wavFile: File,
        sampleCount: Int,
        sampleRate: Int
    ): List<Range>? {
        val manifest =
            File(
                wavFile.parentFile,
                wavFile.nameWithoutExtension +
                    ".activity.json"
            )

        if (!manifest.isFile) {
            return null
        }

        return runCatching {
            val root =
                JSONObject(
                    manifest.readText()
                )

            if (
                root.optString("schema") !=
                "bci-recording-activity-v1"
            ) {
                return@runCatching null
            }

            val manifestRate =
                root.optInt(
                    "sampleRate",
                    sampleRate
                )

            if (manifestRate <= 0) {
                return@runCatching null
            }

            val spans =
                root.optJSONArray(
                    "spans"
                )
                    ?: return@runCatching emptyList()

            val ratio =
                sampleRate.toDouble() /
                    manifestRate

            val out =
                ArrayList<Range>()

            for (i in 0 until spans.length()) {
                val span =
                    spans.optJSONObject(i)
                        ?: continue

                val start =
                    (
                        span.optLong(
                            "startFrame",
                            0L
                        ) *
                            ratio
                        )
                        .toInt()
                        .coerceIn(
                            0,
                            sampleCount
                        )

                val end =
                    (
                        span.optLong(
                            "endFrame",
                            0L
                        ) *
                            ratio
                        )
                        .toInt()
                        .coerceIn(
                            start,
                            sampleCount
                        )

                if (end > start) {
                    out +=
                        Range(
                            start,
                            end
                        )
                }
            }

            out
        }.getOrNull()
    }

    private fun detectFallbackActivity(
        samples: ShortArray,
        sampleRate: Int
    ): List<Range> {
        val frameSize =
            (
                sampleRate *
                    FALLBACK_WINDOW_SECONDS
                )
                .toInt()
                .coerceAtLeast(128)

        val levels =
            ArrayList<Double>()

        var offset = 0

        while (offset < samples.size) {
            val count =
                minOf(
                    frameSize,
                    samples.size - offset
                )

            levels +=
                rmsDbfs(
                    samples,
                    offset,
                    count
                )

            offset += count
        }

        if (levels.isEmpty()) {
            return emptyList()
        }

        val sorted =
            levels.sorted()

        val floorIndex =
            (
                (sorted.size - 1) *
                    0.30
                )
                .toInt()
                .coerceIn(
                    0,
                    sorted.lastIndex
                )

        val floor =
            sorted[floorIndex]

        val threshold =
            maxOf(
                floor +
                    FALLBACK_ACTIVITY_MARGIN_DB,
                FALLBACK_ABSOLUTE_MIN_DBFS
            )

        val active =
            BooleanArray(
                levels.size
            )

        levels.forEachIndexed { index, level ->
            active[index] =
                level >= threshold
        }

        val hangoverFrames =
            (
                FALLBACK_HANGOVER_SECONDS /
                    FALLBACK_WINDOW_SECONDS
                )
                .toInt()
                .coerceAtLeast(1)

        var hold = 0

        for (i in active.indices) {
            if (active[i]) {
                hold =
                    hangoverFrames
            } else if (hold > 0) {
                active[i] = true
                hold--
            }
        }

        val ranges =
            ArrayList<Range>()

        var startFrame: Int? = null

        for (i in active.indices) {
            if (
                active[i] &&
                startFrame == null
            ) {
                startFrame = i
            }

            val closes =
                startFrame != null &&
                    (
                        !active[i] ||
                            i ==
                            active.lastIndex
                        )

            if (closes) {
                val endFrame =
                    if (
                        active[i] &&
                        i ==
                        active.lastIndex
                    ) {
                        i + 1
                    } else {
                        i
                    }

                val start =
                    (
                        startFrame!! *
                            frameSize
                        )
                        .coerceAtMost(
                            samples.size
                        )

                val end =
                    (
                        endFrame *
                            frameSize
                        )
                        .coerceAtMost(
                            samples.size
                        )

                if (
                    end - start >=
                    (
                        sampleRate *
                            MIN_TRANSCRIBABLE_SPAN_SECONDS
                        ).toInt()
                ) {
                    ranges +=
                        Range(
                            start,
                            end
                        )
                }

                startFrame = null
            }
        }

        return ranges
    }

    private fun padAndMerge(
        ranges: List<Range>,
        sampleCount: Int,
        sampleRate: Int
    ): List<Range> {
        if (ranges.isEmpty()) {
            return emptyList()
        }

        val pad =
            (
                sampleRate *
                    CONTEXT_PADDING_SECONDS
                )
                .toInt()

        val mergeGap =
            (
                sampleRate *
                    MERGE_GAP_SECONDS
                )
                .toInt()

        val expanded =
            ranges
                .map {
                    Range(
                        start =
                            (it.start - pad)
                                .coerceAtLeast(0),
                        end =
                            (it.end + pad)
                                .coerceAtMost(
                                    sampleCount
                                )
                    )
                }
                .sortedBy {
                    it.start
                }

        val merged =
            ArrayList<Range>()

        expanded.forEach { candidate ->
            val last =
                merged.lastOrNull()

            if (
                last != null &&
                candidate.start -
                    last.end <=
                mergeGap
            ) {
                merged[merged.lastIndex] =
                    Range(
                        last.start,
                        maxOf(
                            last.end,
                            candidate.end
                        )
                    )
            } else {
                merged += candidate
            }
        }

        return merged
    }

    private fun compact(
        source: ShortArray,
        ranges: List<Range>,
        sampleRate: Int
    ): ShortArray {
        if (ranges.isEmpty()) {
            return ShortArray(0)
        }

        if (
            ranges.size == 1 &&
            ranges[0].start == 0 &&
            ranges[0].end >=
                source.size
        ) {
            return source.copyOf()
        }

        val separatorFrames =
            (
                sampleRate *
                    SEGMENT_SEPARATOR_SECONDS
                )
                .toInt()

        val payloadFrames =
            ranges.sumOf {
                it.end - it.start
            }

        val totalFrames =
            payloadFrames +
                separatorFrames *
                    (ranges.size - 1)

        val out =
            ShortArray(
                totalFrames
            )

        var cursor = 0

        ranges.forEachIndexed { index, range ->
            val length =
                range.end -
                    range.start

            source.copyInto(
                out,
                destinationOffset = cursor,
                startIndex = range.start,
                endIndex = range.end
            )

            cursor += length

            if (
                index <
                ranges.lastIndex
            ) {
                cursor +=
                    separatorFrames
            }
        }

        return out
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
        private const val CONTEXT_PADDING_SECONDS =
            0.45

        private const val MERGE_GAP_SECONDS =
            1.25

        private const val SEGMENT_SEPARATOR_SECONDS =
            0.65

        private const val MIN_TRANSCRIBABLE_ACTIVITY_SECONDS =
            0.40

        private const val MIN_TRANSCRIBABLE_SPAN_SECONDS =
            0.20

        private const val FALLBACK_WINDOW_SECONDS =
            0.030

        private const val FALLBACK_HANGOVER_SECONDS =
            0.30

        private const val FALLBACK_ACTIVITY_MARGIN_DB =
            6.0

        private const val FALLBACK_ABSOLUTE_MIN_DBFS =
            -90.0
    }
}
