/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Runtime Health Supervisor
 * Developer Seal: CJB-DEV-2026
 *
 * Contract:
 * - One language-neutral fault vocabulary is used by Kotlin orchestration,
 *   JNI/C++ native audio, live transcription, and broadcast transport.
 * - The supervisor never executes real-time audio work.
 * - Root failure is the first ERROR/FATAL observation in a monitoring session.
 * - Last failure is the most recent distinct observation.
 * - Repeated identical observations are coalesced with an occurrence count.
 * - Current health can recover to HEALTHY while root/last history remains
 *   available for diagnosis until the next explicit session reset.
 */

package org.beaconcomputinginitiative.bciwhisper.runtime

class RuntimeHealthSupervisor {
    enum class Severity {
        INFO,
        WARNING,
        ERROR,
        FATAL
    }

    enum class Status {
        HEALTHY,
        DEGRADED,
        ERROR
    }

    data class Observation(
        val code: String,
        val module: String,
        val severity: Severity,
        val message: String,
        val fatal: Boolean = false
    )

    data class Fault(
        val code: String,
        val module: String,
        val severity: Severity,
        val message: String,
        val fatal: Boolean,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
        val occurrences: Long
    )

    data class Snapshot(
        val status: Status,
        val checks: Long,
        val healthyChecks: Long,
        val degradedChecks: Long,
        val errorChecks: Long,
        val lastCheckMs: Long,
        val rootFault: Fault?,
        val lastFault: Fault?,
        val recentFaults: List<Fault>
    )

    private val lock =
        Any()

    private val recent =
        ArrayDeque<Fault>()

    private var status =
        Status.HEALTHY

    private var checks =
        0L

    private var healthyChecks =
        0L

    private var degradedChecks =
        0L

    private var errorChecks =
        0L

    private var lastCheckMs =
        0L

    private var rootFault:
        Fault? =
        null

    private var lastFault:
        Fault? =
        null

    fun resetSession() {
        synchronized(
            lock
        ) {
            status =
                Status.HEALTHY

            checks =
                0L

            healthyChecks =
                0L

            degradedChecks =
                0L

            errorChecks =
                0L

            lastCheckMs =
                0L

            rootFault =
                null

            lastFault =
                null

            recent.clear()
        }
    }

    fun completeCheck(
        observations: List<Observation>
    ): List<Fault> {
        val now =
            System.currentTimeMillis()

        synchronized(
            lock
        ) {
            checks +=
                1L

            lastCheckMs =
                now

            status =
                when {
                    observations.any {
                        it.fatal ||
                            it.severity ==
                            Severity.FATAL
                    } ->
                        Status.ERROR

                    observations.any {
                        it.severity ==
                            Severity.ERROR ||
                            it.severity ==
                            Severity.WARNING
                    } ->
                        Status.DEGRADED

                    else ->
                        Status.HEALTHY
                }

            when (status) {
                Status.HEALTHY ->
                    healthyChecks +=
                        1L

                Status.DEGRADED ->
                    degradedChecks +=
                        1L

                Status.ERROR ->
                    errorChecks +=
                        1L
            }

            val recorded =
                ArrayList<Fault>(
                    observations.size
                )

            observations
                .forEach {
                        observation ->

                    val previous =
                        recent
                            .lastOrNull {
                                it.code ==
                                    observation.code &&
                                    it.module ==
                                    observation.module &&
                                    it.message ==
                                    observation.message
                            }

                    val fault =
                        if (previous != null) {
                            previous.copy(
                                lastSeenMs =
                                    now,
                                occurrences =
                                    previous.occurrences +
                                        1L
                            )
                        } else {
                            Fault(
                                code =
                                    observation.code,
                                module =
                                    observation.module,
                                severity =
                                    observation.severity,
                                message =
                                    observation.message,
                                fatal =
                                    observation.fatal,
                                firstSeenMs =
                                    now,
                                lastSeenMs =
                                    now,
                                occurrences =
                                    1L
                            )
                        }

                    if (previous != null) {
                        recent.remove(
                            previous
                        )
                    }

                    recent.addLast(
                        fault
                    )

                    while (
                        recent.size >
                        MAX_RECENT_FAULTS
                    ) {
                        recent.removeFirst()
                    }

                    if (
                        rootFault ==
                            null &&
                        (
                            observation.severity ==
                                Severity.ERROR ||
                                observation.severity ==
                                Severity.FATAL ||
                                observation.fatal
                            )
                    ) {
                        rootFault =
                            fault
                    }

                    lastFault =
                        fault

                    recorded.add(
                        fault
                    )
                }

            return recorded
        }
    }

    fun snapshot():
        Snapshot =
        synchronized(
            lock
        ) {
            Snapshot(
                status =
                    status,
                checks =
                    checks,
                healthyChecks =
                    healthyChecks,
                degradedChecks =
                    degradedChecks,
                errorChecks =
                    errorChecks,
                lastCheckMs =
                    lastCheckMs,
                rootFault =
                    rootFault,
                lastFault =
                    lastFault,
                recentFaults =
                    recent.toList()
            )
        }

    companion object {
        private const val MAX_RECENT_FAULTS =
            24
    }
}
