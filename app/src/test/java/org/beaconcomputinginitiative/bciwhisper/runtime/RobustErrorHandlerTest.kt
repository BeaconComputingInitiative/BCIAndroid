/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Robust Error Handler Regression Tests
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustErrorHandlerTest {
    @Test
    fun rootCauseIsPreservedAcrossSecondaryFailures() {
        val handler =
            RobustErrorHandler()

        handler.completeCheck(
            listOf(
                RobustErrorHandler.Observation(
                    code =
                        "BCI_E_AUDIO_INACTIVE",
                    module =
                        "native.audio",
                    severity =
                        RobustErrorHandler
                            .Severity.FATAL,
                    message =
                        "native stream stopped",
                    fatal =
                        true
                )
            )
        )

        handler.completeCheck(
            listOf(
                RobustErrorHandler.Observation(
                    code =
                        "BCI_E_BROADCAST",
                    module =
                        "kotlin.broadcast",
                    severity =
                        RobustErrorHandler
                            .Severity.ERROR,
                    message =
                        "socket closed",
                    fatal =
                        false
                )
            )
        )

        val snapshot =
            handler.snapshot()

        assertEquals(
            "BCI_E_AUDIO_INACTIVE",
            snapshot.rootFault?.code
        )

        assertEquals(
            "BCI_E_BROADCAST",
            snapshot.lastFault?.code
        )
    }

    @Test
    fun repeatedFaultsAreCoalescedAndCounted() {
        val handler =
            RobustErrorHandler()

        repeat(
            4
        ) {
            handler.completeCheck(
                listOf(
                    RobustErrorHandler.Observation(
                        code =
                            "BCI_W_AUDIO_XRUN",
                        module =
                            "native.audio",
                        severity =
                            RobustErrorHandler
                                .Severity.WARNING,
                        message =
                            "xRun observed",
                        fatal =
                            false
                    )
                )
            )
        }

        val snapshot =
            handler.snapshot()

        assertEquals(
            1,
            snapshot.recentFaults.size
        )

        assertEquals(
            4L,
            snapshot.recentFaults
                .single()
                .occurrences
        )

        assertEquals(
            RobustErrorHandler.Status.DEGRADED,
            snapshot.status
        )
    }

    @Test
    fun healthCanRecoverWithoutLosingRootHistory() {
        val handler =
            RobustErrorHandler()

        handler.completeCheck(
            listOf(
                RobustErrorHandler.Observation(
                    code =
                        "BCI_E_LIVE_TRANSCRIPTION",
                    module =
                        "kotlin.transcription",
                    severity =
                        RobustErrorHandler
                            .Severity.ERROR,
                    message =
                        "caption worker failed",
                    fatal =
                        false
                )
            )
        )

        handler.completeCheck(
            emptyList()
        )

        val snapshot =
            handler.snapshot()

        assertEquals(
            RobustErrorHandler.Status.HEALTHY,
            snapshot.status
        )

        assertNotNull(
            snapshot.rootFault
        )

        assertTrue(
            snapshot.healthyChecks >
                0L
        )
    }
}
