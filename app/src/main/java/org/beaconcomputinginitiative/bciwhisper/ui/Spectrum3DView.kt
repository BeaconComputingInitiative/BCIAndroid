package org.beaconcomputinginitiative.bciwhisper.ui

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View
import kotlin.math.*

class Spectrum3DView(
    context: Context
) : View(
    context
), Choreographer.FrameCallback {
    private val bands = 40
    private val depth = 20

    private val target =
        FloatArray(bands)

    private val current =
        FloatArray(bands)

    private val history =
        Array(depth) {
            FloatArray(bands)
        }

    private val waterfallPaths =
        Array(depth) {
            Path()
        }

    private var running = false
    private var suspended = false
    private var computeBusy = false

    private var phase = 0f
    private var lastFrameNanos = 0L

    private var lockLowHz = 0f
    private var lockHighHz = 0f
    private var sampleRate = 44100f

    private var backdropShader: Shader? = null
    private var radialShader: Shader? = null
    private var spectrumShader: Shader? = null

    private val cyan =
        Color.rgb(
            0,
            216,
            230
        )

    private val cyanSoft =
        Color.argb(
            100,
            0,
            216,
            230
        )

    private val maroon =
        Color.rgb(
            122,
            23,
            51
        )

    private val grid =
        Color.argb(
            46,
            120,
            185,
            198
        )

    private val bg =
        Color.rgb(
            7,
            10,
            13
        )

    private val paint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    private val line =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style =
                Paint.Style.STROKE
            strokeWidth = 1f
        }

    private val glow =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style =
                Paint.Style.STROKE
            strokeWidth = 2f
        }

    private val fill =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    init {
        setBackgroundColor(bg)
        contentDescription =
            "Animated three dimensional audio spectrum"
    }

    fun setSuspended(
        value: Boolean
    ) {
        if (suspended == value) {
            return
        }

        suspended = value

        if (suspended) {
            running = false
            lastFrameNanos = 0L

            Choreographer
                .getInstance()
                .removeFrameCallback(
                    this
                )
        } else {
            ensureRunning()
        }
    }

    fun setComputeBusy(
        value: Boolean
    ) {
        computeBusy = value
        ensureRunning()
    }

    fun submit(
        values: FloatArray,
        lockLowHz: Double,
        lockHighHz: Double,
        sampleRate: Int
    ) {
        val n =
            min(
                values.size,
                bands
            )

        for (i in 0 until n) {
            target[i] =
                values[i]
                    .coerceIn(
                        0f,
                        1f
                    )
        }

        for (i in n until bands) {
            target[i] = 0f
        }

        this.lockLowHz =
            lockLowHz.toFloat()

        this.lockHighHz =
            lockHighHz.toFloat()

        this.sampleRate =
            sampleRate.toFloat()

        ensureRunning()
    }

    private fun ensureRunning() {
        if (
            suspended ||
            running
        ) {
            return
        }

        running = true

        Choreographer
            .getInstance()
            .postFrameCallback(
                this
            )
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(
            w,
            h,
            oldw,
            oldh
        )

        if (
            w <= 0 ||
            h <= 0
        ) {
            return
        }

        val wf = w.toFloat()
        val hf = h.toFloat()

        backdropShader =
            LinearGradient(
                0f,
                0f,
                0f,
                hf,
                intArrayOf(
                    Color.rgb(
                        10,
                        18,
                        24
                    ),
                    Color.rgb(
                        5,
                        9,
                        13
                    ),
                    Color.rgb(
                        3,
                        5,
                        8
                    )
                ),
                floatArrayOf(
                    0f,
                    0.55f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

        radialShader =
            RadialGradient(
                wf * 0.5f,
                hf * 0.74f,
                wf * 0.62f,
                intArrayOf(
                    Color.argb(
                        34,
                        0,
                        216,
                        230
                    ),
                    Color.argb(
                        10,
                        122,
                        23,
                        51
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(
                    0f,
                    0.55f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

        spectrumShader =
            LinearGradient(
                0f,
                hf * 0.90f,
                0f,
                hf * 0.28f,
                maroon,
                cyan,
                Shader.TileMode.CLAMP
            )
    }

    override fun onDetachedFromWindow() {
        running = false
        lastFrameNanos = 0L

        Choreographer
            .getInstance()
            .removeFrameCallback(
                this
            )

        super.onDetachedFromWindow()
    }

    override fun doFrame(
        frameTimeNanos: Long
    ) {
        if (
            !running ||
            suspended
        ) {
            return
        }

        val frameInterval =
            if (computeBusy) {
                BUSY_FRAME_INTERVAL_NANOS
            } else {
                NORMAL_FRAME_INTERVAL_NANOS
            }

        if (
            lastFrameNanos != 0L &&
            frameTimeNanos -
                lastFrameNanos <
                frameInterval
        ) {
            Choreographer
                .getInstance()
                .postFrameCallback(
                    this
                )

            return
        }

        val dt =
            if (lastFrameNanos == 0L) {
                1f /
                    if (computeBusy) {
                        BUSY_FPS
                    } else {
                        NORMAL_FPS
                    }
            } else {
                (
                    (
                        frameTimeNanos -
                            lastFrameNanos
                        ) /
                        1_000_000_000.0
                    )
                    .toFloat()
                    .coerceIn(
                        0.001f,
                        0.075f
                    )
            }

        lastFrameNanos =
            frameTimeNanos

        val attack =
            1f -
                exp(
                    -dt *
                        18f
                )

        val release =
            1f -
                exp(
                    -dt *
                        6f
                )

        for (i in 0 until bands) {
            val smoothing =
                if (
                    target[i] >
                    current[i]
                ) {
                    attack
                } else {
                    release
                }

            current[i] +=
                (
                    target[i] -
                        current[i]
                    ) *
                    smoothing
        }

        phase += dt

        if (
            (
                phase *
                    HISTORY_HZ
                ).toInt() !=
            (
                (phase - dt) *
                    HISTORY_HZ
                ).toInt()
        ) {
            for (
                z in
                depth - 1 downTo 1
            ) {
                System.arraycopy(
                    history[z - 1],
                    0,
                    history[z],
                    0,
                    bands
                )
            }

            System.arraycopy(
                current,
                0,
                history[0],
                0,
                bands
            )
        }

        postInvalidateOnAnimation()

        Choreographer
            .getInstance()
            .postFrameCallback(
                this
            )
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        if (
            w <= 1f ||
            h <= 1f
        ) {
            return
        }

        drawBackdrop(
            canvas,
            w,
            h
        )

        drawPerspectiveGrid(
            canvas,
            w,
            h
        )

        drawWaterfall(
            canvas,
            w,
            h
        )

        drawFrontSpectrum(
            canvas,
            w,
            h
        )

        drawLockBand(
            canvas,
            w,
            h
        )
    }

    private fun drawBackdrop(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        fill.shader =
            backdropShader

        canvas.drawRect(
            0f,
            0f,
            w,
            h,
            fill
        )

        fill.shader =
            radialShader

        canvas.drawRect(
            0f,
            0f,
            w,
            h,
            fill
        )

        fill.shader = null
    }

    private fun drawPerspectiveGrid(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val horizonY =
            h * 0.20f

        val floorY =
            h * 0.92f

        val centerX =
            w * 0.5f

        line.color = grid

        line.strokeWidth =
            max(
                1f,
                resources
                    .displayMetrics
                    .density *
                    0.7f
            )

        for (i in 0..10) {
            val x =
                w *
                    i /
                    10f

            val horizonX =
                centerX +
                    (
                        x -
                            centerX
                        ) *
                        0.18f

            canvas.drawLine(
                x,
                floorY,
                horizonX,
                horizonY,
                line
            )
        }

        for (i in 0..8) {
            val t =
                i /
                    8f

            val curved =
                t * t

            val y =
                horizonY +
                    (
                        floorY -
                            horizonY
                        ) *
                        curved

            val inset =
                w *
                    0.40f *
                    (
                        1f -
                            curved
                        )

            canvas.drawLine(
                inset,
                y,
                w - inset,
                y,
                line
            )
        }
    }

    private fun drawWaterfall(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val horizonY =
            h * 0.20f

        val floorY =
            h * 0.88f

        val centerX =
            w * 0.5f

        for (
            z in
            depth - 1 downTo 0
        ) {
            val depthT =
                z.toFloat() /
                    (
                        depth - 1
                        )
                        .coerceAtLeast(
                            1
                        )

            val perspective =
                1f -
                    depthT

            val yBase =
                horizonY +
                    (
                        floorY -
                            horizonY
                        ) *
                        perspective
                            .pow(
                                1.7f
                            )

            val span =
                w *
                    (
                        0.18f +
                            0.80f *
                                perspective
                        )

            val left =
                centerX -
                    span /
                        2f

            val bandW =
                span /
                    bands

            val ampScale =
                h *
                    (
                        0.045f +
                            0.26f *
                                perspective
                        )

            val alpha =
                (
                    22 +
                        118 *
                            perspective
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        180
                    )

            val row =
                history[z]

            val path =
                waterfallPaths[z]

            path.reset()

            for (i in 0 until bands) {
                val x =
                    left +
                        (
                            i +
                                0.5f
                            ) *
                            bandW

                val y =
                    yBase -
                        row[i] *
                            ampScale

                if (i == 0) {
                    path.moveTo(
                        x,
                        y
                    )
                } else {
                    path.lineTo(
                        x,
                        y
                    )
                }
            }

            line.color =
                Color.argb(
                    alpha,
                    0,
                    216,
                    230
                )

            line.strokeWidth =
                1f +
                    1.5f *
                        perspective

            canvas.drawPath(
                path,
                line
            )
        }
    }

    private fun drawFrontSpectrum(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val left =
            w * 0.05f

        val right =
            w * 0.95f

        val bottom =
            h * 0.90f

        val top =
            h * 0.28f

        val usable =
            right -
                left

        val bandW =
            usable /
                bands

        fill.shader =
            spectrumShader

        fill.alpha = 205

        glow.color =
            cyanSoft

        glow.strokeWidth =
            max(
                1f,
                bandW *
                    0.06f
            )

        for (i in 0 until bands) {
            val value =
                current[i]
                    .coerceIn(
                        0f,
                        1f
                    )

            val barHeight =
                (
                    bottom -
                        top
                    ) *
                    value

            val x0 =
                left +
                    i *
                        bandW +
                    bandW *
                        0.10f

            val x1 =
                left +
                    (
                        i +
                            1
                        ) *
                        bandW -
                    bandW *
                        0.10f

            val y0 =
                bottom -
                    barHeight

            canvas.drawRoundRect(
                x0,
                y0,
                x1,
                bottom,
                bandW * 0.18f,
                bandW * 0.18f,
                fill
            )

            canvas.drawLine(
                (
                    x0 +
                        x1
                    ) /
                    2f,
                y0,
                (
                    x0 +
                        x1
                    ) /
                    2f,
                bottom,
                glow
            )
        }

        fill.shader = null

        paint.color =
            Color.argb(
                210,
                215,
                244,
                248
            )

        paint.textSize =
            10f *
                resources
                    .displayMetrics
                    .scaledDensity

        paint.typeface =
            Typeface.MONOSPACE

        canvas.drawText(
            "80 Hz",
            left,
            h * 0.975f,
            paint
        )

        val label =
            "8.5 kHz"

        canvas.drawText(
            label,
            right -
                paint.measureText(
                    label
                ),
            h * 0.975f,
            paint
        )
    }

    private fun drawLockBand(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        if (
            lockHighHz <=
                lockLowHz ||
            sampleRate <=
                0f
        ) {
            return
        }

        val lowLimit =
            80f

        val highLimit =
            min(
                8500f,
                sampleRate /
                    2f
            )

        val low =
            (
                (
                    lockLowHz -
                        lowLimit
                    ) /
                    (
                        highLimit -
                            lowLimit
                        )
                )
                .coerceIn(
                    0f,
                    1f
                )

        val high =
            (
                (
                    lockHighHz -
                        lowLimit
                    ) /
                    (
                        highLimit -
                            lowLimit
                        )
                )
                .coerceIn(
                    0f,
                    1f
                )

        val left =
            w * 0.05f

        val right =
            w * 0.95f

        val x0 =
            left +
                (
                    right -
                        left
                    ) *
                    low

        val x1 =
            left +
                (
                    right -
                        left
                    ) *
                    high

        fill.color =
            Color.argb(
                24,
                122,
                23,
                51
            )

        canvas.drawRect(
            x0,
            h * 0.22f,
            x1,
            h * 0.91f,
            fill
        )

        line.color =
            Color.argb(
                210,
                255,
                57,
                101
            )

        line.strokeWidth =
            2f *
                resources
                    .displayMetrics
                    .density

        canvas.drawLine(
            x0,
            h * 0.22f,
            x0,
            h * 0.91f,
            line
        )

        canvas.drawLine(
            x1,
            h * 0.22f,
            x1,
            h * 0.91f,
            line
        )

        paint.color =
            Color.rgb(
                255,
                83,
                119
            )

        paint.textSize =
            9f *
                resources
                    .displayMetrics
                    .scaledDensity

        paint.typeface =
            Typeface.MONOSPACE

        val text =
            "${lockLowHz.roundToInt()}–${lockHighHz.roundToInt()} Hz"

        canvas.drawText(
            text,
            x0 + 6f,
            h * 0.255f,
            paint
        )
    }

    companion object {
        private const val NORMAL_FPS = 30f
        private const val BUSY_FPS = 20f

        private const val NORMAL_FRAME_INTERVAL_NANOS =
            1_000_000_000L /
                30L

        private const val BUSY_FRAME_INTERVAL_NANOS =
            1_000_000_000L /
                20L

        private const val HISTORY_HZ = 18f
    }
}
