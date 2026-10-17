/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Workload Module
 * Developer Seal: BCI-CJB-ANDROID
 * Integrity: SHA-256 emitted by the Android CI source-hash manifest.
 */
package org.beaconcomputinginitiative.bciwhisper.workload

import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Serializes CPU-heavy offline work so Whisper transcription and mastering
 * cannot saturate the device at the same time.
 *
 * A job owns the heavy-compute lane if and only if [activeJob] equals that job.
 */
class HeavyWorkCoordinator {
    enum class Job {
        TRANSCRIPTION,
        MASTERING
    }

    private val activeJobRef =
        AtomicReference<Job?>(null)

    private val closed =
        AtomicBoolean(false)

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "BCI-HeavyWork").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = false
            }
        }

    fun activeJob(): Job? =
        activeJobRef.get()

    fun isIdle(): Boolean =
        activeJobRef.get() == null

    fun isRunning(job: Job): Boolean =
        activeJobRef.get() == job

    fun <T> trySubmit(
        job: Job,
        task: () -> T,
        onComplete: (Result<T>) -> Unit
    ): Boolean {
        if (closed.get()) {
            return false
        }

        // Ownership succeeds if and only if the lane was idle at this instant.
        if (!activeJobRef.compareAndSet(null, job)) {
            return false
        }

        executor.execute {
            runCatching {
                Process.setThreadPriority(
                    Process.THREAD_PRIORITY_BACKGROUND
                )
            }

            val result =
                runCatching(task)

            activeJobRef.compareAndSet(
                job,
                null
            )

            if (!closed.get()) {
                mainHandler.post {
                    if (!closed.get()) {
                        onComplete(result)
                    }
                }
            }
        }

        return true
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
}
