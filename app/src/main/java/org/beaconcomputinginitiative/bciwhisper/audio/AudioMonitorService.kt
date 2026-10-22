/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Audio Monitor Service
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

package org.beaconcomputinginitiative.bciwhisper.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import org.beaconcomputinginitiative.bciwhisper.MainActivity
import org.beaconcomputinginitiative.bciwhisper.R
import org.beaconcomputinginitiative.bciwhisper.broadcast.BroadcastConfig
import org.beaconcomputinginitiative.bciwhisper.broadcast.BroadcastPublisher
import org.beaconcomputinginitiative.bciwhisper.broadcast.PublisherCredentialStore
import org.beaconcomputinginitiative.bciwhisper.transcription.LiveTranscriptionEngine
import org.beaconcomputinginitiative.bciwhisper.runtime.RobustErrorHandler
import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class AudioMonitorService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): AudioMonitorService =
            this@AudioMonitorService
    }

    data class RecordingEntry(
        val baseName: String,
        val rawFile: File?,
        val isolatedFile: File?,
        val modified: Long,
        val totalBytes: Long
    )

    data class Snapshot(
        val running: Boolean,
        val sampleRate: Int,
        val rmsDbfs: Double,
        val peakDbfs: Double,
        val baselineSeconds: Double,
        val baselineComplete: Boolean,
        val noiseFloorDbfs: Double,
        val snrDb: Double,
        val signalActive: Boolean,
        val lockLowHz: Double,
        val lockHighHz: Double,
        val lockCenterHz: Double,
        val lockExcessDb: Double,
        val spectrumBands: FloatArray,
        val recordingActive: Boolean,
        val lockedSeconds: Double,
        val lockedSignalDbfs: Double,
        val lockedSignalPeakDbfs: Double,
        val lastEventSummary: String?,
        val framesCaptured: Long,
        val inputSource: String,
        val inputDevice: String,
        val inputCapabilities: String,
        val lastError: String?,
        val broadcastState: BroadcastPublisher.State,
        val broadcastBytesSent: Long,
        val broadcastDroppedFrames: Long,
        val broadcastTranscriptSegments: Long,
        val broadcastDroppedTranscripts: Long,
        val liveTranscriptionDecodeMs: Long,
        val liveTranscriptionSkippedWindows: Long,
        val liveTranscriptionText: String?,
        val liveTranscriptionError: String?,
        val broadcastLastError: String?,
        val healthStatus: RobustErrorHandler.Status,
        val healthChecks: Long,
        val rootErrorCode: String?,
        val rootErrorMessage: String?,
        val lastErrorCode: String?,
        val lastErrorMessage: String?
    )

    private val binder = LocalBinder()
    private val ring =
        AudioRingBuffer(SAMPLE_RATE * 15)

    private val publisher =
        BroadcastPublisher()

    private val robustErrorHandler =
        RobustErrorHandler()

    @Volatile
    private var robustErrorLoopRunning =
        false

    private var robustErrorThread:
        Thread? =
        null

    private var healthLastFrames =
        0L

    private var healthLastFrameProgressMs =
        0L

    private var healthLastBaselineSeconds =
        0.0

    private var healthLastBaselineProgressMs =
        0L

    private var healthLastNativeXruns =
        0

    private var healthLastNativeDroppedFrames =
        0L

    private var healthLastBroadcastDroppedFrames =
        0L

    private var healthLastBroadcastDroppedTranscripts =
        0L

    private val publisherCredentials by lazy {
        PublisherCredentialStore(this)
    }

    private val bluetoothRouter by lazy {
        BluetoothAudioRouter(this)
    }

    private val inputOptimizer by lazy {
        AudioInputOptimizer(this)
    }

    @Volatile
    private var inputSource = INPUT_SOURCE_PHONE

    @Volatile
    private var inputDevice = "Phone microphone"

    @Volatile
    private var inputCapabilities =
        "Input capability probe pending"

    private lateinit var detector:
        DetectionEngine

    private lateinit var recorder:
        EventRecorder

    private lateinit var liveTranscription:
        LiveTranscriptionEngine

    @Volatile private var running = false
    @Volatile private var rmsDbfs = -120.0
    @Volatile private var peakDbfs = -120.0
    @Volatile private var baselineSeconds = 0.0
    @Volatile private var baselineComplete = false
    @Volatile private var noiseFloorDbfs = -120.0
    @Volatile private var snrDb = 0.0
    @Volatile private var signalActive = false
    @Volatile private var lockLowHz = 80.0
    @Volatile private var lockHighHz = 8500.0
    @Volatile private var lockCenterHz = 1200.0
    @Volatile private var lockExcessDb = -120.0
    @Volatile private var spectrumBands = FloatArray(40)
    @Volatile private var framesCaptured = 0L
    @Volatile private var lockedSignalPowerSum = 0.0
    @Volatile private var lockedSignalBlocks = 0L
    @Volatile private var lockedSignalPeakDbfs = -120.0
    @Volatile private var lastError: String? = null
    @Volatile private var lastEventSummary: String? = null

    private var worker: Thread? = null

    private var lastNotificationSignature: String? =
        null

    private val recordingsDirectory: File by lazy {
        File(
            getExternalFilesDir(
                Environment.DIRECTORY_MUSIC
            ) ?: filesDir,
            "BCIWhisper/Recordings"
        ).apply {
            mkdirs()
        }
    }

    override fun onCreate() {
        super.onCreate()

        detector = DetectionEngine(
            sampleRate = SAMPLE_RATE,
            blockSize = BLOCK_SIZE,
            baselineSecondsRequired =
                BASELINE_SECONDS,
            sensitivity = 2.0
        )

        recorder = EventRecorder(
            directory =
                recordingsDirectory,
            sampleRate =
                SAMPLE_RATE
        )

        liveTranscription =
            LiveTranscriptionEngine(
                context =
                    this,
                sampleRate =
                    SAMPLE_RATE,
                onSegment = {
                        segment ->
                    publisher.submitTranscript(
                        sequence =
                            segment.sequence,
                        startMs =
                            segment.startMs,
                        endMs =
                            segment.endMs,
                        speaker =
                            segment.speaker,
                        text =
                            segment.text,
                        decodeMs =
                            segment.decodeMs
                    )
                }
            )

        createNotificationChannel()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                inputSource = intent.getStringExtra(EXTRA_INPUT_SOURCE)
                    ?.takeIf { it == INPUT_SOURCE_PHONE || it == INPUT_SOURCE_BLUETOOTH }
                    ?: INPUT_SOURCE_PHONE
                startMonitoring()
            }

            ACTION_STOP ->
                stopMonitoring()

            ACTION_GO_LIVE -> {
                val token =
                    publisherCredentials
                        .read()
                        .orEmpty()

                try {
                    require(token.isNotBlank()) {
                        "Publisher credential is not configured"
                    }

                    inputSource =
                        intent.getStringExtra(
                            EXTRA_INPUT_SOURCE
                        )
                            ?.takeIf {
                                it == INPUT_SOURCE_PHONE ||
                                    it == INPUT_SOURCE_BLUETOOTH
                            }
                            ?: inputSource

                    startMonitoring()

                    liveTranscription.start()

                    publisher.start(
                        BroadcastConfig.PUBLISH_URL,
                        token
                    )

                    updateNotification(
                        force = true
                    )
                } catch (t: Throwable) {
                    lastError =
                        "Go Live: " +
                            (
                                t.message
                                    ?: t::class.simpleName
                                    ?: "error"
                                )
                }
            }

            ACTION_STOP_LIVE -> {
                liveTranscription.stop()
                publisher.stop()

                updateNotification(
                    force = true
                )
            }
        }

        return START_NOT_STICKY
    }

    fun snapshot(): Snapshot {
        val broadcast =
            publisher.snapshot()

        val captions =
            liveTranscription.snapshot()

        val health =
            robustErrorHandler.snapshot()

        return Snapshot(
            running = running,
            sampleRate = SAMPLE_RATE,
            rmsDbfs = rmsDbfs,
            peakDbfs = peakDbfs,
            baselineSeconds =
                baselineSeconds,
            baselineComplete =
                baselineComplete,
            noiseFloorDbfs =
                noiseFloorDbfs,
            snrDb = snrDb,
            signalActive =
                signalActive,
            lockLowHz = lockLowHz,
            lockHighHz = lockHighHz,
            lockCenterHz = lockCenterHz,
            lockExcessDb = lockExcessDb,
            spectrumBands = spectrumBands.copyOf(),
            recordingActive =
                recorder.active,
            lockedSeconds =
                recorder.lockedSeconds,
            lockedSignalDbfs =
                if (lockedSignalBlocks > 0) {
                    10.0 * log10(
                        (lockedSignalPowerSum / lockedSignalBlocks)
                            .coerceAtLeast(1e-12)
                    )
                } else {
                    -120.0
                },
            lockedSignalPeakDbfs =
                lockedSignalPeakDbfs,
            lastEventSummary =
                lastEventSummary,
            framesCaptured =
                framesCaptured,
            inputSource = inputSource,
            inputDevice = inputDevice,
            inputCapabilities = inputCapabilities,
            lastError = lastError,
            broadcastState =
                broadcast.state,
            broadcastBytesSent =
                broadcast.bytesSent,
            broadcastDroppedFrames =
                broadcast.droppedFrames,
            broadcastTranscriptSegments =
                broadcast.transcriptsSent,
            broadcastDroppedTranscripts =
                broadcast.droppedTranscripts,
            liveTranscriptionDecodeMs =
                captions.lastDecodeMs,
            liveTranscriptionSkippedWindows =
                captions.skippedWindows,
            liveTranscriptionText =
                captions.latestText,
            liveTranscriptionError =
                captions.lastError,
            broadcastLastError =
                broadcast.lastError,
            healthStatus =
                health.status,
            healthChecks =
                health.checks,
            rootErrorCode =
                health.rootFault?.code,
            rootErrorMessage =
                health.rootFault?.message,
            lastErrorCode =
                health.lastFault?.code,
            lastErrorMessage =
                health.lastFault?.message
        )
    }

    fun recordings(): List<RecordingEntry> {
        val groups =
            recordingsDirectory
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.extension.equals(
                            "wav",
                            ignoreCase = true
                        )
                }
                .groupBy { file ->
                    file.name
                        .removeSuffix("_raw.wav")
                        .removeSuffix(
                            "_isolated.wav"
                        )
                }

        return groups
            .map { (base, files) ->
                val raw =
                    files.firstOrNull {
                        it.name.endsWith(
                            "_raw.wav"
                        )
                    }

                val isolated =
                    files.firstOrNull {
                        it.name.endsWith(
                            "_isolated.wav"
                        )
                    }

                RecordingEntry(
                    baseName = base,
                    rawFile = raw,
                    isolatedFile =
                        isolated,
                    modified =
                        files.maxOfOrNull {
                            it.lastModified()
                        } ?: 0L,
                    totalBytes =
                        files.sumOf {
                            it.length()
                        }
                )
            }
            .sortedByDescending {
                it.modified
            }
    }

    private data class NativeInputRoute(
        val deviceId: Int,
        val communicationPreset: Boolean,
        val capability: AudioInputOptimizer.Capability
    )

    private fun prepareNativeInput(): NativeInputRoute {
        val usingBluetooth =
            inputSource ==
                INPUT_SOURCE_BLUETOOTH

        val route =
            if (usingBluetooth) {
                bluetoothRouter.route()
            } else {
                bluetoothRouter.clear()

                val builtIn =
                    inputOptimizer
                        .builtInMicrophone()

                BluetoothAudioRouter.RouteResult(
                    routed = true,
                    label =
                        builtIn
                            ?.productName
                            ?.toString()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: "Phone microphone",
                    device = builtIn
                )
            }

        require(
            route.routed
        ) {
            route.label
        }

        inputDevice =
            route.label

        val capability =
            inputOptimizer
                .capability(
                    route.device,
                    route.label
                )

        return NativeInputRoute(
            deviceId =
                route.device
                    ?.id
                    ?: 0,
            communicationPreset =
                usingBluetooth,
            capability =
                capability
        )
    }

    private fun startMonitoring() {
        if (running) return

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lastError =
                "RECORD_AUDIO permission " +
                    "not granted"

            stopSelf()
            return
        }

        startForeground(
            NOTIFICATION_ID,
            notification(
                "Microphone boundary starting…"
            )
        )

        try {
            val route =
                prepareNativeInput()

            check(
                NativeAudioEngine
                    .startCapture(
                        sampleRate =
                            SAMPLE_RATE,
                        deviceId =
                            route.deviceId,
                        communicationPreset =
                            route.communicationPreset
                    )
            ) {
                "Oboe input stream did not initialize"
            }

            val actualRate =
                NativeAudioEngine
                    .actualSampleRate()

            check(
                actualRate ==
                    SAMPLE_RATE
            ) {
                "Oboe sample-rate contract mismatch: " +
                    "$actualRate Hz"
            }

            val processing =
                inputOptimizer
                    .disableVoiceProcessing(
                        NativeAudioEngine
                            .sessionId()
                    )

            inputCapabilities =
                inputOptimizer
                    .describe(
                        route.capability,
                        processing
                    ) +
                    " • Oboe native" +
                    " • burst:" +
                    NativeAudioEngine
                        .framesPerBurst()

            ring.clear()
            detector.reset()
            recorder.abort()

            framesCaptured = 0
            lockedSignalPowerSum = 0.0
            lockedSignalBlocks = 0L
            lockedSignalPeakDbfs = -120.0
            baselineSeconds = 0.0
            baselineComplete = false
            noiseFloorDbfs = -120.0
            snrDb = 0.0
            signalActive = false
            lockLowHz = 80.0
            lockHighHz = 8500.0
            lockCenterHz = 1200.0
            lockExcessDb = -120.0
            spectrumBands = FloatArray(40)
            lastError = null
            lastEventSummary = null

            robustErrorHandler
                .resetSession()

            running = true

            startRobustErrorHandling()

            worker =
                Thread(
                    {
                        captureLoop()
                    },
                    "BCI-OboeDrain"
                ).also {
                    it.start()
                }

            updateNotification(
                force = true
            )
        } catch (t: Throwable) {
            val message =
                "${t::class.simpleName}: " +
                    (
                        t.message
                            ?: "monitor startup failure"
                        )

            recordImmediateFault(
                code =
                    BCI_E_MONITOR_START,
                module =
                    "android.audio.startup",
                severity =
                    RobustErrorHandler
                        .Severity.FATAL,
                message =
                    message,
                fatal =
                    true
            )

            failMonitoringSafely(
                BCI_E_MONITOR_START,
                message
            )
        }
    }

    private fun captureLoop() {
        // Oboe owns the real-time microphone callback and writes into a native
        // SPSC ring. This worker drains larger PCM blocks for Kotlin-side
        // detection/recording without blocking the native audio callback.
        runCatching {
            Process.setThreadPriority(
                Process.THREAD_PRIORITY_AUDIO
            )
        }

        val block =
            ShortArray(BLOCK_SIZE)

        var publisherWasLive =
            false

        while (running) {
            try {
                val n =
                    NativeAudioEngine
                        .readPcm16(
                        destination =
                            block,
                        requestedFrames =
                            block.size,
                        timeoutMs =
                            250
                    )

            if (n <= 0) {
                if (
                    running &&
                    !NativeAudioEngine
                        .captureActive()
                ) {
                    recordImmediateFault(
                        code =
                            BCI_E_AUDIO_INACTIVE,
                        module =
                            "native.audio",
                        severity =
                            RobustErrorHandler
                                .Severity.FATAL,
                        message =
                            "Oboe input stream stopped",
                        fatal =
                            true
                    )

                    failMonitoringSafely(
                        BCI_E_AUDIO_INACTIVE,
                        "Oboe input stream stopped"
                    )

                    break
                }

                continue
            }

            ring.write(
                block,
                n
            )

            val broadcastState =
                publisher
                    .snapshot()
                    .state

            if (
                broadcastState ==
                BroadcastPublisher
                    .State.LIVE
            ) {
                if (!publisherWasLive) {
                    liveTranscription
                        .resetStream()
                }

                publisher.submitPcm(
                    block,
                    n
                )

                liveTranscription.offer(
                    block,
                    n
                )

                publisherWasLive =
                    true
            } else {
                publisherWasLive =
                    false
            }

            framesCaptured +=
                n.toLong()

            updateLevelTelemetry(
                block,
                n
            )

            val detection =
                detector.process(
                    block,
                    n
                )

            baselineSeconds =
                detection
                    .baselineSeconds

            baselineComplete =
                detection
                    .baselineComplete

            noiseFloorDbfs =
                detection
                    .noiseFloorDbfs

            snrDb =
                detection.snrDb

            signalActive =
                detection.signalActive

            lockLowHz = detection.lockLowHz
            lockHighHz = detection.lockHighHz
            lockCenterHz = detection.lockCenterHz
            lockExcessDb = detection.lockExcessDb
            spectrumBands = detection.spectrumBands

            if (
                baselineComplete &&
                detection.confirmedTrigger &&
                !recorder.active
            ) {
                val preRoll =
                    ring.snapshot(
                        SAMPLE_RATE
                    )

                recorder.start(
                    preRoll,
                    detection.lockLowHz,
                    detection.lockHighHz
                )

                // A new retained-event candidate owns a fresh signal-level
                // accumulator. These values are reported as dBFS because
                // Android microphone input is not SPL-calibrated.
                lockedSignalPowerSum = 0.0
                lockedSignalBlocks = 0L
                lockedSignalPeakDbfs = -120.0
            }

            if (recorder.active) {
                recorder.append(
                    block,
                    n,
                    signalActive,
                    detection.lockLowHz,
                    detection.lockHighHz
                )

                if (signalActive) {
                    lockedSignalPowerSum +=
                        10.0.pow(rmsDbfs / 10.0)
                    lockedSignalBlocks += 1L
                    lockedSignalPeakDbfs =
                        kotlin.math.max(
                            lockedSignalPeakDbfs,
                            peakDbfs
                        )
                }

                if (
                    recorder
                        .shouldStopForLoss()
                ) {
                    finishRecording(
                        "signal_lost"
                    )
                }
            }

                updateNotification()
            } catch (t: Throwable) {
                val message =
                    "${t::class.simpleName}: " +
                        (
                            t.message
                                ?: "capture loop failure"
                            )

                recordImmediateFault(
                    code =
                        BCI_E_CAPTURE_LOOP,
                    module =
                        "android.audio.capture",
                    severity =
                        RobustErrorHandler
                            .Severity.FATAL,
                    message =
                        message,
                    fatal =
                        true
                )

                failMonitoringSafely(
                    BCI_E_CAPTURE_LOOP,
                    message
                )

                break
            }
        }
    }

    private fun updateLevelTelemetry(
        block: ShortArray,
        count: Int
    ) {
        val n =
            count
                .coerceAtMost(
                    block.size
                )
                .coerceAtLeast(1)

        var sumSq = 0.0
        var peak = 0

        for (i in 0 until n) {
            val value =
                block[i].toInt()

            sumSq +=
                value.toDouble() *
                    value.toDouble()

            val absolute =
                kotlin.math.abs(
                    value
                )

            if (absolute > peak) {
                peak = absolute
            }
        }

        val rms =
            sqrt(
                sumSq /
                    n
            )

        rmsDbfs =
            if (rms > 0.0) {
                20.0 *
                    log10(
                        rms /
                            32768.0
                    )
            } else {
                -120.0
            }

        peakDbfs =
            if (peak > 0) {
                20.0 *
                    log10(
                        peak.toDouble() /
                            32768.0
                    )
            } else {
                -120.0
            }
    }

    private fun finishRecording(
        reason: String
    ) {
        val event =
            recorder.finish(
                reason
            )

        detector.releaseLock()

        lastEventSummary =
            if (event.kept) {
                "SAVED • " +
                    String.format(
                        java.util.Locale.US,
                        "%.1fs lock / %.1fs total",
                        event.lockedSeconds,
                        event.totalSeconds
                    )
            } else {
                "DISCARDED • " +
                    String.format(
                        java.util.Locale.US,
                        "%.1fs lock < 15.0s keep rule",
                        event.lockedSeconds
                    )
            }
    }

    private fun stopMonitoring() {
        stopRobustErrorHandling()
        liveTranscription.stop()
        publisher.stop()
        bluetoothRouter.clear()

        if (recorder.active) {
            finishRecording(
                "operator_stop"
            )
        }

        running = false

        NativeAudioEngine
            .stopCapture()

        try {
            worker?.join(
                1200
            )
        } catch (
            _: InterruptedException
        ) {
        }

        worker = null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {
        stopRobustErrorHandling()
        liveTranscription.stop()
        publisher.stop()
        bluetoothRouter.clear()

        if (recorder.active) {
            recorder.abort()
        }

        running = false

        NativeAudioEngine
            .stopCapture()

        super.onDestroy()
    }

    private fun startRobustErrorHandling() {
        if (robustErrorLoopRunning) {
            return
        }

        val now =
            SystemClock
                .elapsedRealtime()

        healthLastFrames =
            framesCaptured

        healthLastFrameProgressMs =
            now

        healthLastBaselineSeconds =
            baselineSeconds

        healthLastBaselineProgressMs =
            now

        healthLastNativeXruns =
            runCatching {
                NativeAudioEngine
                    .xRunCount()
            }
                .getOrDefault(
                    0
                )

        healthLastNativeDroppedFrames =
            runCatching {
                NativeAudioEngine
                    .droppedFrames()
            }
                .getOrDefault(
                    0L
                )

        val broadcast =
            publisher.snapshot()

        healthLastBroadcastDroppedFrames =
            broadcast.droppedFrames

        healthLastBroadcastDroppedTranscripts =
            broadcast.droppedTranscripts

        robustErrorLoopRunning =
            true

        robustErrorThread =
            Thread(
                {
                    robustErrorLoop()
                },
                "BCI-RobustErrorHandler"
            ).apply {
                priority =
                    Thread.NORM_PRIORITY -
                        1

                start()
            }
    }

    private fun stopRobustErrorHandling() {
        robustErrorLoopRunning =
            false

        val thread =
            robustErrorThread

        thread?.interrupt()

        if (
            thread !=
            null &&
            thread !==
            Thread.currentThread()
        ) {
            try {
                thread.join(
                    750L
                )
            } catch (
                _: InterruptedException
            ) {
                Thread
                    .currentThread()
                    .interrupt()
            }
        }

        if (
            thread ===
            robustErrorThread
        ) {
            robustErrorThread =
                null
        }
    }

    private fun robustErrorLoop() {
        while (
            robustErrorLoopRunning
        ) {
            try {
                val observations =
                    collectHealthObservations()

                val faults =
                    robustErrorHandler
                        .completeCheck(
                            observations
                        )

                val fatal =
                    faults
                        .firstOrNull {
                            it.fatal ||
                                it.severity ==
                                RobustErrorHandler
                                    .Severity.FATAL
                        }

                if (
                    fatal !=
                    null &&
                    running
                ) {
                    failMonitoringSafely(
                        fatal.code,
                        fatal.message
                    )
                }
            } catch (
                t: Throwable
            ) {
                val message =
                    "${t::class.simpleName}: " +
                        (
                            t.message
                                ?: "robust error handler failure"
                            )

                runCatching {
                    robustErrorHandler
                        .completeCheck(
                            listOf(
                                RobustErrorHandler
                                    .Observation(
                                        code =
                                            BCI_E_ERROR_HANDLER,
                                        module =
                                            "android.robust_error_handler",
                                        severity =
                                            RobustErrorHandler
                                                .Severity.ERROR,
                                        message =
                                            message,
                                        fatal =
                                            false
                                    )
                            )
                        )
                }

                lastError =
                    "$BCI_E_ERROR_HANDLER • " +
                        message
            }

            try {
                Thread.sleep(
                    ROBUST_ERROR_CHECK_MS
                )
            } catch (
                _: InterruptedException
            ) {
                if (
                    !robustErrorLoopRunning
                ) {
                    return
                }
            }
        }
    }

    private fun collectHealthObservations():
        List<RobustErrorHandler.Observation> {
        val observations =
            ArrayList<
                RobustErrorHandler
                    .Observation
                >()

        val now =
            SystemClock
                .elapsedRealtime()

        if (running) {
            val nativeActive =
                runCatching {
                    NativeAudioEngine
                        .captureActive()
                }
                    .getOrElse {
                        observations.add(
                            RobustErrorHandler
                                .Observation(
                                    code =
                                        BCI_E_NATIVE_PROBE,
                                    module =
                                        "native.audio",
                                    severity =
                                        RobustErrorHandler
                                            .Severity.FATAL,
                                    message =
                                        "captureActive probe failed: " +
                                            (
                                                it.message
                                                    ?: it::class
                                                        .simpleName
                                                ),
                                    fatal =
                                        true
                                )
                        )

                        false
                    }

            if (!nativeActive) {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_E_AUDIO_INACTIVE,
                            module =
                                "native.audio",
                            severity =
                                RobustErrorHandler
                                    .Severity.FATAL,
                            message =
                                "Native Oboe stream is inactive while monitoring is RUNNING",
                            fatal =
                                true
                        )
                )
            }

            val actualRate =
                runCatching {
                    NativeAudioEngine
                        .actualSampleRate()
                }
                    .getOrDefault(
                        0
                    )

            if (
                actualRate !=
                SAMPLE_RATE
            ) {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_E_SAMPLE_RATE,
                            module =
                                "native.audio",
                            severity =
                                RobustErrorHandler
                                    .Severity.FATAL,
                            message =
                                "Expected $SAMPLE_RATE Hz, native stream reports $actualRate Hz",
                            fatal =
                                true
                        )
                )
            }

            val currentFrames =
                framesCaptured

            if (
                currentFrames >
                healthLastFrames
            ) {
                healthLastFrames =
                    currentFrames

                healthLastFrameProgressMs =
                    now
            } else if (
                now -
                    healthLastFrameProgressMs >=
                CAPTURE_STALL_MS
            ) {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_E_CAPTURE_STALL,
                            module =
                                "android.audio.capture",
                            severity =
                                RobustErrorHandler
                                    .Severity.FATAL,
                            message =
                                "No PCM frame progress for " +
                                    (
                                        now -
                                            healthLastFrameProgressMs
                                        ) +
                                    " ms",
                            fatal =
                                true
                        )
                )
            }

            if (!baselineComplete) {
                val currentBaseline =
                    baselineSeconds

                if (
                    currentBaseline >
                    healthLastBaselineSeconds +
                        0.01
                ) {
                    healthLastBaselineSeconds =
                        currentBaseline

                    healthLastBaselineProgressMs =
                        now
                } else if (
                    currentFrames >
                        0L &&
                    now -
                        healthLastBaselineProgressMs >=
                    BASELINE_STALL_MS
                ) {
                    observations.add(
                        RobustErrorHandler
                            .Observation(
                                code =
                                    BCI_E_BASELINE_STALL,
                                module =
                                    "kotlin.detection",
                                severity =
                                    RobustErrorHandler
                                        .Severity.FATAL,
                                message =
                                    "PCM is arriving but baseline progress has not advanced for " +
                                        (
                                            now -
                                                healthLastBaselineProgressMs
                                            ) +
                                        " ms",
                                fatal =
                                    true
                            )
                    )
                }
            }

            val xruns =
                runCatching {
                    NativeAudioEngine
                        .xRunCount()
                }
                    .getOrDefault(
                        healthLastNativeXruns
                    )

            if (
                xruns >
                healthLastNativeXruns
            ) {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_W_AUDIO_XRUN,
                            module =
                                "native.audio",
                            severity =
                                RobustErrorHandler
                                    .Severity.WARNING,
                            message =
                                "Oboe xRun count increased by " +
                                    (
                                        xruns -
                                            healthLastNativeXruns
                                        ),
                            fatal =
                                false
                        )
                )

                healthLastNativeXruns =
                    xruns
            }

            val nativeDropped =
                runCatching {
                    NativeAudioEngine
                        .droppedFrames()
                }
                    .getOrDefault(
                        healthLastNativeDroppedFrames
                    )

            if (
                nativeDropped >
                healthLastNativeDroppedFrames
            ) {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_W_NATIVE_RING_DROP,
                            module =
                                "native.audio",
                            severity =
                                RobustErrorHandler
                                    .Severity.WARNING,
                            message =
                                "Native ring dropped " +
                                    (
                                        nativeDropped -
                                            healthLastNativeDroppedFrames
                                        ) +
                                    " PCM frame(s)",
                            fatal =
                                false
                        )
                )

                healthLastNativeDroppedFrames =
                    nativeDropped
            }
        }

        val captions =
            liveTranscription
                .snapshot()

        captions.lastError
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                observations.add(
                    RobustErrorHandler
                        .Observation(
                            code =
                                BCI_E_LIVE_TRANSCRIPTION,
                            module =
                                "kotlin.transcription",
                            severity =
                                RobustErrorHandler
                                    .Severity.ERROR,
                            message =
                                it,
                            fatal =
                                false
                        )
                )
            }

        val broadcast =
            publisher.snapshot()

        if (
            broadcast.state ==
            BroadcastPublisher
                .State.ERROR
        ) {
            observations.add(
                RobustErrorHandler
                    .Observation(
                        code =
                            BCI_E_BROADCAST,
                        module =
                            "kotlin.broadcast",
                        severity =
                            RobustErrorHandler
                                .Severity.ERROR,
                        message =
                            broadcast.lastError
                                ?: "Broadcast publisher entered ERROR state",
                        fatal =
                            false
                    )
            )
        }

        if (
            broadcast.droppedFrames >
            healthLastBroadcastDroppedFrames
        ) {
            observations.add(
                RobustErrorHandler
                    .Observation(
                        code =
                            BCI_W_BROADCAST_AUDIO_DROP,
                        module =
                            "kotlin.broadcast",
                        severity =
                            RobustErrorHandler
                                .Severity.WARNING,
                        message =
                            "Broadcast publisher dropped " +
                                (
                                    broadcast.droppedFrames -
                                        healthLastBroadcastDroppedFrames
                                    ) +
                                " audio frame(s)",
                        fatal =
                            false
                    )
            )

            healthLastBroadcastDroppedFrames =
                broadcast.droppedFrames
        }

        if (
            broadcast.droppedTranscripts >
            healthLastBroadcastDroppedTranscripts
        ) {
            observations.add(
                RobustErrorHandler
                    .Observation(
                        code =
                            BCI_W_BROADCAST_TRANSCRIPT_DROP,
                        module =
                            "kotlin.broadcast",
                        severity =
                            RobustErrorHandler
                                .Severity.WARNING,
                        message =
                            "Broadcast publisher dropped " +
                                (
                                    broadcast.droppedTranscripts -
                                        healthLastBroadcastDroppedTranscripts
                                    ) +
                                " transcript message(s)",
                        fatal =
                            false
                    )
            )

            healthLastBroadcastDroppedTranscripts =
                broadcast.droppedTranscripts
        }

        return observations
    }

    private fun recordImmediateFault(
        code: String,
        module: String,
        severity: RobustErrorHandler.Severity,
        message: String,
        fatal: Boolean
    ) {
        robustErrorHandler
            .completeCheck(
                listOf(
                    RobustErrorHandler
                        .Observation(
                            code =
                                code,
                            module =
                                module,
                            severity =
                                severity,
                            message =
                                message,
                            fatal =
                                fatal
                        )
                )
            )

        lastError =
            "$code • $message"
    }

    private fun failMonitoringSafely(
        code: String,
        message: String
    ) {
        lastError =
            "$code • $message"

        running =
            false

        robustErrorLoopRunning =
            false

        runCatching {
            NativeAudioEngine
                .stopCapture()
        }

        runCatching {
            liveTranscription
                .stop()
        }

        runCatching {
            publisher
                .stop()
        }

        if (
            ::recorder.isInitialized &&
            recorder.active
        ) {
            runCatching {
                recorder.abort()
            }
        }

        runCatching {
            bluetoothRouter
                .clear()
        }

        runCatching {
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "BCIWhisper microphone",
                NotificationManager
                    .IMPORTANCE_LOW
            )

        channel.description =
            "Foreground microphone analysis, " +
                "event recording, and " +
                "operator-enabled live broadcast"

        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(
            channel
        )
    }

    private fun updateNotification(
        force: Boolean = false
    ) {
        if (!running) return

        val broadcastState =
            publisher.snapshot().state

        val recordingState =
            when {
                recorder.active ->
                    "recording"

                baselineComplete ->
                    "monitoring"

                else ->
                    "calibrating"
            }

        val signature =
            "${broadcastState.name}:" +
                recordingState

        if (
            !force &&
            signature ==
                lastNotificationSignature
        ) {
            return
        }

        lastNotificationSignature =
            signature

        val message =
            buildString {
                append(
                    when {
                        recorder.active ->
                            "SIGNAL LOCKED • recording"

                        !baselineComplete ->
                            "Calibrating room baseline"

                        else ->
                            "Microphone monitoring active"
                    }
                )

                when (broadcastState) {
                    BroadcastPublisher
                        .State.LIVE ->
                        append(
                            " • LIVE broadcast"
                        )

                    BroadcastPublisher
                        .State.CONNECTING ->
                        append(
                            " • connecting broadcast"
                        )

                    BroadcastPublisher
                        .State.RETRYING ->
                        append(
                            " • reconnecting broadcast"
                        )

                    BroadcastPublisher
                        .State.ERROR ->
                        append(
                            " • broadcast error"
                        )

                    BroadcastPublisher
                        .State.OFFLINE -> {
                    }
                }
            }

        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            notification(
                message
            )
        )
    }

    private fun notification(
        message: String
    ): Notification {
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent
                        .FLAG_UPDATE_CURRENT
            )

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable
                    .ic_bci_notification
            )
            .setContentTitle(
                "BCIWhisper"
            )
            .setContentText(
                message
            )
            .setOngoing(true)
            .setContentIntent(
                pending
            )
            .build()
    }

    companion object {
        const val ACTION_START =
            "org.beaconcomputinginitiative." +
                "bciwhisper.START"

        const val ACTION_STOP =
            "org.beaconcomputinginitiative." +
                "bciwhisper.STOP"

        const val ACTION_GO_LIVE =
            "org.beaconcomputinginitiative." +
                "bciwhisper.GO_LIVE"

        const val ACTION_STOP_LIVE =
            "org.beaconcomputinginitiative." +
                "bciwhisper.STOP_LIVE"

        const val EXTRA_INPUT_SOURCE =
            "input_source"

        const val INPUT_SOURCE_PHONE =
            "phone"

        const val INPUT_SOURCE_BLUETOOTH =
            "bluetooth"

        private const val ROBUST_ERROR_CHECK_MS =
            250L

        private const val CAPTURE_STALL_MS =
            1_500L

        private const val BASELINE_STALL_MS =
            3_000L

        private const val BCI_E_MONITOR_START =
            "BCI_E_MONITOR_START"

        private const val BCI_E_CAPTURE_LOOP =
            "BCI_E_CAPTURE_LOOP"

        private const val BCI_E_AUDIO_INACTIVE =
            "BCI_E_AUDIO_INACTIVE"

        private const val BCI_E_NATIVE_PROBE =
            "BCI_E_NATIVE_PROBE"

        private const val BCI_E_SAMPLE_RATE =
            "BCI_E_SAMPLE_RATE"

        private const val BCI_E_CAPTURE_STALL =
            "BCI_E_CAPTURE_STALL"

        private const val BCI_E_BASELINE_STALL =
            "BCI_E_BASELINE_STALL"

        private const val BCI_E_LIVE_TRANSCRIPTION =
            "BCI_E_LIVE_TRANSCRIPTION"

        private const val BCI_E_BROADCAST =
            "BCI_E_BROADCAST"

        private const val BCI_E_ERROR_HANDLER =
            "BCI_E_ERROR_HANDLER"

        private const val BCI_W_AUDIO_XRUN =
            "BCI_W_AUDIO_XRUN"

        private const val BCI_W_NATIVE_RING_DROP =
            "BCI_W_NATIVE_RING_DROP"

        private const val BCI_W_BROADCAST_AUDIO_DROP =
            "BCI_W_BROADCAST_AUDIO_DROP"

        private const val BCI_W_BROADCAST_TRANSCRIPT_DROP =
            "BCI_W_BROADCAST_TRANSCRIPT_DROP"

        private const val CHANNEL_ID =
            "bciwhisper_audio"

        private const val NOTIFICATION_ID =
            5201

        private const val SAMPLE_RATE =
            44100

        private const val BLOCK_SIZE =
            2048

        private const val BASELINE_SECONDS =
            60.0
    }
}
