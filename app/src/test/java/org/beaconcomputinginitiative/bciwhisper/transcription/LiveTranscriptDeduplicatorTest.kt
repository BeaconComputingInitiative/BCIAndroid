/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Live Transcript De-duplication Regression
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTranscriptDeduplicatorTest {
    @Test
    fun removesRollingWindowOverlap() {
        val dedupe =
            LiveTranscriptDeduplicator()

        assertEquals(
            "the system is capturing audio",
            dedupe.emitNovel(
                "the system is capturing audio"
            )
        )

        assertEquals(
            "in real time",
            dedupe.emitNovel(
                "capturing audio in real time"
            )
        )
    }

    @Test
    fun preservesNewRepeatedWordsOutsideBoundary() {
        val dedupe =
            LiveTranscriptDeduplicator()

        assertEquals(
            "go go now",
            dedupe.emitNovel(
                "go go now"
            )
        )

        assertEquals(
            "go again",
            dedupe.emitNovel(
                "now go again"
            )
        )
    }
}
