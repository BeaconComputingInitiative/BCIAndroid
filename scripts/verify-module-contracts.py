#!/usr/bin/env python3
"""
BEACON COMPUTING INITIATIVE
BCIAndroid cross-module compatibility contract verifier.
Developer Seal: CJB-DEV-2026
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def one(pattern: str, text: str, label: str, flags: int = re.S) -> str:
    match = re.search(pattern, text, flags)
    if not match:
        errors.append(f"missing contract value: {label}")
        return ""
    return match.group(1)


def kotlin_const_string(text: str, name: str) -> str:
    return one(
        rf"const\s+val\s+{re.escape(name)}\s*=\s*\"([^\"]+)\"",
        text,
        f"Kotlin const {name}",
    )


def kotlin_const_int(text: str, name: str) -> int:
    raw = one(
        rf"(?:private\s+)?const\s+val\s+{re.escape(name)}\s*=\s*([0-9_]+)",
        text,
        f"Kotlin int const {name}",
    )
    return int(raw.replace("_", "")) if raw else -1


def gradle_string(text: str, name: str) -> str:
    return one(
        rf"{re.escape(name)}\s*=\s*\"([^\"]+)\"",
        text,
        f"Gradle value {name}",
    )


def parse_kotlin_external(text: str) -> dict[str, tuple[str, list[str]]]:
    pattern = re.compile(
        r"(?:private\s+)?external\s+fun\s+"
        r"(?P<name>\w+)\s*\((?P<params>.*?)\)"
        r"\s*(?::\s*(?P<ret>[A-Za-z0-9_<>?.]+))?",
        re.S,
    )
    methods: dict[str, tuple[str, list[str]]] = {}
    for match in pattern.finditer(text):
        params: list[str] = []
        for param in match.group("params").split(","):
            if not param.strip():
                continue
            ptype = re.search(r":\s*([A-Za-z0-9_<>?.]+)", param)
            if not ptype:
                errors.append(
                    f"unable to parse Kotlin parameter for {match.group('name')}: {param!r}"
                )
                continue
            params.append(ptype.group(1))
        methods[match.group("name")] = (
            match.group("ret") or "Unit",
            params,
        )
    return methods


JNI_TYPE = {
    "Unit": "void",
    "Boolean": "jboolean",
    "Int": "jint",
    "Long": "jlong",
    "String": "jstring",
    "ShortArray": "jshortArray",
    "FloatArray": "jfloatArray",
    "DoubleArray": "jdoubleArray",
}


def parse_cpp_jni(
    text: str,
    java_prefix: str,
) -> dict[str, tuple[str, list[str]]]:
    pattern = re.compile(
        rf"JNIEXPORT\s+(?P<ret>\w+)\s+JNICALL\s+"
        rf"Java_{re.escape(java_prefix)}_(?P<name>\w+)\s*"
        rf"\((?P<params>.*?)\)\s*\{{",
        re.S,
    )
    methods: dict[str, tuple[str, list[str]]] = {}
    for match in pattern.finditer(text):
        jtypes = re.findall(
            r"\b(j(?:object|class|string|boolean|byte|char|short|int|long|float|double|"
            r"objectArray|booleanArray|byteArray|charArray|shortArray|intArray|longArray|"
            r"floatArray|doubleArray))\b",
            match.group("params"),
        )
        if jtypes and jtypes[0] in {"jobject", "jclass"}:
            jtypes = jtypes[1:]
        methods[match.group("name")] = (
            match.group("ret"),
            jtypes,
        )
    return methods


def verify_jni_surface(
    kotlin_path: str,
    cpp_path: str,
    java_prefix: str,
) -> None:
    kotlin = parse_kotlin_external(read(kotlin_path))
    native = parse_cpp_jni(read(cpp_path), java_prefix)

    require(
        set(kotlin) == set(native),
        f"JNI method set mismatch for {kotlin_path}: "
        f"Kotlin={sorted(kotlin)} native={sorted(native)}",
    )

    for name, (kret, kparams) in kotlin.items():
        if name not in native:
            continue
        nret, nparams = native[name]
        expected_ret = JNI_TYPE.get(kret)
        expected_params = [JNI_TYPE.get(x, f"<unsupported:{x}>") for x in kparams]
        require(
            expected_ret == nret,
            f"JNI return mismatch {name}: Kotlin {kret} -> {expected_ret}, native {nret}",
        )
        require(
            expected_params == nparams,
            f"JNI parameter mismatch {name}: Kotlin {kparams} -> {expected_params}, "
            f"native {nparams}",
        )


app_gradle = read("app/build.gradle.kts")
root_gradle = read("build.gradle.kts")
android_workflow = read(".github/workflows/build-android.yml")
gateway_workflow = read(".github/workflows/build-gateway.yml")
cmake = read("app/src/main/cpp/whisper/CMakeLists.txt")
manifest = read("app/src/main/AndroidManifest.xml")
native_audio_kt = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/audio/NativeAudioEngine.kt"
)
native_audio_cpp = read("app/src/main/cpp/whisper/audio_engine.cpp")
native_tx_kt = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/transcription/NativeTranscriptionEngine.kt"
)
native_tx_cpp = read("app/src/main/cpp/whisper/native_engine.cpp")
audio_service = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/audio/AudioMonitorService.kt"
)
main_activity = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/MainActivity.kt"
)
broadcast_config = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/broadcast/BroadcastConfig.kt"
)
broadcast_publisher = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/broadcast/BroadcastPublisher.kt"
)
live_tx = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/transcription/LiveTranscriptionEngine.kt"
)
transcription_engine = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/transcription/TranscriptionEngine.kt"
)
spectrum = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/ui/Spectrum3DView.kt"
)
gateway = read("gateway/src/server.ts")
gateway_package = read("gateway/package.json")
gateway_docker = read("gateway/Dockerfile")
robust_handler = read(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/runtime/RobustErrorHandler.kt"
)
core_contract = json.loads(
    read(
        "contracts/bci-core-v1.json"
    )
)
contract_audio = core_contract["audio"]["capture"]
contract_whisper = core_contract["audio"]["whisper"]
contract_baseline = core_contract["baseline"]
contract_broadcast = core_contract["broadcast"]
contract_error = core_contract["robustErrorHandling"]

# Kotlin <-> JNI/C++
verify_jni_surface(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/audio/NativeAudioEngine.kt",
    "app/src/main/cpp/whisper/audio_engine.cpp",
    "org_beaconcomputinginitiative_bciwhisper_audio_NativeAudioEngine",
)
verify_jni_surface(
    "app/src/main/java/org/beaconcomputinginitiative/bciwhisper/transcription/NativeTranscriptionEngine.kt",
    "app/src/main/cpp/whisper/native_engine.cpp",
    "org_beaconcomputinginitiative_bciwhisper_transcription_NativeTranscriptionEngine",
)

cmake_target = one(
    r"add_library\s*\(\s*(\w+)",
    cmake,
    "CMake native target",
)
for label, source in (
    ("NativeAudioEngine", native_audio_kt),
    ("NativeTranscriptionEngine", native_tx_kt),
):
    load_name = one(
        r"System\.loadLibrary\s*\(\s*\"([^\"]+)\"\s*\)",
        source,
        f"{label} loadLibrary",
    )
    require(
        load_name == cmake_target,
        f"{label} loads {load_name!r}, but CMake builds {cmake_target!r}",
    )

require("set(CMAKE_CXX_STANDARD 17)" in cmake, "native C++ standard must remain C++17")
require("find_package(oboe REQUIRED CONFIG)" in cmake, "CMake must resolve Oboe through Prefab")
require("oboe::oboe" in cmake, "native target must link oboe::oboe")
require("prefab = true" in app_gradle, "Android Gradle build must enable Prefab")
require(
    'implementation("com.google.oboe:oboe:1.11.0")' in app_gradle,
    "Gradle Oboe dependency must remain pinned to 1.11.0",
)
require(
    '"-DANDROID_STL=c++_shared"' in app_gradle,
    "Android native build must use c++_shared consistently",
)

# Gradle / SDK / NDK / CMake
agp = one(
    r'id\("com\.android\.application"\)\s+version\s+"([^"]+)"',
    root_gradle,
    "Android Gradle Plugin version",
)
ndk = gradle_string(app_gradle, "ndkVersion")
cmake_version = one(
    r'cmake\s*\{.*?version\s*=\s*"([^"]+)"',
    app_gradle,
    "CMake version",
)
require(bool(agp), "Android Gradle Plugin version must be explicit")
require(
    f"ndk;{ndk}" in android_workflow,
    f"CI must install the same NDK declared by Gradle ({ndk})",
)
require(
    f"cmake;{cmake_version}" in android_workflow,
    f"CI must install the same CMake declared by Gradle ({cmake_version})",
)
require(
    'abiFilters += listOf("arm64-v8a")' in app_gradle,
    "current release contract is arm64-v8a; update packaging checks if ABI support changes",
)

# Audio data contract
service_rate = kotlin_const_int(audio_service, "SAMPLE_RATE")
broadcast_rate = kotlin_const_int(broadcast_config, "SAMPLE_RATE")
broadcast_channels = kotlin_const_int(broadcast_config, "CHANNELS")
broadcast_bitrate = kotlin_const_int(broadcast_config, "BITRATE_KBPS")
broadcast_codec = kotlin_const_string(broadcast_config, "INPUT_CODEC")

require(
    service_rate == contract_audio["sampleRateHz"],
    f"Audio service sample rate {service_rate} does not match core contract {contract_audio['sampleRateHz']}",
)
require(
    service_rate == broadcast_rate,
    f"capture/publisher sample-rate mismatch: {service_rate} vs {broadcast_rate}",
)
require(
    broadcast_channels == contract_audio["channels"],
    f"publisher channels {broadcast_channels} do not match core contract {contract_audio['channels']}",
)
require(
    broadcast_bitrate == contract_broadcast["publicBitrateKbps"],
    f"broadcast bitrate {broadcast_bitrate} does not match core contract {contract_broadcast['publicBitrateKbps']}",
)
require(
    broadcast_codec == contract_audio["sampleFormat"],
    f"publisher codec {broadcast_codec} does not match core contract {contract_audio['sampleFormat']}",
)
require("oboe::AudioFormat::I16" in native_audio_cpp, "Oboe capture format must remain PCM16")
require("oboe::ChannelCount::Mono" in native_audio_cpp, "Oboe capture must remain mono")
require("setSampleRate(sampleRate)" in native_audio_cpp, "Oboe must use the requested Android sample rate")
require(
    "transcribeLivePcm" in live_tx,
    "rolling worker must delegate live PCM transcription through TranscriptionEngine",
)
require(
    "resamplePcm16To16k" in transcription_engine,
    "TranscriptionEngine must own the explicit 44.1 kHz -> 16 kHz native resampling boundary",
)

# OpenGL ES contract
require(
    bool(re.search(r"setEGLContextClientVersion\s*\(\s*2\s*\)", spectrum)),
    "Spectrum3DView must request an OpenGL ES 2 context",
)
require("GLES20" in spectrum, "Spectrum3DView must use GLES20 with its ES2 context")
require(
    'android:glEsVersion="0x00020000"' in manifest,
    "manifest OpenGL ES requirement must match GLES20 renderer",
)

# Android publisher <-> gateway protocol
protocol = kotlin_const_string(broadcast_config, "PROTOCOL")
publisher_id = kotlin_const_string(broadcast_config, "PUBLISHER_ID")
stream_id = kotlin_const_string(broadcast_config, "STREAM_ID")
output_codec = kotlin_const_string(broadcast_config, "OUTPUT_CODEC")

gateway_protocol = one(
    r'const\s+PROTOCOL\s*=\s*"([^"]+)"',
    gateway,
    "gateway protocol",
)
gateway_stream = one(
    r'Bun\.env\.BCI_STREAM_ID\s*\|\|\s*"([^"]+)"',
    gateway,
    "gateway stream id",
)
gateway_publisher = one(
    r'Bun\.env\.BCI_EXPECTED_PUBLISHER_ID\s*\|\|\s*"([^"]+)"',
    gateway,
    "gateway publisher id",
)

require(
    protocol == contract_broadcast["protocol"],
    f"Android protocol {protocol} does not match core contract {contract_broadcast['protocol']}",
)
require(
    stream_id == contract_broadcast["streamId"],
    f"Android stream id {stream_id} does not match core contract {contract_broadcast['streamId']}",
)
require(
    publisher_id == contract_broadcast["publisherId"],
    f"Android publisher id {publisher_id} does not match core contract {contract_broadcast['publisherId']}",
)
require(protocol == gateway_protocol, f"protocol mismatch: Android={protocol}, gateway={gateway_protocol}")
require(stream_id == gateway_stream, f"stream id mismatch: Android={stream_id}, gateway={gateway_stream}")
require(
    publisher_id == gateway_publisher,
    f"publisher id mismatch: Android={publisher_id}, gateway={gateway_publisher}",
)
require(
    output_codec == contract_broadcast["publicCodec"],
    f"public output codec {output_codec} does not match core contract {contract_broadcast['publicCodec']}",
)
require('"pcm_s16le"' in gateway, "gateway must accept Android pcm_s16le input")
require('"mp3"' in gateway, "gateway must expose MP3 output")
require(
    f"/api/v1/publish/{stream_id}" in kotlin_const_string(broadcast_config, "PUBLISH_URL"),
    "Android publish URL must contain the configured stream id",
)
require(
    f"/live/{stream_id}" in kotlin_const_string(broadcast_config, "STREAM_URL"),
    "Android public stream URL must contain the configured stream id",
)

hello_fields = (
    "type",
    "protocol",
    "codec",
    "sampleRate",
    "channels",
    "bitrateKbps",
)
for field in hello_fields:
    require(f'"{field}"' in broadcast_publisher, f"publisher hello is missing field {field}")
    require(
        f"body.{field}" in gateway or f'"{field}"' in gateway,
        f"gateway hello parser/ack is missing field {field}",
    )

transcript_fields = (
    "sequence",
    "startMs",
    "endMs",
    "speaker",
    "text",
    "decodeMs",
    "final",
)
for field in transcript_fields:
    require(f'"{field}"' in broadcast_publisher, f"publisher transcript is missing field {field}")
    require(f"body.{field}" in gateway, f"gateway transcript parser is missing field {field}")

require(
    "encodeTranscriptEvent(" in gateway and '"transcript"' in gateway,
    "gateway must fan transcript messages out as SSE transcript events",
)
require(
    "new EventSource('/api/v1/transcript/" in gateway and "streamId}')" in gateway,
    "public player SSE path must use the gateway stream id",
)

require(
    "preloadTranscriptionEngine()" not in one(
        r"override\s+fun\s+onCreate\s*\(.*?\)\s*\{(.*?)\n\s*\}",
        main_activity,
        "MainActivity onCreate body",
    ),
    "offline Whisper warmup must not start from Activity onCreate",
)
require(
    re.search(
        r"private\s+fun\s+buildTranscriptsScreen\s*\(\).*?preloadTranscriptionEngine\s*\(",
        main_activity,
        re.S,
    )
    is not None,
    "offline Whisper warmup must be lazy from the transcripts workflow",
)

# Robust error handling contract
require(
    core_contract["contract"]["id"] == "bci-core-v1",
    "machine-readable contract id must remain bci-core-v1",
)
require(
    "class RobustErrorHandler" in robust_handler,
    "RobustErrorHandler implementation is missing",
)
require(
    "private fun robustErrorLoop()" in audio_service,
    "AudioMonitorService robust error loop is missing",
)
require(
    "private fun collectHealthObservations()" in audio_service,
    "AudioMonitorService health observation collector is missing",
)
require(
    "BCI_E_CAPTURE_LOOP" in audio_service
    and "catch (t: Throwable)" in audio_service,
    "capture loop must retain an outer exception boundary and stable capture-loop error code",
)
require(
    f'private const val ROBUST_ERROR_CHECK_MS =\n            {contract_error["checkIntervalMs"]}L'
    in audio_service,
    "robust error loop interval does not match core contract",
)
require(
    f'private const val FIRST_PCM_FRAME_TIMEOUT_MS =\n            {contract_baseline["failureRules"]["firstPcmFrameTimeoutMs"]:,}L'.replace(",", "_")
    in audio_service,
    "first PCM frame timeout does not match core contract",
)
require(
    f'private const val CAPTURE_STALL_MS =\n            {contract_baseline["failureRules"]["pcmNoProgressMs"]:,}L'.replace(",", "_")
    in audio_service,
    "capture stall threshold does not match core contract",
)
require(
    f'private const val BASELINE_STALL_MS =\n            {contract_baseline["failureRules"]["baselineNoProgressWhilePcmArrivesMs"]:,}L'.replace(",", "_")
    in audio_service,
    "baseline stall threshold does not match core contract",
)

for code in contract_error["errorCodes"]:
    require(
        code in audio_service or code in robust_handler,
        f"contract error code {code} is not represented in runtime source",
    )

# Gateway runtime parity
docker_bun = one(
    r"FROM\s+oven/bun:([^\s-]+)",
    gateway_docker,
    "Docker Bun version",
)
workflow_bun = one(
    r'bun-version:\s*"([^"]+)"',
    gateway_workflow,
    "Gateway CI Bun version",
)
package_version = one(
    r'"version"\s*:\s*"([^"]+)"',
    gateway_package,
    "gateway package version",
)
server_version = one(
    r'const\s+VERSION\s*=\s*"([^"]+)"',
    gateway,
    "gateway server version",
)
require(
    docker_bun == workflow_bun,
    f"Bun runtime mismatch: Docker={docker_bun}, CI={workflow_bun}",
)
require(
    package_version == server_version,
    f"gateway version mismatch: package.json={package_version}, server={server_version}",
)

if errors:
    print("BCI MODULE CONTRACT AUDIT: FAIL", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("BEACON COMPUTING INITIATIVE")
print("Developer Seal: CJB-DEV-2026")
print("BCI MODULE CONTRACT AUDIT: PASS")
print(f"Android Gradle Plugin: {agp}")
print(f"NDK: {ndk}")
print(f"CMake: {cmake_version}")
print("ABI: arm64-v8a")
print("Native target:", cmake_target)
print("Oboe: 1.11.0")
print("Kotlin/JNI audio methods:", len(parse_kotlin_external(native_audio_kt)))
print("Kotlin/JNI transcription methods:", len(parse_kotlin_external(native_tx_kt)))
print(f"Audio contract: {service_rate} Hz PCM16 mono -> {broadcast_codec}")
print(f"Whisper input boundary: {service_rate} Hz PCM16 -> 16000 Hz float")
print(f"Broadcast contract: {protocol} / {stream_id} / {output_codec}")
print(f"Gateway runtime: Bun {docker_bun} / gateway {server_version}")
print("OpenGL contract: ES 2.0 manifest + GLSurfaceView/GLES20")
print("Core contract:", core_contract["contract"]["id"])
print(
    "Robust error handling:",
    f'{contract_error["checkIntervalMs"]} ms checks / '
    f'{contract_baseline["failureRules"]["pcmNoProgressMs"]} ms capture stall / '
    f'{contract_baseline["failureRules"]["baselineNoProgressWhilePcmArrivesMs"]} ms baseline stall',
)
