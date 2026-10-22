/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Fast Dead-Air-Aware Transcription Engine
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

package org.beaconcomputinginitiative.bciwhisper.transcription

import android.content.Context
import android.os.Process
import java.io.File
import java.io.RandomAccessFile

class TranscriptionEngine(
    context: Context
) {
    private val context =
        context.applicationContext

    data class Result(
        val source: File,
        val transcript: File,
        val text: String,
        val threadsUsed: Int,
        val resourceMode: ResourceMode,
        val sourceSeconds: Double,
        val inferenceSeconds: Double,
        val deadAirSkippedSeconds: Double,
        val activitySeconds: Double,
        val speechDetected: Boolean,
        val usedRecorderTimeline: Boolean,
        val reconstructionApplied: Boolean,
        val qualityContrastDb: Double,
        val qualityNoiseFloorDbfs: Double,
        val averageNoiseReductionDb: Double,
        val formantProtectedBins: Long,
        val harmonicProtectedBins: Long,
        val speakerCount: Int,
        val preparationMs: Long,
        val modelSetupMs: Long,
        val reconstructionMs: Long,
        val decodeMs: Long,
        val diarizationMs: Long,
        val totalMs: Long
    )

    enum class ResourceMode {
        CAPTURE_CRITICAL,
        MONITORING,
        EXCLUSIVE
    }

    /**
     * Copies the model once (first install only) and initializes the shared
     * whisper.cpp context before the user requests transcription.
     */
    fun preload(): Boolean {
        val model =
            ensureModel()

        return NativeTranscriptionEngine
            .warmModel(
                model.absolutePath
            )
    }

    fun transcribe(
        wavFile: File,
        resourceMode: ResourceMode
    ): Result {
        require(
            wavFile.isFile
        ) {
            "Recording not found: ${wavFile.name}"
        }

        val totalStart =
            System.nanoTime()

        val preparationStart =
            totalStart

        val pcm =
            readMonoPcm16Wav(
                wavFile
            )

        val plan =
            TranscriptionAudioPlanner()
                .plan(
                    wavFile = wavFile,
                    samples = pcm.samples,
                    sampleRate = pcm.sampleRate
                )

        val outDir =
            File(
                context.filesDir,
                "transcripts"
            ).apply {
                mkdirs()
            }

        val out =
            File(
                outDir,
                wavFile.nameWithoutExtension +
                    ".txt"
            )

        if (!plan.speechDetected) {
            val text =
                "[No intelligible speech detected]"

            out.writeText(
                text + "\n"
            )

            val totalMs =
                elapsedMs(
                    totalStart
                )

            return Result(
                source = wavFile,
                transcript = out,
                text = text,
                threadsUsed = 0,
                resourceMode = resourceMode,
                sourceSeconds = plan.sourceSeconds,
                inferenceSeconds = 0.0,
                deadAirSkippedSeconds =
                    plan.deadAirSkippedSeconds,
                activitySeconds =
                    plan.activitySeconds,
                speechDetected = false,
                usedRecorderTimeline =
                    plan.usedRecorderTimeline,
                reconstructionApplied = false,
                qualityContrastDb = 0.0,
                qualityNoiseFloorDbfs = -120.0,
                averageNoiseReductionDb = 0.0,
                formantProtectedBins = 0L,
                harmonicProtectedBins = 0L,
                speakerCount = 0,
                preparationMs = totalMs,
                modelSetupMs = 0L,
                reconstructionMs = 0L,
                decodeMs = 0L,
                diarizationMs = 0L,
                totalMs = totalMs
            )
        }

        val audio16k =
            NativeTranscriptionEngine
                .resamplePcm16To16k(
                    input = plan.samples,
                    sourceRate = pcm.sampleRate
                )

        val quality =
            NativeTranscriptionEngine
                .quality(
                    audio = audio16k,
                    sampleRate = 16_000
                )

        val preparationMs =
            elapsedMs(
                preparationStart
            )

        val modelStart =
            System.nanoTime()

        val model =
            ensureModel()

        val modelReady =
            NativeTranscriptionEngine
                .warmModel(
                    model.absolutePath
                )

        require(
            modelReady
        ) {
            "whisper.cpp could not load the model"
        }

        val modelSetupMs =
            elapsedMs(
                modelStart
            )

        val reconstructionStart =
            System.nanoTime()

        val reconstruction =
            if (quality.reconstruct) {
                PhoneticReconstructionEngine()
                    .enhance(
                        input = audio16k,
                        sampleRate = 16_000
                    )
            } else {
                null
            }

        val inferenceAudio =
            reconstruction
                ?.samples
                ?: audio16k

        val reconstructionMs =
            elapsedMs(
                reconstructionStart
            )

        // Transcription yields only when a real-time audio path is active.
        // In EXCLUSIVE mode, Whisper receives normal foreground CPU
        // scheduling while nonessential app work is suspended.
        runCatching {
            Process.setThreadPriority(
                when (
                    resourceMode
                ) {
                    ResourceMode.CAPTURE_CRITICAL ->
                        Process.THREAD_PRIORITY_BACKGROUND

                    ResourceMode.MONITORING ->
                        Process.THREAD_PRIORITY_BACKGROUND

                    ResourceMode.EXCLUSIVE ->
                        Process.THREAD_PRIORITY_DEFAULT
                }
            )
        }

        val threads =
            threadBudget(
                resourceMode
            )

        val decodeStart =
            System.nanoTime()

        val rawSegments =
            synchronized(
                nativeLock
            ) {
                val rc =
                    NativeTranscriptionEngine
                        .transcribe(
                            inferenceAudio,
                            threads
                        )

                require(
                    rc == 0
                ) {
                    "whisper.cpp transcription failed: $rc"
                }

                buildList {
                    val count =
                        NativeTranscriptionEngine
                            .segmentCount()

                    for (
                        i in
                        0 until count
                    ) {
                        val segmentText =
                            NativeTranscriptionEngine
                                .segmentText(
                                    i
                                )
                                .trim()

                        if (
                            segmentText.isNotBlank()
                        ) {
                            add(
                                SpeakerDiarizer.Segment(
                                    text =
                                        segmentText,
                                    start10ms =
                                        NativeTranscriptionEngine
                                            .segmentStart10ms(
                                                i
                                            ),
                                    end10ms =
                                        NativeTranscriptionEngine
                                            .segmentEnd10ms(
                                                i
                                            )
                                )
                            )
                        }
                    }
                }
            }

        val decodeMs =
            elapsedMs(
                decodeStart
            )

        val diarizationStart =
            System.nanoTime()

        val labeledSegments =
            SpeakerDiarizer(
                sampleRate = 16_000
            )
                .label(
                    audio =
                        inferenceAudio,
                    segments =
                        rawSegments
                )

        val diarizationMs =
            elapsedMs(
                diarizationStart
            )

        val decoded =
            labeledSegments
                .joinToString(
                    separator = "\n"
                ) {
                    "Speaker ${it.speaker}: ${it.text}"
                }
                .trim()

        val speakerCount =
            labeledSegments
                .maxOfOrNull {
                    it.speaker
                }
                ?: 0

        val text =
            decoded.ifBlank {
                "[No intelligible speech detected]"
            }

        out.writeText(
            text + "\n"
        )

        return Result(
            source = wavFile,
            transcript = out,
            text = text,
            threadsUsed = threads,
            resourceMode = resourceMode,
            sourceSeconds =
                plan.sourceSeconds,
            inferenceSeconds =
                plan.inferenceSeconds,
            deadAirSkippedSeconds =
                plan.deadAirSkippedSeconds,
            activitySeconds =
                plan.activitySeconds,
            speechDetected =
                decoded.isNotBlank(),
            usedRecorderTimeline =
                plan.usedRecorderTimeline,
            reconstructionApplied =
                reconstruction != null,
            qualityContrastDb =
                quality.contrastDb,
            qualityNoiseFloorDbfs =
                quality.noiseFloorDbfs,
            averageNoiseReductionDb =
                reconstruction
                    ?.metrics
                    ?.averageNoiseReductionDb
                    ?: 0.0,
            formantProtectedBins =
                reconstruction
                    ?.metrics
                    ?.formantProtectedBins
                    ?: 0L,
            harmonicProtectedBins =
                reconstruction
                    ?.metrics
                    ?.harmonicProtectedBins
                    ?: 0L,
            speakerCount =
                speakerCount,
            preparationMs =
                preparationMs,
            modelSetupMs =
                modelSetupMs,
            reconstructionMs =
                reconstructionMs,
            decodeMs =
                decodeMs,
            diarizationMs =
                diarizationMs,
            totalMs =
                elapsedMs(
                    totalStart
                )
        )
    }

    fun transcripts(): List<File> =
        File(
            context.filesDir,
            "transcripts"
        )
            .listFiles {
                    f ->
                f.isFile &&
                    f.extension.equals(
                        "txt",
                        true
                    )
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            .orEmpty()

    private fun ensureModel(): File {
        val dir =
            File(
                context.filesDir,
                "models"
            ).apply {
                mkdirs()
            }

        val model =
            File(
                dir,
                MODEL_NAME
            )

        if (
            model.isFile &&
            model.length() >
                1_000_000L
        ) {
            return model
        }

        context.assets
            .open(
                "models/$MODEL_NAME"
            )
            .use {
                    input ->

                model.outputStream()
                    .buffered()
                    .use {
                            output ->

                        input.copyTo(
                            output,
                            MODEL_COPY_BUFFER_BYTES
                        )
                    }
            }

        return model
    }

    private data class Pcm(
        val sampleRate: Int,
        val samples: ShortArray
    )

    /**
     * Reads PCM payload in one buffered operation instead of two
     * RandomAccessFile calls per sample.
     */
    private fun readMonoPcm16Wav(
        file: File
    ): Pcm {
        RandomAccessFile(
            file,
            "r"
        ).use {
                raf ->

            require(
                raf.length() >=
                    44
            ) {
                "WAV is too small"
            }

            raf.seek(
                22
            )

            val channels =
                readShortLE(
                    raf
                )

            val sampleRate =
                readIntLE(
                    raf
                )

            raf.seek(
                34
            )

            val bits =
                readShortLE(
                    raf
                )

            require(
                channels == 1 &&
                    bits == 16
            ) {
                "Expected mono PCM16 WAV"
            }

            raf.seek(
                12
            )

            var dataOffset =
                -1L

            var dataSize =
                0

            while (
                raf.filePointer +
                    8 <=
                raf.length()
            ) {
                val id =
                    ByteArray(
                        4
                    )
                        .also {
                            raf.readFully(
                                it
                            )
                        }
                        .toString(
                            Charsets.US_ASCII
                        )

                val size =
                    readIntLE(
                        raf
                    )

                if (
                    id ==
                    "data"
                ) {
                    dataOffset =
                        raf.filePointer

                    dataSize =
                        size

                    break
                }

                raf.seek(
                    (
                        raf.filePointer +
                            size +
                            (
                                size and
                                    1
                                )
                        )
                        .coerceAtMost(
                            raf.length()
                        )
                )
            }

            require(
                dataOffset >=
                    0
            ) {
                "WAV data chunk missing"
            }

            require(
                dataSize >=
                    0 &&
                    dataSize.toLong() <=
                    raf.length() -
                        dataOffset
            ) {
                "Invalid WAV data chunk"
            }

            raf.seek(
                dataOffset
            )

            val bytes =
                ByteArray(
                    dataSize
                )

            raf.readFully(
                bytes
            )

            val count =
                dataSize /
                    2

            val samples =
                ShortArray(
                    count
                )

            var byteIndex =
                0

            for (
                i in
                0 until count
            ) {
                val lo =
                    bytes[
                        byteIndex
                    ]
                        .toInt() and
                        0xff

                val hi =
                    bytes[
                        byteIndex +
                            1
                    ]
                        .toInt()

                samples[i] =
                    (
                        lo or
                            (
                                hi shl
                                    8
                                )
                        )
                        .toShort()

                byteIndex +=
                    2
            }

            return Pcm(
                sampleRate,
                samples
            )
        }
    }

    private fun readShortLE(
        raf: RandomAccessFile
    ): Int {
        val a =
            raf.readUnsignedByte()

        val b =
            raf.readUnsignedByte()

        return a or
            (
                b shl
                    8
                )
    }

    private fun readIntLE(
        raf: RandomAccessFile
    ): Int {
        val a =
            raf.readUnsignedByte()

        val b =
            raf.readUnsignedByte()

        val c =
            raf.readUnsignedByte()

        val d =
            raf.readUnsignedByte()

        return a or
            (
                b shl
                    8
                ) or
            (
                c shl
                    16
                ) or
            (
                d shl
                    24
                )
    }

    private fun threadBudget(
        mode: ResourceMode
    ): Int {
        val cores =
            Runtime
                .getRuntime()
                .availableProcessors()
                .coerceAtLeast(
                    1
                )

        return when (
            mode
        ) {
            ResourceMode.CAPTURE_CRITICAL ->
                1

            ResourceMode.MONITORING ->
                when {
                    cores >=
                        6 ->
                        2

                    else ->
                        1
                }

            ResourceMode.EXCLUSIVE ->
                maxOf(
                    1,
                    cores -
                        1
                )
        }
    }

    private fun elapsedMs(
        startNanos: Long
    ): Long =
        (
            System.nanoTime() -
                startNanos
            ) /
            1_000_000L

    companion object {
        const val MODEL_NAME =
            "ggml-tiny.en.bin"

        private const val MODEL_COPY_BUFFER_BYTES =
            1024 *
                1024

        private val nativeLock =
            Any()
    }
}
