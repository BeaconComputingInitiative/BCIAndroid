/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Local Speaker Diarizer
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lightweight within-recording speaker clustering.
 *
 * This deliberately produces neutral labels (Speaker 1, Speaker 2, ...)
 * rather than inferring sensitive demographic attributes from a voice.
 *
 * Feature vector:
 * - normalized log energy in eight speech bands
 * - spectral centroid
 * - zero-crossing rate
 *
 * Very short turns inherit the previous speaker to prevent label chatter.
 */
class SpeakerDiarizer(
    private val sampleRate: Int = 16_000
) {
    data class Segment(
        val text: String,
        val start10ms: Long,
        val end10ms: Long
    )

    data class LabeledSegment(
        val speaker: Int,
        val text: String,
        val start10ms: Long,
        val end10ms: Long
    )

    private data class Cluster(
        val centroid: DoubleArray,
        var count: Int
    )

    fun label(
        audio: FloatArray,
        segments: List<Segment>
    ): List<LabeledSegment> {
        if (segments.isEmpty()) {
            return emptyList()
        }

        val clusters =
            ArrayList<Cluster>()

        val output =
            ArrayList<LabeledSegment>(
                segments.size
            )

        var previousSpeaker =
            1

        segments.forEachIndexed {
                index,
                segment ->

            val start =
                (
                    segment.start10ms *
                        sampleRate /
                        100L
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        audio.size
                    )

            val end =
                (
                    segment.end10ms *
                        sampleRate /
                        100L
                    )
                    .toInt()
                    .coerceIn(
                        start,
                        audio.size
                    )

            val durationSeconds =
                (
                    end -
                        start
                    ).toDouble() /
                    sampleRate

            val feature =
                if (
                    end >
                    start
                ) {
                    featureVector(
                        audio,
                        start,
                        end
                    )
                } else {
                    null
                }

            val speaker =
                when {
                    feature == null ->
                        previousSpeaker

                    durationSeconds <
                        MIN_CLUSTERABLE_SECONDS &&
                        index >
                        0 ->
                        previousSpeaker

                    clusters.isEmpty() -> {
                        clusters +=
                            Cluster(
                                centroid =
                                    feature.copyOf(),
                                count = 1
                            )

                        1
                    }

                    else -> {
                        val nearest =
                            nearestCluster(
                                clusters,
                                feature
                            )

                        if (
                            nearest.second <=
                                CLUSTER_DISTANCE_THRESHOLD ||
                            clusters.size >=
                                MAX_SPEAKERS
                        ) {
                            updateCluster(
                                clusters[
                                    nearest.first
                                ],
                                feature
                            )

                            nearest.first +
                                1
                        } else {
                            clusters +=
                                Cluster(
                                    centroid =
                                        feature.copyOf(),
                                    count = 1
                                )

                            clusters.size
                        }
                    }
                }

            previousSpeaker =
                speaker

            output +=
                LabeledSegment(
                    speaker = speaker,
                    text = segment.text.trim(),
                    start10ms =
                        segment.start10ms,
                    end10ms =
                        segment.end10ms
                )
        }

        return mergeAdjacent(
            output
        )
    }

    private fun nearestCluster(
        clusters: List<Cluster>,
        feature: DoubleArray
    ): Pair<Int, Double> {
        var bestIndex =
            0

        var bestDistance =
            Double.POSITIVE_INFINITY

        clusters.forEachIndexed {
                index,
                cluster ->

            val distance =
                distance(
                    cluster.centroid,
                    feature
                )

            if (
                distance <
                bestDistance
            ) {
                bestDistance =
                    distance

                bestIndex =
                    index
            }
        }

        return bestIndex to
            bestDistance
    }

    private fun updateCluster(
        cluster: Cluster,
        feature: DoubleArray
    ) {
        val nextCount =
            cluster.count +
                1

        for (
            i in
            cluster.centroid.indices
        ) {
            cluster.centroid[i] +=
                (
                    feature[i] -
                        cluster.centroid[i]
                    ) /
                    nextCount
        }

        cluster.count =
            nextCount
    }

    private fun distance(
        a: DoubleArray,
        b: DoubleArray
    ): Double {
        val n =
            minOf(
                a.size,
                b.size
            )

        if (
            n ==
            0
        ) {
            return Double.POSITIVE_INFINITY
        }

        var sum =
            0.0

        for (
            i in
            0 until n
        ) {
            val delta =
                a[i] -
                    b[i]

            sum +=
                delta *
                    delta
        }

        return sqrt(
            sum /
                n
        )
    }

    private fun featureVector(
        audio: FloatArray,
        start: Int,
        end: Int
    ): DoubleArray {
        val bandEnergy =
            DoubleArray(
                BAND_EDGES_HZ.size -
                    1
            )

        var centroidNumerator =
            0.0

        var centroidDenominator =
            0.0

        var zeroCrossings =
            0L

        var sampleCount =
            0L

        var offset =
            start

        var previous =
            audio[start]
                .toDouble()

        while (
            offset <
            end
        ) {
            val frame =
                DoubleArray(
                    FFT_SIZE * 2
                )

            for (
                i in
                0 until FFT_SIZE
            ) {
                val index =
                    offset +
                        i

                val sample =
                    if (
                        index <
                        end
                    ) {
                        audio[index]
                            .toDouble()
                    } else {
                        0.0
                    }

                val window =
                    0.5 -
                        0.5 *
                        cos(
                            2.0 *
                                PI *
                                i /
                                (
                                    FFT_SIZE -
                                        1
                                    )
                        )

                frame[i] =
                    sample *
                        window

                if (
                    index <
                    end
                ) {
                    if (
                        (
                            sample >=
                                0.0
                            ) !=
                            (
                                previous >=
                                    0.0
                                )
                    ) {
                        zeroCrossings++
                    }

                    previous =
                        sample

                    sampleCount++
                }
            }

            DoubleFFT_1D(
                FFT_SIZE.toLong()
            )
                .realForwardFull(
                    frame
                )

            for (
                bin in
                1..
                    FFT_SIZE /
                        2
            ) {
                val re =
                    frame[
                        2 * bin
                    ]

                val im =
                    frame[
                        2 * bin +
                            1
                    ]

                val power =
                    re *
                        re +
                        im *
                            im

                val hz =
                    bin.toDouble() *
                        sampleRate /
                        FFT_SIZE

                if (
                    hz >
                    0.0 &&
                    hz <=
                        8000.0
                ) {
                    centroidNumerator +=
                        hz *
                            power

                    centroidDenominator +=
                        power
                }

                for (
                    band in
                    bandEnergy.indices
                ) {
                    if (
                        hz >=
                            BAND_EDGES_HZ[
                                band
                            ] &&
                        hz <
                            BAND_EDGES_HZ[
                                band +
                                    1
                            ]
                    ) {
                        bandEnergy[band] +=
                            power

                        break
                    }
                }
            }

            offset +=
                HOP_SIZE
        }

        val total =
            bandEnergy.sum()
                .coerceAtLeast(
                    1e-12
                )

        val feature =
            DoubleArray(
                bandEnergy.size +
                    2
            )

        for (
            band in
            bandEnergy.indices
        ) {
            val ratio =
                bandEnergy[band] /
                    total

            feature[band] =
                ln(
                    ratio
                        .coerceAtLeast(
                            1e-9
                        )
                ) /
                    LOG_RATIO_SCALE
        }

        val centroid =
            if (
                centroidDenominator >
                1e-12
            ) {
                centroidNumerator /
                    centroidDenominator
            } else {
                0.0
            }

        feature[
            bandEnergy.size
        ] =
            (
                centroid /
                    8000.0
                )
                .coerceIn(
                    0.0,
                    1.0
                )

        feature[
            bandEnergy.size +
                1
        ] =
            if (
                sampleCount >
                0
            ) {
                zeroCrossings.toDouble() /
                    sampleCount
            } else {
                0.0
            }

        return feature
    }

    private fun mergeAdjacent(
        input: List<LabeledSegment>
    ): List<LabeledSegment> {
        if (
            input.isEmpty()
        ) {
            return input
        }

        val merged =
            ArrayList<LabeledSegment>()

        input.forEach { segment ->
            val previous =
                merged.lastOrNull()

            if (
                previous != null &&
                previous.speaker ==
                    segment.speaker &&
                segment.start10ms -
                    previous.end10ms <=
                    MERGE_ADJACENT_10MS
            ) {
                merged[
                    merged.lastIndex
                ] =
                    previous.copy(
                        text =
                            (
                                previous.text +
                                    " " +
                                    segment.text
                                )
                                .trim(),
                        end10ms =
                            maxOf(
                                previous.end10ms,
                                segment.end10ms
                            )
                    )
            } else {
                merged +=
                    segment
            }
        }

        return merged
    }

    companion object {
        private const val FFT_SIZE =
            512

        private const val HOP_SIZE =
            256

        private const val MIN_CLUSTERABLE_SECONDS =
            0.45

        private const val CLUSTER_DISTANCE_THRESHOLD =
            0.23

        private const val MAX_SPEAKERS =
            4

        private const val MERGE_ADJACENT_10MS =
            35L

        private const val LOG_RATIO_SCALE =
            8.0

        private val BAND_EDGES_HZ =
            doubleArrayOf(
                80.0,
                180.0,
                350.0,
                700.0,
                1200.0,
                2000.0,
                3200.0,
                5000.0,
                8000.0
            )
    }
}
