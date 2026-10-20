/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Phonetic Reconstruction Engine
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline transcription-preparation stage implementing the second and third
 * BCI voice-recognition approaches:
 *
 *  2. STFT/Wiener reconstruction with harmonic protection.
 *  3. LPC spectral-envelope protection for likely formant structure.
 *
 * This class never touches the preserved WAV. It operates only on the
 * transcription inference copy after dead-air planning and resampling.
 */
class PhoneticReconstructionEngine {
    data class Metrics(
        val inputRmsDbfs: Double,
        val outputRmsDbfs: Double,
        val averageNoiseReductionDb: Double,
        val formantProtectedBins: Long,
        val harmonicProtectedBins: Long
    )

    data class Output(
        val samples: FloatArray,
        val metrics: Metrics
    )

    fun enhance(
        input: FloatArray,
        sampleRate: Int
    ): Output {
        if (
            input.isEmpty() ||
            input.size < NFFT
        ) {
            return Output(
                samples = input.copyOf(),
                metrics = Metrics(
                    inputRmsDbfs = rmsDbfs(input),
                    outputRmsDbfs = rmsDbfs(input),
                    averageNoiseReductionDb = 0.0,
                    formantProtectedBins = 0L,
                    harmonicProtectedBins = 0L
                )
            )
        }

        val fft =
            DoubleFFT_1D(
                NFFT.toLong()
            )

        val window =
            HANN_WINDOW

        val frameCount =
            1 +
                max(
                    0,
                    (
                        input.size -
                            NFFT +
                            HOP -
                            1
                        ) /
                        HOP
                )

        val spectra =
            Array(
                frameCount
            ) {
                DoubleArray(
                    NFFT * 2
                )
            }

        val powers =
            Array(
                frameCount
            ) {
                DoubleArray(
                    NFFT / 2 +
                        1
                )
            }

        for (
            frame in
            0 until frameCount
        ) {
            val start =
                frame *
                    HOP

            val spectrum =
                spectra[frame]

            for (
                i in
                0 until NFFT
            ) {
                val index =
                    start + i

                spectrum[i] =
                    if (
                        index <
                        input.size
                    ) {
                        input[index] *
                            window[i]
                    } else {
                        0.0
                    }
            }

            fft.realForwardFull(
                spectrum
            )

            for (
                bin in
                powers[frame].indices
            ) {
                val re =
                    spectrum[
                        2 * bin
                    ]

                val im =
                    spectrum[
                        2 * bin +
                            1
                    ]

                powers[frame][bin] =
                    re * re +
                        im * im
            }
        }

        val noise =
            estimateNoise(
                powers
            )

        val output =
            DoubleArray(
                input.size +
                    NFFT
            )

        val normalization =
            DoubleArray(
                output.size
            )

        var totalGainReductionDb =
            0.0

        var gainSamples =
            0L

        var formantProtected =
            0L

        var harmonicProtected =
            0L

        for (
            frame in
            0 until frameCount
        ) {
            val spectrum =
                spectra[frame]

            val power =
                powers[frame]

            val frameSamples =
                DoubleArray(
                    NFFT
                )

            val start =
                frame *
                    HOP

            for (
                i in
                0 until NFFT
            ) {
                val index =
                    start + i

                frameSamples[i] =
                    if (
                        index <
                        input.size
                    ) {
                        input[index]
                            .toDouble()
                    } else {
                        0.0
                    }
            }

            val lpc =
                lpcCoefficients(
                    frameSamples,
                    LPC_ORDER
                )

            val envelope =
                lpcEnvelope(
                    lpc,
                    sampleRate
                )

            val envelopeThreshold =
                percentile(
                    envelope,
                    FORMANT_ENVELOPE_PERCENTILE
                )

            for (
                bin in
                power.indices
            ) {
                val hz =
                    bin.toDouble() *
                        sampleRate /
                        NFFT

                val floor =
                    noise[bin]
                        .coerceAtLeast(
                            MIN_POWER
                        )

                val residual =
                    max(
                        0.0,
                        power[bin] -
                            WIENER_OVERSUBTRACT *
                                floor
                    )

                val snr =
                    residual /
                        floor

                var gain =
                    sqrt(
                        snr /
                            (
                                1.0 +
                                    snr
                                )
                    )

                gain =
                    SPECTRAL_FLOOR +
                        (
                            1.0 -
                                SPECTRAL_FLOOR
                            ) *
                        gain

                val harmonic =
                    isProtectedHarmonic(
                        power,
                        noise,
                        bin,
                        hz
                    )

                if (harmonic) {
                    gain =
                        max(
                            gain,
                            HARMONIC_GAIN_FLOOR
                        )

                    harmonicProtected++
                }

                val formantLike =
                    hz in
                        FORMANT_LOW_HZ..
                            FORMANT_HIGH_HZ &&
                        envelope[bin] >=
                            envelopeThreshold

                if (formantLike) {
                    gain =
                        max(
                            gain,
                            FORMANT_GAIN_FLOOR
                        )

                    formantProtected++
                }

                // High-frequency frication has weak periodic structure, so
                // preserve a conservative floor even when it is not harmonic.
                if (
                    hz in
                    FRICATIVE_LOW_HZ..
                        FRICATIVE_HIGH_HZ
                ) {
                    gain =
                        max(
                            gain,
                            FRICATIVE_GAIN_FLOOR
                        )
                }

                val speechBandGain =
                    when {
                        hz <
                            55.0 ->
                            0.20

                        hz <
                            75.0 ->
                            0.60

                        hz <=
                            7800.0 ->
                            1.0

                        hz <=
                            8000.0 ->
                            0.82

                        else ->
                            0.35
                    }

                val finalGain =
                    (
                        gain *
                            speechBandGain
                        )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                totalGainReductionDb +=
                    -20.0 *
                        log10(
                            finalGain
                                .coerceAtLeast(
                                    1e-6
                                )
                        )

                gainSamples++

                spectrum[
                    2 * bin
                ] *=
                    finalGain

                spectrum[
                    2 * bin +
                        1
                ] *=
                    finalGain

                if (
                    bin != 0 &&
                    bin !=
                        NFFT / 2
                ) {
                    val mirror =
                        NFFT -
                            bin

                    spectrum[
                        2 * mirror
                    ] *=
                        finalGain

                    spectrum[
                        2 * mirror +
                            1
                    ] *=
                        finalGain
                }
            }

            fft.complexInverse(
                spectrum,
                true
            )

            for (
                i in
                0 until NFFT
            ) {
                val index =
                    start + i

                if (
                    index >=
                    output.size
                ) {
                    break
                }

                val value =
                    spectrum[
                        2 * i
                    ] *
                        window[i]

                output[index] +=
                    value

                normalization[index] +=
                    window[i] *
                        window[i]
            }
        }

        val reconstructed =
            FloatArray(
                input.size
            ) { i ->
                val value =
                    if (
                        normalization[i] >
                        1e-12
                    ) {
                        output[i] /
                            normalization[i]
                    } else {
                        0.0
                    }

                value
                    .coerceIn(
                        -1.0,
                        1.0
                    )
                    .toFloat()
            }

        return Output(
            samples =
                reconstructed,
            metrics =
                Metrics(
                    inputRmsDbfs =
                        rmsDbfs(
                            input
                        ),
                    outputRmsDbfs =
                        rmsDbfs(
                            reconstructed
                        ),
                    averageNoiseReductionDb =
                        if (
                            gainSamples >
                            0
                        ) {
                            totalGainReductionDb /
                                gainSamples
                        } else {
                            0.0
                        },
                    formantProtectedBins =
                        formantProtected,
                    harmonicProtectedBins =
                        harmonicProtected
                )
        )
    }

    private fun estimateNoise(
        powers: Array<DoubleArray>
    ): DoubleArray {
        val bins =
            powers[0].size

        val noise =
            DoubleArray(
                bins
            )

        val sampledFrames =
            (
                powers.size +
                    NOISE_FRAME_STRIDE -
                    1
                ) /
                NOISE_FRAME_STRIDE

        val scratch =
            DoubleArray(
                sampledFrames
            )

        for (
            bin in
            0 until bins
        ) {
            var sample =
                0

            var frame =
                0

            while (
                frame <
                powers.size
            ) {
                scratch[sample] =
                    powers[frame][bin]

                sample++
                frame +=
                    NOISE_FRAME_STRIDE
            }

            java.util.Arrays.sort(
                scratch,
                0,
                sample
            )

            val index =
                (
                    (
                        sample -
                            1
                        ) *
                        NOISE_PERCENTILE
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        sample -
                            1
                    )

            noise[bin] =
                scratch[index]
                    .coerceAtLeast(
                        MIN_POWER
                    )
        }

        return noise
    }

    private fun isProtectedHarmonic(
        power: DoubleArray,
        noise: DoubleArray,
        bin: Int,
        hz: Double
    ): Boolean {
        if (
            bin <= 0 ||
            bin >=
                power.lastIndex ||
            hz <
                HARMONIC_LOW_HZ ||
            hz >
                HARMONIC_HIGH_HZ
        ) {
            return false
        }

        val localPeak =
            power[bin] >
                power[
                    bin - 1
                ] &&
                power[bin] >=
                power[
                    bin + 1
                ]

        val aboveNoise =
            power[bin] >=
                HARMONIC_NOISE_RATIO *
                    noise[bin]
                        .coerceAtLeast(
                            MIN_POWER
                        )

        return localPeak &&
            aboveNoise
    }

    /**
     * Autocorrelation LPC solved with Levinson-Durbin recursion.
     *
     * A light pre-emphasis is used only for LPC estimation; the audio itself
     * is never pre-emphasized. This keeps the estimated envelope sensitive to
     * formant structure without coloring the inference samples.
     */
    private fun lpcCoefficients(
        frame: DoubleArray,
        order: Int
    ): DoubleArray {
        if (
            frame.isEmpty()
        ) {
            return doubleArrayOf(
                1.0
            )
        }

        val emphasized =
            DoubleArray(
                frame.size
            )

        emphasized[0] =
            frame[0]

        for (
            i in
            1 until frame.size
        ) {
            emphasized[i] =
                frame[i] -
                    LPC_PREEMPHASIS *
                        frame[
                            i - 1
                        ]
        }

        val autocorrelation =
            DoubleArray(
                order +
                    1
            )

        for (
            lag in
            0..order
        ) {
            var sum =
                0.0

            for (
                i in
                lag until emphasized.size
            ) {
                sum +=
                    emphasized[i] *
                        emphasized[
                            i - lag
                        ]
            }

            autocorrelation[lag] =
                sum
        }

        if (
            autocorrelation[0] <=
            1e-12
        ) {
            return DoubleArray(
                order +
                    1
            ).apply {
                this[0] =
                    1.0
            }
        }

        var error =
            autocorrelation[0]

        val a =
            DoubleArray(
                order +
                    1
            )

        a[0] =
            1.0

        for (
            i in
            1..order
        ) {
            var numerator =
                autocorrelation[i]

            for (
                j in
                1 until i
            ) {
                numerator +=
                    a[j] *
                        autocorrelation[
                            i - j
                        ]
            }

            val reflection =
                (
                    -numerator /
                        error
                    )
                    .coerceIn(
                        -0.985,
                        0.985
                    )

            val previous =
                a.copyOf()

            a[i] =
                reflection

            for (
                j in
                1 until i
            ) {
                a[j] =
                    previous[j] +
                        reflection *
                            previous[
                                i - j
                            ]
            }

            error *=
                1.0 -
                    reflection *
                        reflection

            if (
                error <=
                1e-12
            ) {
                break
            }
        }

        return a
    }

    /**
     * |H(e^jw)|^2 = 1 / |A(e^jw)|^2
     *
     * We only need the relative LPC envelope, not an absolute vocal-tract
     * transfer function. The normalized envelope is used as a protection mask.
     */
    private fun lpcEnvelope(
        coefficients: DoubleArray,
        sampleRate: Int
    ): DoubleArray {
        val bins =
            NFFT / 2 +
                1

        val envelope =
            DoubleArray(
                bins
            )

        var maximum =
            MIN_POWER

        for (
            bin in
            0 until bins
        ) {
            var real =
                0.0

            var imag =
                0.0

            val cosRow =
                LPC_COS_BASIS[bin]

            val sinRow =
                LPC_SIN_BASIS[bin]

            val order =
                minOf(
                    coefficients.size,
                    cosRow.size
                )

            for (
                k in
                0 until order
            ) {
                real +=
                    coefficients[k] *
                        cosRow[k]

                imag -=
                    coefficients[k] *
                        sinRow[k]
            }

            val magnitudeSquared =
                real *
                    real +
                    imag *
                        imag

            val response =
                1.0 /
                    magnitudeSquared
                        .coerceAtLeast(
                            MIN_POWER
                        )

            val hz =
                bin.toDouble() *
                    sampleRate /
                    NFFT

            envelope[bin] =
                if (
                    hz in
                    FORMANT_LOW_HZ..
                        FORMANT_HIGH_HZ
                ) {
                    response
                } else {
                    0.0
                }

            maximum =
                max(
                    maximum,
                    envelope[bin]
                )
        }

        if (
            maximum >
            MIN_POWER
        ) {
            for (
                i in
                envelope.indices
            ) {
                envelope[i] /=
                    maximum
            }
        }

        return envelope
    }

    private fun percentile(
        values: DoubleArray,
        q: Double
    ): Double {
        if (
            values.isEmpty()
        ) {
            return 0.0
        }

        val histogram =
            IntArray(
                ENVELOPE_HISTOGRAM_BINS
            )

        var count =
            0

        for (
            value in
            values
        ) {
            if (
                value <=
                0.0
            ) {
                continue
            }

            val bucket =
                (
                    value
                        .coerceIn(
                            0.0,
                            1.0
                        ) *
                        (
                            ENVELOPE_HISTOGRAM_BINS -
                                1
                            )
                    )
                    .toInt()

            histogram[bucket]++
            count++
        }

        if (
            count ==
            0
        ) {
            return 1.0
        }

        val target =
            (
                (
                    count -
                        1
                    ) *
                    q
                )
                .toInt()
                .coerceIn(
                    0,
                    count -
                        1
                )

        var cumulative =
            0

        for (
            bucket in
            histogram.indices
        ) {
            cumulative +=
                histogram[bucket]

            if (
                cumulative >
                target
            ) {
                return bucket.toDouble() /
                    (
                        ENVELOPE_HISTOGRAM_BINS -
                            1
                        )
            }
        }

        return 1.0
    }

    private fun rmsDbfs(
        samples: FloatArray
    ): Double {
        if (
            samples.isEmpty()
        ) {
            return -120.0
        }

        var sum =
            0.0

        for (
            value in
            samples
        ) {
            val x =
                value.toDouble()

            sum +=
                x *
                    x
        }

        val rms =
            sqrt(
                sum /
                    samples.size
            )

        return if (
            rms >
            1e-12
        ) {
            20.0 *
                log10(
                    rms
                )
        } else {
            -120.0
        }
    }

    companion object {
        private const val NFFT =
            1024

        private const val HOP =
            256

        // LPC order 16 at 16 kHz is a conservative mobile speech-analysis
        // setting: enough envelope detail for major formants without trying
        // to encode every narrow spectral fluctuation as vocal-tract shape.
        private const val LPC_ORDER =
            16

        private const val LPC_PREEMPHASIS =
            0.94

        private const val NOISE_PERCENTILE =
            0.18

        private const val NOISE_FRAME_STRIDE =
            4

        private const val ENVELOPE_HISTOGRAM_BINS =
            64

        private const val WIENER_OVERSUBTRACT =
            1.10

        private const val SPECTRAL_FLOOR =
            0.10

        private const val HARMONIC_GAIN_FLOOR =
            0.72

        private const val FORMANT_GAIN_FLOOR =
            0.68

        private const val FRICATIVE_GAIN_FLOOR =
            0.38

        private const val HARMONIC_NOISE_RATIO =
            3.2

        private const val HARMONIC_LOW_HZ =
            70.0

        private const val HARMONIC_HIGH_HZ =
            5000.0

        private const val FORMANT_LOW_HZ =
            180.0

        private const val FORMANT_HIGH_HZ =
            5000.0

        private const val FRICATIVE_LOW_HZ =
            3500.0

        private const val FRICATIVE_HIGH_HZ =
            7800.0

        private const val FORMANT_ENVELOPE_PERCENTILE =
            0.72

        private const val MIN_POWER =
            1e-14

        private val HANN_WINDOW =
            DoubleArray(
                NFFT
            ) { i ->
                0.5 -
                    0.5 *
                    cos(
                        2.0 *
                            PI *
                            i /
                            (
                                NFFT -
                                    1
                                )
                    )
            }

        private val LPC_COS_BASIS =
            Array(
                NFFT /
                    2 +
                    1
            ) { bin ->
                DoubleArray(
                    LPC_ORDER +
                        1
                ) { k ->
                    cos(
                        2.0 *
                            PI *
                            bin *
                            k /
                            NFFT
                    )
                }
            }

        private val LPC_SIN_BASIS =
            Array(
                NFFT /
                    2 +
                    1
            ) { bin ->
                DoubleArray(
                    LPC_ORDER +
                        1
                ) { k ->
                    sin(
                        2.0 *
                            PI *
                            bin *
                            k /
                            NFFT
                    )
                }
            }
    }
}
