/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Rolling Live Transcription Engine
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

class LiveTranscriptionEngine(
    context: Context,
    private val sampleRate: Int,
    private val onSegment:
        (Segment) -> Unit,
    private val onError:
        (String) -> Unit = {}
) {
    data class Segment(
        val sequence: Long,
        val startMs: Long,
        val endMs: Long,
        val speaker: String?,
        val text: String,
        val decodeMs: Long
    )

    data class Snapshot(
        val running: Boolean,
        val segmentsEmitted: Long,
        val lastDecodeMs: Long,
        val latestText: String?,
        val skippedWindows: Long,
        val lastError: String?
    )

    private val engine =
        TranscriptionEngine(
            context.applicationContext
        )

    private val deduplicator =
        LiveTranscriptDeduplicator()

    private val lock =
        Object()

    private val windowSamples =
        sampleRate *
            WINDOW_SECONDS

    private val hopSamples =
        sampleRate.toLong() *
            HOP_SECONDS

    private val ring =
        ShortArray(
            windowSamples
        )

    private var writeIndex =
        0

    private var size =
        0

    private var totalFrames =
        0L

    private var lastProcessedEndFrame =
        0L

    private var generation =
        0L

    private var workerEpoch =
        0L

    @Volatile
    private var running =
        false

    @Volatile
    private var worker:
        Thread? =
        null

    private val sequence =
        AtomicLong(
            0L
        )

    @Volatile
    private var segmentsEmitted =
        0L

    @Volatile
    private var lastDecodeMs =
        0L

    @Volatile
    private var latestText:
        String? =
        null

    @Volatile
    private var skippedWindows =
        0L

    @Volatile
    private var lastError:
        String? =
        null

    fun start() {
        synchronized(
            lock
        ) {
            if (running) {
                return
            }

            running =
                true

            workerEpoch +=
                1L

            resetLocked(
                resetSequence =
                    true
            )
        }

        val epoch =
            synchronized(
                lock
            ) {
                workerEpoch
            }

        worker =
            Thread(
                {
                    workerLoop(
                        epoch
                    )
                },
                "BCI-LiveTranscript"
            ).apply {
                priority =
                    Thread.MIN_PRIORITY

                start()
            }
    }

    fun stop() {
        synchronized(
            lock
        ) {
            if (!running) {
                return
            }

            running =
                false

            generation +=
                1L

            workerEpoch +=
                1L

            lock.notifyAll()
        }

        worker =
            null
    }

    fun resetStream() {
        synchronized(
            lock
        ) {
            resetLocked(
                resetSequence =
                    true
            )

            lock.notifyAll()
        }
    }

    fun offer(
        samples: ShortArray,
        count: Int
    ) {
        val n =
            count
                .coerceAtMost(
                    samples.size
                )
                .coerceAtLeast(
                    0
                )

        if (
            !running ||
            n <=
                0
        ) {
            return
        }

        synchronized(
            lock
        ) {
            if (!running) {
                return
            }

            for (
                index in
                0 until n
            ) {
                ring[
                    writeIndex
                ] =
                    samples[
                        index
                    ]

                writeIndex =
                    (
                        writeIndex +
                            1
                        ) %
                        ring.size

                if (
                    size <
                    ring.size
                ) {
                    size +=
                        1
                }
            }

            totalFrames +=
                n.toLong()

            if (
                readyLocked()
            ) {
                lock.notifyAll()
            }
        }
    }

    fun snapshot():
        Snapshot =
        Snapshot(
            running =
                running,
            segmentsEmitted =
                segmentsEmitted,
            lastDecodeMs =
                lastDecodeMs,
            latestText =
                latestText,
            skippedWindows =
                skippedWindows,
            lastError =
                lastError
        )

    private fun workerLoop(
        epoch: Long
    ) {
        try {
            check(
                engine.preload()
            ) {
                "whisper.cpp model preload failed"
            }

            while (true) {
                val window:
                    Window =
                    synchronized(
                        lock
                    ) {
                        while (
                            running &&
                            epoch ==
                                workerEpoch &&
                            !readyLocked()
                        ) {
                            lock.wait(
                                250L
                            )
                        }

                        if (
                            !running ||
                            epoch !=
                                workerEpoch
                        ) {
                            return
                        }

                        snapshotWindowLocked()
                    }

                val result =
                    try {
                        engine.transcribeLivePcm(
                            pcm =
                                window.samples,
                            sourceRate =
                                sampleRate,
                            resourceMode =
                                TranscriptionEngine
                                    .ResourceMode
                                    .CAPTURE_CRITICAL
                        )
                    } catch (
                        t: Throwable
                    ) {
                        val message =
                            t.message
                                ?: t::class
                                    .simpleName
                                ?: "live transcription error"

                        lastError =
                            message

                        onError(
                            message
                        )

                        continue
                    }

                lastDecodeMs =
                    result.decodeMs

                val stillCurrent =
                    synchronized(
                        lock
                    ) {
                        running &&
                            epoch ==
                                workerEpoch &&
                            window.generation ==
                                generation
                    }

                if (!stillCurrent) {
                    val workerStillCurrent =
                        synchronized(
                            lock
                        ) {
                            running &&
                                epoch ==
                                    workerEpoch
                        }

                    if (!workerStillCurrent) {
                        return
                    }

                    continue
                }

                result.segments
                    .forEach {
                            segment ->

                        val novel =
                            deduplicator
                                .emitNovel(
                                    segment.text
                                )

                        if (
                            novel.isBlank()
                        ) {
                            return@forEach
                        }

                        val baseStartMs =
                            framesToMs(
                                window.startFrame
                            )

                        val startMs =
                            baseStartMs +
                                segment.start10ms *
                                    10L

                        val endMs =
                            baseStartMs +
                                segment.end10ms *
                                    10L

                        val output =
                            Segment(
                                sequence =
                                    sequence
                                        .incrementAndGet(),
                                startMs =
                                    startMs
                                        .coerceAtLeast(
                                            0L
                                        ),
                                endMs =
                                    endMs
                                        .coerceAtLeast(
                                            startMs
                                        ),
                                speaker =
                                    "Speaker " +
                                        segment.speaker,
                                text =
                                    novel,
                                decodeMs =
                                    result.decodeMs
                            )

                        latestText =
                            novel

                        segmentsEmitted +=
                            1L

                        onSegment(
                            output
                        )
                    }
            }
        } catch (
            t: Throwable
        ) {
            val message =
                t.message
                    ?: t::class
                        .simpleName
                    ?: "live transcription worker failed"

            lastError =
                message

            onError(
                message
            )
        }
    }

    private fun readyLocked():
        Boolean {
        if (
            size <
            windowSamples
        ) {
            return false
        }

        return (
            totalFrames -
                lastProcessedEndFrame
            ) >=
            hopSamples
    }

    private fun snapshotWindowLocked():
        Window {
        val endFrame =
            totalFrames

        if (
            lastProcessedEndFrame >
                0L
        ) {
            val elapsed =
                endFrame -
                    lastProcessedEndFrame

            if (
                elapsed >=
                hopSamples *
                    2L
            ) {
                skippedWindows +=
                    (
                        elapsed /
                            hopSamples -
                            1L
                        )
                        .coerceAtLeast(
                            0L
                        )
            }
        }

        val samples =
            ShortArray(
                windowSamples
            )

        var source =
            writeIndex -
                windowSamples

        if (
            source <
            0
        ) {
            source +=
                ring.size
        }

        for (
            index in
            samples.indices
        ) {
            samples[
                index
            ] =
                ring[
                    source
                ]

            source =
                (
                    source +
                        1
                    ) %
                    ring.size
        }

        lastProcessedEndFrame =
            endFrame

        return Window(
            samples =
                samples,
            startFrame =
                endFrame -
                    windowSamples,
            endFrame =
                endFrame,
            generation =
                generation
        )
    }

    private fun resetLocked(
        resetSequence: Boolean
    ) {
        writeIndex =
            0

        size =
            0

        totalFrames =
            0L

        lastProcessedEndFrame =
            -hopSamples

        generation +=
            1L

        deduplicator.reset()

        latestText =
            null

        lastDecodeMs =
            0L

        skippedWindows =
            0L

        lastError =
            null

        if (resetSequence) {
            sequence.set(
                0L
            )

            segmentsEmitted =
                0L
        }
    }

    private fun framesToMs(
        frames: Long
    ): Long =
        (
            frames *
                1000L
            ) /
            sampleRate

    private data class Window(
        val samples: ShortArray,
        val startFrame: Long,
        val endFrame: Long,
        val generation: Long
    )

    companion object {
        private const val WINDOW_SECONDS =
            3

        private const val HOP_SECONDS =
            2

    }
}
