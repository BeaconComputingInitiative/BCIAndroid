/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Native Transcription Engine
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "BCINativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kTargetSampleRate = 16000;
constexpr double kFrameSeconds = 0.020;
constexpr double kNoisePercentile = 0.12;
constexpr double kSpeechPercentile = 0.78;
constexpr double kActiveMarginDb = 6.0;
constexpr double kMinActiveDbfs = -55.0;
constexpr double kMaxCleanNoiseFloorDbfs = -42.0;
constexpr double kMinCleanContrastDb = 10.0;
constexpr double kMinCleanSpeechLevelDbfs = -38.0;

std::mutex g_mutex;
whisper_context *g_context = nullptr;
std::string g_model_path;

double rms_dbfs(
        const float *samples,
        int offset,
        int count) {
    if (samples == nullptr || count <= 0) {
        return -120.0;
    }

    double sum_sq = 0.0;

    for (int i = 0; i < count; ++i) {
        const double x =
                static_cast<double>(
                        samples[offset + i]);

        sum_sq +=
                x * x;
    }

    const double rms =
            std::sqrt(
                    sum_sq /
                    static_cast<double>(count));

    return rms > 1e-12
            ? 20.0 *
              std::log10(rms)
            : -120.0;
}

double percentile_sorted(
        const std::vector<double> &values,
        double q) {
    if (values.empty()) {
        return -120.0;
    }

    const auto index =
            static_cast<size_t>(
                    std::clamp(
                            static_cast<long long>(
                                    (values.size() - 1) * q),
                            0LL,
                            static_cast<long long>(
                                    values.size() - 1)));

    return values[index];
}

bool ensure_context_locked(
        const char *model_path) {
    if (model_path == nullptr ||
        model_path[0] == '\0') {
        return false;
    }

    const std::string requested(
            model_path);

    if (g_context != nullptr &&
        g_model_path == requested) {
        return true;
    }

    if (g_context != nullptr) {
        whisper_free(
                g_context);

        g_context = nullptr;
        g_model_path.clear();
    }

    const auto params =
            whisper_context_default_params();

    g_context =
            whisper_init_from_file_with_params(
                    requested.c_str(),
                    params);

    if (g_context == nullptr) {
        LOGE(
                "Failed to initialize whisper model");
        return false;
    }

    g_model_path =
            requested;

    LOGI(
            "Whisper context initialized once and retained");

    return true;
}

}  // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_warmModel(
        JNIEnv *env,
        jobject,
        jstring model_path) {
    if (model_path == nullptr) {
        return JNI_FALSE;
    }

    const char *path =
            env->GetStringUTFChars(
                    model_path,
                    nullptr);

    if (path == nullptr) {
        return JNI_FALSE;
    }

    bool ok = false;

    {
        std::lock_guard<std::mutex>
                guard(
                        g_mutex);

        ok =
                ensure_context_locked(
                        path);
    }

    env->ReleaseStringUTFChars(
            model_path,
            path);

    return ok
            ? JNI_TRUE
            : JNI_FALSE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_resamplePcm16To16k(
        JNIEnv *env,
        jobject,
        jshortArray input,
        jint source_rate) {
    if (input == nullptr ||
        source_rate <= 0) {
        return env->NewFloatArray(
                0);
    }

    const jsize input_size =
            env->GetArrayLength(
                    input);

    if (input_size <= 0) {
        return env->NewFloatArray(
                0);
    }

    jshort *source =
            env->GetShortArrayElements(
                    input,
                    nullptr);

    if (source == nullptr) {
        return env->NewFloatArray(
                0);
    }

    int output_size = 0;

    if (source_rate ==
        kTargetSampleRate) {
        output_size =
                input_size;
    } else {
        output_size =
                std::max(
                        1,
                        static_cast<int>(
                                std::floor(
                                        static_cast<double>(
                                                input_size) *
                                        kTargetSampleRate /
                                        source_rate)));
    }

    std::vector<float>
            output(
                    static_cast<size_t>(
                            output_size));

    if (source_rate ==
        kTargetSampleRate) {
        for (int i = 0;
             i < output_size;
             ++i) {
            output[i] =
                    static_cast<float>(
                            source[i]) /
                    32768.0f;
        }
    } else {
        const double ratio =
                static_cast<double>(
                        source_rate) /
                kTargetSampleRate;

        for (int i = 0;
             i < output_size;
             ++i) {
            const double position =
                    i * ratio;

            const int left =
                    std::clamp(
                            static_cast<int>(
                                    position),
                            0,
                            static_cast<int>(
                                    input_size) - 1);

            const int right =
                    std::min(
                            left + 1,
                            static_cast<int>(
                                    input_size) - 1);

            const double fraction =
                    position -
                    left;

            const double sample =
                    static_cast<double>(
                            source[left]) *
                    (1.0 - fraction) +
                    static_cast<double>(
                            source[right]) *
                    fraction;

            output[i] =
                    static_cast<float>(
                            sample /
                            32768.0);
        }
    }

    env->ReleaseShortArrayElements(
            input,
            source,
            JNI_ABORT);

    jfloatArray result =
            env->NewFloatArray(
                    output_size);

    if (result != nullptr) {
        env->SetFloatArrayRegion(
                result,
                0,
                output_size,
                output.data());
    }

    return result;
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_assessQuality(
        JNIEnv *env,
        jobject,
        jfloatArray audio,
        jint sample_rate) {
    constexpr int kFieldCount = 5;

    double result[kFieldCount] = {
            -120.0,
            -120.0,
            0.0,
            0.0,
            0.0
    };

    if (audio == nullptr ||
        sample_rate <= 0) {
        jdoubleArray out =
                env->NewDoubleArray(
                        kFieldCount);

        env->SetDoubleArrayRegion(
                out,
                0,
                kFieldCount,
                result);

        return out;
    }

    const jsize sample_count =
            env->GetArrayLength(
                    audio);

    jfloat *samples =
            env->GetFloatArrayElements(
                    audio,
                    nullptr);

    if (samples == nullptr ||
        sample_count <= 0) {
        if (samples != nullptr) {
            env->ReleaseFloatArrayElements(
                    audio,
                    samples,
                    JNI_ABORT);
        }

        jdoubleArray out =
                env->NewDoubleArray(
                        kFieldCount);

        env->SetDoubleArrayRegion(
                out,
                0,
                kFieldCount,
                result);

        return out;
    }

    const int frame_size =
            std::max(
                    128,
                    static_cast<int>(
                            sample_rate *
                            kFrameSeconds));

    const int frame_count =
            (
                    sample_count +
                    frame_size -
                    1) /
            frame_size;

    std::vector<double>
            levels;

    levels.reserve(
            static_cast<size_t>(
                    frame_count));

    int offset = 0;

    while (offset <
           sample_count) {
        const int count =
                std::min(
                        frame_size,
                        static_cast<int>(
                                sample_count) -
                        offset);

        levels.push_back(
                rms_dbfs(
                        samples,
                        offset,
                        count));

        offset +=
                count;
    }

    std::sort(
            levels.begin(),
            levels.end());

    const double noise =
            percentile_sorted(
                    levels,
                    kNoisePercentile);

    const double speech =
            percentile_sorted(
                    levels,
                    kSpeechPercentile);

    const double contrast =
            std::max(
                    0.0,
                    speech -
                    noise);

    const double active_threshold =
            std::max(
                    noise +
                    kActiveMarginDb,
                    kMinActiveDbfs);

    int active = 0;

    for (const double level :
         levels) {
        if (level >=
            active_threshold) {
            ++active;
        }
    }

    const double active_ratio =
            levels.empty()
                    ? 0.0
                    : static_cast<double>(
                              active) /
                      levels.size();

    const bool reconstruct =
            noise >
                    kMaxCleanNoiseFloorDbfs ||
            contrast <
                    kMinCleanContrastDb ||
            speech <
                    kMinCleanSpeechLevelDbfs;

    result[0] = noise;
    result[1] = speech;
    result[2] = contrast;
    result[3] = active_ratio;
    result[4] = reconstruct
            ? 1.0
            : 0.0;

    env->ReleaseFloatArrayElements(
            audio,
            samples,
            JNI_ABORT);

    jdoubleArray out =
            env->NewDoubleArray(
                    kFieldCount);

    env->SetDoubleArrayRegion(
            out,
            0,
            kFieldCount,
            result);

    return out;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_transcribe(
        JNIEnv *env,
        jobject,
        jfloatArray audio,
        jint threads) {
    if (audio == nullptr) {
        return -2;
    }

    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    if (g_context == nullptr) {
        return -1;
    }

    jfloat *samples =
            env->GetFloatArrayElements(
                    audio,
                    nullptr);

    if (samples == nullptr) {
        return -3;
    }

    const jsize count =
            env->GetArrayLength(
                    audio);

    whisper_full_params params =
            whisper_full_default_params(
                    WHISPER_SAMPLING_GREEDY);

    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads =
            std::max(
                    1,
                    static_cast<int>(
                            threads));
    params.no_context = true;
    params.single_segment = false;

    const int rc =
            whisper_full(
                    g_context,
                    params,
                    samples,
                    count);

    env->ReleaseFloatArrayElements(
            audio,
            samples,
            JNI_ABORT);

    return rc;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_segmentCount(
        JNIEnv *,
        jobject) {
    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    return g_context != nullptr
            ? whisper_full_n_segments(
                      g_context)
            : 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_segmentText(
        JNIEnv *env,
        jobject,
        jint index) {
    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    const char *text =
            g_context != nullptr
                    ? whisper_full_get_segment_text(
                              g_context,
                              index)
                    : "";

    return env->NewStringUTF(
            text);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_segmentStart10ms(
        JNIEnv *,
        jobject,
        jint index) {
    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    return g_context != nullptr
            ? static_cast<jlong>(
                      whisper_full_get_segment_t0(
                              g_context,
                              index))
            : 0;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_segmentEnd10ms(
        JNIEnv *,
        jobject,
        jint index) {
    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    return g_context != nullptr
            ? static_cast<jlong>(
                      whisper_full_get_segment_t1(
                              g_context,
                              index))
            : 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine_shutdown(
        JNIEnv *,
        jobject) {
    std::lock_guard<std::mutex>
            guard(
                    g_mutex);

    if (g_context != nullptr) {
        whisper_free(
                g_context);

        g_context = nullptr;
        g_model_path.clear();
    }
}
