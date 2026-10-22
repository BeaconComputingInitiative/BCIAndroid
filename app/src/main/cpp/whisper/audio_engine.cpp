/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Oboe Input Engine
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by Android CI for each committed revision.
 */

#include <jni.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <thread>
#include <vector>

namespace {

class OboeInputEngine final :
        public oboe::AudioStreamDataCallback,
        public oboe::AudioStreamErrorCallback {
public:
    static constexpr int32_t kRingCapacityFrames = 262144;

    bool start(
            int32_t sampleRate,
            int32_t deviceId,
            bool communicationPreset) {
        stop();

        mReadIndex.store(0, std::memory_order_release);
        mWriteIndex.store(0, std::memory_order_release);
        mDroppedFrames.store(0, std::memory_order_release);
        mRunning.store(false, std::memory_order_release);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
            ->setFormat(oboe::AudioFormat::I16)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setInputPreset(
                communicationPreset
                    ? oboe::InputPreset::VoiceCommunication
                    : oboe::InputPreset::Unprocessed)
            ->setChannelConversionAllowed(true)
            ->setFormatConversionAllowed(true)
            ->setSampleRateConversionQuality(
                oboe::SampleRateConversionQuality::Medium)
            ->setSessionId(oboe::SessionId::Allocate)
            ->setDataCallback(this)
            ->setErrorCallback(this);

        if (deviceId > 0) {
            builder.setDeviceId(deviceId);
        }

        oboe::Result result =
            builder.openStream(mStream);

        if (result != oboe::Result::OK) {
            builder.setSharingMode(
                oboe::SharingMode::Shared);

            result =
                builder.openStream(mStream);
        }

        if (
            result != oboe::Result::OK ||
            !mStream
        ) {
            mStream.reset();
            return false;
        }

        mRunning.store(
            true,
            std::memory_order_release);

        result =
            mStream->requestStart();

        if (result != oboe::Result::OK) {
            stop();
            return false;
        }

        return true;
    }

    void stop() {
        mRunning.store(
            false,
            std::memory_order_release);

        auto stream =
            mStream;

        mStream.reset();

        if (stream) {
            stream->requestStop();
            stream->close();
        }
    }

    int32_t read(
            int16_t *destination,
            int32_t requestedFrames,
            int32_t timeoutMs) {
        if (
            destination == nullptr ||
            requestedFrames <= 0
        ) {
            return 0;
        }

        const auto deadline =
            std::chrono::steady_clock::now() +
            std::chrono::milliseconds(
                std::max(0, timeoutMs));

        int32_t copied = 0;

        while (copied < requestedFrames) {
            auto readIndex =
                mReadIndex.load(
                    std::memory_order_acquire);

            const auto writeIndex =
                mWriteIndex.load(
                    std::memory_order_acquire);

            if (readIndex == writeIndex) {
                if (
                    !mRunning.load(
                        std::memory_order_acquire)
                ) {
                    break;
                }

                if (
                    timeoutMs <= 0 ||
                    std::chrono::steady_clock::now() >=
                        deadline
                ) {
                    break;
                }

                std::this_thread::sleep_for(
                    std::chrono::milliseconds(1));

                continue;
            }

            destination[copied++] =
                mRing[
                    static_cast<size_t>(
                        readIndex)
                ];

            readIndex =
                (
                    readIndex +
                    1
                ) %
                kRingCapacityFrames;

            mReadIndex.store(
                readIndex,
                std::memory_order_release);
        }

        return copied;
    }

    int32_t actualSampleRate() const {
        return mStream
            ? mStream->getSampleRate()
            : 0;
    }

    int32_t framesPerBurst() const {
        return mStream
            ? mStream->getFramesPerBurst()
            : 0;
    }

    int32_t xRunCount() const {
        if (!mStream) {
            return 0;
        }

        const auto value =
            mStream->getXRunCount();

        return value
            ? value.value()
            : 0;
    }

    int32_t sessionId() const {
        return mStream
            ? static_cast<int32_t>(
                mStream->getSessionId())
            : 0;
    }

    int32_t audioApi() const {
        return mStream
            ? static_cast<int32_t>(
                mStream->getAudioApi())
            : 0;
    }

    int64_t droppedFrames() const {
        return mDroppedFrames.load(
            std::memory_order_acquire);
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *,
            void *audioData,
            int32_t numFrames) override {
        if (
            !mRunning.load(
                std::memory_order_acquire)
        ) {
            return oboe::DataCallbackResult::Stop;
        }

        const auto *input =
            static_cast<const int16_t *>(
                audioData);

        if (input == nullptr) {
            return oboe::DataCallbackResult::Continue;
        }

        auto writeIndex =
            mWriteIndex.load(
                std::memory_order_relaxed);

        auto readIndex =
            mReadIndex.load(
                std::memory_order_acquire);

        for (
            int32_t frame = 0;
            frame < numFrames;
            ++frame
        ) {
            const auto nextWrite =
                (
                    writeIndex +
                    1
                ) %
                kRingCapacityFrames;

            if (nextWrite == readIndex) {
                readIndex =
                    (
                        readIndex +
                        1
                    ) %
                    kRingCapacityFrames;

                mReadIndex.store(
                    readIndex,
                    std::memory_order_release);

                mDroppedFrames.fetch_add(
                    1,
                    std::memory_order_relaxed);
            }

            mRing[
                static_cast<size_t>(
                    writeIndex)
            ] =
                input[frame];

            writeIndex =
                nextWrite;
        }

        mWriteIndex.store(
            writeIndex,
            std::memory_order_release);

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(
            oboe::AudioStream *,
            oboe::Result) override {
        mRunning.store(
            false,
            std::memory_order_release);
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::vector<int16_t> mRing =
        std::vector<int16_t>(
            kRingCapacityFrames);

    std::atomic<int32_t> mReadIndex{0};
    std::atomic<int32_t> mWriteIndex{0};
    std::atomic<int64_t> mDroppedFrames{0};
    std::atomic<bool> mRunning{false};
};

OboeInputEngine g_engine;

}  // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_startCapture(
        JNIEnv *,
        jobject,
        jint sampleRate,
        jint deviceId,
        jboolean communicationPreset) {
    return g_engine.start(
        sampleRate,
        deviceId,
        communicationPreset == JNI_TRUE)
        ? JNI_TRUE
        : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_stopCapture(
        JNIEnv *,
        jobject) {
    g_engine.stop();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_readPcm16(
        JNIEnv *env,
        jobject,
        jshortArray destination,
        jint requestedFrames,
        jint timeoutMs) {
    if (destination == nullptr) {
        return 0;
    }

    const jsize capacity =
        env->GetArrayLength(
            destination);

    const int32_t wanted =
        std::min(
            static_cast<int32_t>(
                capacity),
            std::max(
                0,
                static_cast<int32_t>(
                    requestedFrames)));

    if (wanted <= 0) {
        return 0;
    }

    std::vector<int16_t> buffer(
        static_cast<size_t>(
            wanted));

    const int32_t count =
        g_engine.read(
            buffer.data(),
            wanted,
            timeoutMs);

    if (count > 0) {
        env->SetShortArrayRegion(
            destination,
            0,
            count,
            reinterpret_cast<const jshort *>(
                buffer.data()));
    }

    return count;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_actualSampleRate(
        JNIEnv *,
        jobject) {
    return g_engine.actualSampleRate();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_framesPerBurst(
        JNIEnv *,
        jobject) {
    return g_engine.framesPerBurst();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_xRunCount(
        JNIEnv *,
        jobject) {
    return g_engine.xRunCount();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_sessionId(
        JNIEnv *,
        jobject) {
    return g_engine.sessionId();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_audioApi(
        JNIEnv *,
        jobject) {
    return g_engine.audioApi();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine_droppedFrames(
        JNIEnv *,
        jobject) {
    return static_cast<jlong>(
        g_engine.droppedFrames());
}
