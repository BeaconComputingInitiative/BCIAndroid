/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid OpenGL ES Spectrum Renderer
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Choreographer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

class Spectrum3DView(
    context: Context
) : GLSurfaceView(
    context
), Choreographer.FrameCallback {
    private val renderer =
        SpectrumRenderer()

    private var running =
        false

    private var suspended =
        false

    private var computeBusy =
        false

    private var lastFrameNanos =
        0L

    init {
        setEGLContextClientVersion(
            2
        )

        setRenderer(
            renderer
        )

        renderMode =
            RENDERMODE_WHEN_DIRTY

        preserveEGLContextOnPause =
            true

        contentDescription =
            "GPU accelerated three dimensional audio spectrum"
    }

    fun setSuspended(
        value: Boolean
    ) {
        if (
            suspended ==
            value
        ) {
            return
        }

        suspended =
            value

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
        computeBusy =
            value

        ensureRunning()
    }

    fun submit(
        values: FloatArray,
        lockLowHz: Double,
        lockHighHz: Double,
        sampleRate: Int
    ) {
        renderer.submit(
            values,
            lockLowHz.toFloat(),
            lockHighHz.toFloat(),
            sampleRate.toFloat()
        )

        ensureRunning()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureRunning()
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

        val interval =
            if (computeBusy) {
                BUSY_FRAME_INTERVAL_NANOS
            } else {
                NORMAL_FRAME_INTERVAL_NANOS
            }

        if (
            lastFrameNanos ==
                0L ||
            frameTimeNanos -
                lastFrameNanos >=
            interval
        ) {
            lastFrameNanos =
                frameTimeNanos

            requestRender()
        }

        Choreographer
            .getInstance()
            .postFrameCallback(
                this
            )
    }

    private fun ensureRunning() {
        if (
            suspended ||
            running ||
            !isAttachedToWindow
        ) {
            return
        }

        running =
            true

        Choreographer
            .getInstance()
            .postFrameCallback(
                this
            )
    }

    private class SpectrumRenderer :
        Renderer {
        private val target =
            FloatArray(
                BANDS
            )

        private val snapshot =
            FloatArray(
                BANDS
            )

        private val current =
            FloatArray(
                BANDS
            )

        private val history =
            Array(
                DEPTH
            ) {
                FloatArray(
                    BANDS
                )
            }

        private val stateLock =
            Any()

        private val scratch =
            FloatArray(
                MAX_VERTEX_FLOATS
            )

        private val vertexBuffer:
            FloatBuffer =
            ByteBuffer
                .allocateDirect(
                    MAX_VERTEX_FLOATS *
                        4
                )
                .order(
                    ByteOrder.nativeOrder()
                )
                .asFloatBuffer()

        private var lockLowHz =
            0f

        private var lockHighHz =
            0f

        private var sampleRate =
            44_100f

        private var program =
            0

        private var positionHandle =
            0

        private var colorHandle =
            0

        private var lastRenderNanos =
            0L

        private var historyAccumulator =
            0f

        fun submit(
            values: FloatArray,
            lowHz: Float,
            highHz: Float,
            rate: Float
        ) {
            synchronized(
                stateLock
            ) {
                val count =
                    min(
                        values.size,
                        BANDS
                    )

                for (
                    index in
                    0 until count
                ) {
                    target[
                        index
                    ] =
                        values[
                            index
                        ]
                            .coerceIn(
                                0f,
                                1f
                            )
                }

                for (
                    index in
                    count until BANDS
                ) {
                    target[
                        index
                    ] =
                        0f
                }

                lockLowHz =
                    lowHz

                lockHighHz =
                    highHz

                sampleRate =
                    rate
            }
        }

        override fun onSurfaceCreated(
            gl: GL10?,
            config: EGLConfig?
        ) {
            program =
                buildProgram(
                    VERTEX_SHADER,
                    FRAGMENT_SHADER
                )

            positionHandle =
                GLES20
                    .glGetAttribLocation(
                        program,
                        "aPosition"
                    )

            colorHandle =
                GLES20
                    .glGetUniformLocation(
                        program,
                        "uColor"
                    )

            GLES20.glEnable(
                GLES20.GL_BLEND
            )

            GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
            )

            GLES20.glClearColor(
                0.027f,
                0.039f,
                0.051f,
                1f
            )
        }

        override fun onSurfaceChanged(
            gl: GL10?,
            width: Int,
            height: Int
        ) {
            GLES20.glViewport(
                0,
                0,
                width,
                height
            )
        }

        override fun onDrawFrame(
            gl: GL10?
        ) {
            GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
            )

            if (
                program ==
                0
            ) {
                return
            }

            updateAnimation()

            GLES20.glUseProgram(
                program
            )

            drawPerspectiveGrid()
            drawWaterfall()
            drawFrontBars()
            drawLockBand()
        }

        private fun updateAnimation() {
            val now =
                System.nanoTime()

            val dt =
                if (
                    lastRenderNanos ==
                    0L
                ) {
                    1f /
                        30f
                } else {
                    (
                        (
                            now -
                                lastRenderNanos
                            ) /
                            1_000_000_000.0
                        )
                        .toFloat()
                        .coerceIn(
                            0.001f,
                            0.075f
                        )
                }

            lastRenderNanos =
                now

            synchronized(
                stateLock
            ) {
                System.arraycopy(
                    target,
                    0,
                    snapshot,
                    0,
                    BANDS
                )
            }

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

            for (
                index in
                0 until BANDS
            ) {
                val smoothing =
                    if (
                        snapshot[
                            index
                        ] >
                        current[
                            index
                        ]
                    ) {
                        attack
                    } else {
                        release
                    }

                current[
                    index
                ] +=
                    (
                        snapshot[
                            index
                        ] -
                            current[
                                index
                            ]
                        ) *
                        smoothing
            }

            historyAccumulator +=
                dt

            val historyPeriod =
                1f /
                    HISTORY_HZ

            if (
                historyAccumulator >=
                historyPeriod
            ) {
                historyAccumulator %=
                    historyPeriod

                for (
                    depth in
                    DEPTH -
                        1 downTo 1
                ) {
                    System.arraycopy(
                        history[
                            depth -
                                1
                        ],
                        0,
                        history[
                            depth
                        ],
                        0,
                        BANDS
                    )
                }

                System.arraycopy(
                    current,
                    0,
                    history[
                        0
                    ],
                    0,
                    BANDS
                )
            }
        }

        private fun drawPerspectiveGrid() {
            var cursor =
                0

            for (
                index in
                0..10
            ) {
                val x =
                    -1f +
                        2f *
                            index /
                            10f

                val horizonX =
                    x *
                        0.18f

                scratch[
                    cursor++
                ] =
                    x
                scratch[
                    cursor++
                ] =
                    -0.84f
                scratch[
                    cursor++
                ] =
                    horizonX
                scratch[
                    cursor++
                ] =
                    0.60f
            }

            for (
                index in
                0..8
            ) {
                val t =
                    index /
                        8f

                val curved =
                    t *
                        t

                val y =
                    0.60f -
                        1.44f *
                            curved

                val inset =
                    0.80f *
                        (
                            1f -
                                curved
                            )

                scratch[
                    cursor++
                ] =
                    -1f +
                        inset
                scratch[
                    cursor++
                ] =
                    y
                scratch[
                    cursor++
                ] =
                    1f -
                        inset
                scratch[
                    cursor++
                ] =
                    y
            }

            drawScratch(
                cursor,
                GLES20.GL_LINES,
                0.28f,
                0.55f,
                0.62f,
                0.28f
            )
        }

        private fun drawWaterfall() {
            var cursor =
                0

            for (
                depth in
                DEPTH -
                    1 downTo 0
            ) {
                val depthT =
                    depth.toFloat() /
                        (
                            DEPTH -
                                1
                            )
                            .coerceAtLeast(
                                1
                            )

                val perspective =
                    1f -
                        depthT

                val yBase =
                    0.60f -
                        1.34f *
                            perspective
                                .pow(
                                    1.7f
                                )

                val span =
                    0.34f +
                        1.56f *
                            perspective

                val left =
                    -span /
                        2f

                val amp =
                    0.08f +
                        0.50f *
                            perspective

                val row =
                    history[
                        depth
                    ]

                for (
                    index in
                    0 until
                        BANDS -
                            1
                ) {
                    val x0 =
                        left +
                            span *
                                index /
                                (
                                    BANDS -
                                        1
                                    )

                    val x1 =
                        left +
                            span *
                                (
                                    index +
                                        1
                                    ) /
                                (
                                    BANDS -
                                        1
                                    )

                    scratch[
                        cursor++
                    ] =
                        x0
                    scratch[
                        cursor++
                    ] =
                        yBase +
                            row[
                                index
                            ] *
                            amp

                    scratch[
                        cursor++
                    ] =
                        x1
                    scratch[
                        cursor++
                    ] =
                        yBase +
                            row[
                                index +
                                    1
                            ] *
                            amp
                }
            }

            drawScratch(
                cursor,
                GLES20.GL_LINES,
                0.0f,
                0.85f,
                0.90f,
                0.36f
            )
        }

        private fun drawFrontBars() {
            val left =
                -0.90f

            val right =
                0.90f

            val bottom =
                -0.80f

            val height =
                1.12f

            val width =
                (
                    right -
                        left
                    ) /
                    BANDS

            var cursor =
                0

            for (
                index in
                0 until BANDS
            ) {
                val value =
                    current[
                        index
                    ]
                        .coerceIn(
                            0f,
                            1f
                        )

                val x0 =
                    left +
                        index *
                            width +
                        width *
                            0.10f

                val x1 =
                    left +
                        (
                            index +
                                1
                            ) *
                            width -
                        width *
                            0.10f

                val y1 =
                    bottom +
                        value *
                            height

                scratch[
                    cursor++
                ] =
                    x0
                scratch[
                    cursor++
                ] =
                    bottom
                scratch[
                    cursor++
                ] =
                    x1
                scratch[
                    cursor++
                ] =
                    bottom
                scratch[
                    cursor++
                ] =
                    x0
                scratch[
                    cursor++
                ] =
                    y1

                scratch[
                    cursor++
                ] =
                    x0
                scratch[
                    cursor++
                ] =
                    y1
                scratch[
                    cursor++
                ] =
                    x1
                scratch[
                    cursor++
                ] =
                    bottom
                scratch[
                    cursor++
                ] =
                    x1
                scratch[
                    cursor++
                ] =
                    y1
            }

            drawScratch(
                cursor,
                GLES20.GL_TRIANGLES,
                0.0f,
                0.85f,
                0.90f,
                0.86f
            )
        }

        private fun drawLockBand() {
            val low: Float
            val high: Float
            val rate: Float

            synchronized(
                stateLock
            ) {
                low =
                    lockLowHz
                high =
                    lockHighHz
                rate =
                    sampleRate
            }

            if (
                high <=
                    low ||
                rate <=
                    0f
            ) {
                return
            }

            val lowLimit =
                80f

            val highLimit =
                min(
                    8500f,
                    rate /
                        2f
                )

            if (
                highLimit <=
                    lowLimit
            ) {
                return
            }

            val lowN =
                (
                    (
                        low -
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

            val highN =
                (
                    (
                        high -
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

            val x0 =
                -0.90f +
                    1.80f *
                        lowN

            val x1 =
                -0.90f +
                    1.80f *
                        highN

            scratch[
                0
            ] =
                x0
            scratch[
                1
            ] =
                -0.82f
            scratch[
                2
            ] =
                x1
            scratch[
                3
            ] =
                -0.82f
            scratch[
                4
            ] =
                x0
            scratch[
                5
            ] =
                0.58f
            scratch[
                6
            ] =
                x0
            scratch[
                7
            ] =
                0.58f
            scratch[
                8
            ] =
                x1
            scratch[
                9
            ] =
                -0.82f
            scratch[
                10
            ] =
                x1
            scratch[
                11
            ] =
                0.58f

            drawScratch(
                12,
                GLES20.GL_TRIANGLES,
                0.68f,
                0.08f,
                0.20f,
                0.18f
            )

            scratch[
                0
            ] =
                x0
            scratch[
                1
            ] =
                -0.82f
            scratch[
                2
            ] =
                x0
            scratch[
                3
            ] =
                0.58f
            scratch[
                4
            ] =
                x1
            scratch[
                5
            ] =
                -0.82f
            scratch[
                6
            ] =
                x1
            scratch[
                7
            ] =
                0.58f

            drawScratch(
                8,
                GLES20.GL_LINES,
                1.0f,
                0.25f,
                0.42f,
                0.90f
            )
        }

        private fun drawScratch(
            floatCount: Int,
            primitive: Int,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float
        ) {
            if (
                floatCount <=
                    0
            ) {
                return
            }

            vertexBuffer
                .clear()

            vertexBuffer
                .put(
                    scratch,
                    0,
                    floatCount
                )

            vertexBuffer
                .position(
                    0
                )

            GLES20.glUniform4f(
                colorHandle,
                red,
                green,
                blue,
                alpha
            )

            GLES20.glEnableVertexAttribArray(
                positionHandle
            )

            GLES20.glVertexAttribPointer(
                positionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            GLES20.glDrawArrays(
                primitive,
                0,
                floatCount /
                    2
            )

            GLES20.glDisableVertexAttribArray(
                positionHandle
            )
        }

        private fun buildProgram(
            vertex: String,
            fragment: String
        ): Int {
            val vertexShader =
                compileShader(
                    GLES20.GL_VERTEX_SHADER,
                    vertex
                )

            val fragmentShader =
                compileShader(
                    GLES20.GL_FRAGMENT_SHADER,
                    fragment
                )

            val result =
                GLES20.glCreateProgram()

            GLES20.glAttachShader(
                result,
                vertexShader
            )

            GLES20.glAttachShader(
                result,
                fragmentShader
            )

            GLES20.glLinkProgram(
                result
            )

            val linked =
                IntArray(
                    1
                )

            GLES20.glGetProgramiv(
                result,
                GLES20.GL_LINK_STATUS,
                linked,
                0
            )

            if (
                linked[
                    0
                ] ==
                0
            ) {
                val message =
                    GLES20
                        .glGetProgramInfoLog(
                            result
                        )

                GLES20.glDeleteProgram(
                    result
                )

                error(
                    "OpenGL ES program link failed: " +
                        message
                )
            }

            GLES20.glDeleteShader(
                vertexShader
            )

            GLES20.glDeleteShader(
                fragmentShader
            )

            return result
        }

        private fun compileShader(
            type: Int,
            source: String
        ): Int {
            val shader =
                GLES20.glCreateShader(
                    type
                )

            GLES20.glShaderSource(
                shader,
                source
            )

            GLES20.glCompileShader(
                shader
            )

            val compiled =
                IntArray(
                    1
                )

            GLES20.glGetShaderiv(
                shader,
                GLES20.GL_COMPILE_STATUS,
                compiled,
                0
            )

            if (
                compiled[
                    0
                ] ==
                0
            ) {
                val message =
                    GLES20
                        .glGetShaderInfoLog(
                            shader
                        )

                GLES20.glDeleteShader(
                    shader
                )

                error(
                    "OpenGL ES shader compile failed: " +
                        message
                )
            }

            return shader
        }
    }

    companion object {
        private const val BANDS =
            40

        private const val DEPTH =
            20

        private const val MAX_VERTEX_FLOATS =
            DEPTH *
                (
                    BANDS -
                        1
                    ) *
                4

        private const val NORMAL_FRAME_INTERVAL_NANOS =
            1_000_000_000L /
                30L

        private const val BUSY_FRAME_INTERVAL_NANOS =
            1_000_000_000L /
                20L

        private const val HISTORY_HZ =
            18f

        private const val VERTEX_SHADER =
            """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """

        private const val FRAGMENT_SHADER =
            """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
            """
    }
}
