/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Whisper JNI Surface
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

package org.beaconcomputinginitiative.bciwhisper.transcription

object WhisperNative {
    init {
        System.loadLibrary("bciwhisper_whisper")
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribe(contextPtr: Long, audio: FloatArray, threads: Int): Int
    external fun segmentCount(contextPtr: Long): Int
    external fun segmentText(contextPtr: Long, index: Int): String
    external fun segmentStart10ms(contextPtr: Long, index: Int): Long
    external fun segmentEnd10ms(contextPtr: Long, index: Int): Long
}
