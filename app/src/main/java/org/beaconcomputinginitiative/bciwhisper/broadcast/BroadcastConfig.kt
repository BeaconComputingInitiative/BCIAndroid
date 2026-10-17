/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Live Broadcast Configuration
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.broadcast

object BroadcastConfig {
    const val PUBLIC_PLAYER_URL =
        "https://bciandroid-live.onrender.com/"

    const val STREAM_URL =
        "https://bciandroid-live.onrender.com/live/bciandroid-main"

    const val PUBLISH_URL =
        "wss://bciandroid-live.onrender.com/api/v1/publish/bciandroid-main"

    const val PUBLISHER_ID =
        "bciwhisper-android"

    const val PROTOCOL =
        "bci-broadcast-v1"

    const val INPUT_CODEC =
        "pcm_s16le"

    const val SAMPLE_RATE =
        44_100

    const val CHANNELS =
        1

    const val BITRATE_KBPS =
        128
}
