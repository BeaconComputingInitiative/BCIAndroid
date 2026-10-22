/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Native Transcription Engine Surface
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

package org.beaconcomputinginitiative.bciwhisper.transcription

/**
 * Single JNI boundary for the native transcription hot path.
 *
 * Native code owns the whisper.cpp context and computationally hot sample
 * preparation. Kotlin does not own or pass native context pointers.
 */
object NativeTranscriptionEngine {
    data class Quality(
        val noiseFloorDbfs: Double,
        val speechLevelDbfs: Double,
        val contrastDb: Double,
        val activeRatio: Double,
        val reconstruct: Boolean
    )

    init {
        System.loadLibrary(
            "bciwhisper_whisper"
        )
    }

    external fun warmModel(
        modelPath: String
    ): Boolean

    external fun resamplePcm16To16k(
        input: ShortArray,
        sourceRate: Int
    ): FloatArray

    private external fun assessQuality(
        audio: FloatArray,
        sampleRate: Int
    ): DoubleArray

    external fun transcribe(
        audio: FloatArray,
        threads: Int
    ): Int

    external fun segmentCount(): Int

    external fun segmentText(
        index: Int
    ): String

    external fun segmentStart10ms(
        index: Int
    ): Long

    external fun segmentEnd10ms(
        index: Int
    ): Long

    external fun shutdown()

    fun quality(
        audio: FloatArray,
        sampleRate: Int = 16_000
    ): Quality {
        val values =
            assessQuality(
                audio,
                sampleRate
            )

        return Quality(
            noiseFloorDbfs =
                values.getOrElse(0) {
                    -120.0
                },
            speechLevelDbfs =
                values.getOrElse(1) {
                    -120.0
                },
            contrastDb =
                values.getOrElse(2) {
                    0.0
                },
            activeRatio =
                values.getOrElse(3) {
                    0.0
                },
            reconstruct =
                values.getOrElse(4) {
                    1.0
                } >=
                    0.5
        )
    }
}
