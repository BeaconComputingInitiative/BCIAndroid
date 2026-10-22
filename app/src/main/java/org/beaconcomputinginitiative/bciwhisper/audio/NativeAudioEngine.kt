/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Native Oboe Audio Surface
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.audio

object NativeAudioEngine {
    init {
        System.loadLibrary("bciwhisper_whisper")
    }

    external fun startCapture(
        sampleRate: Int,
        deviceId: Int,
        communicationPreset: Boolean
    ): Boolean

    external fun stopCapture()

    external fun readPcm16(
        destination: ShortArray,
        requestedFrames: Int,
        timeoutMs: Int
    ): Int

    external fun captureActive(): Boolean
    external fun actualSampleRate(): Int
    external fun framesPerBurst(): Int
    external fun xRunCount(): Int
    external fun sessionId(): Int
    external fun audioApi(): Int
    external fun droppedFrames(): Long
}
