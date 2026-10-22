# BCI Core Contract v1

**Status:** Authoritative  
**Developer Seal:** CJB-DEV-2026  
**Implementation domains:** Kotlin · C++/Oboe · TypeScript

## Purpose

BCI Core Contract v1 is the single semantic agreement between the three primary BCIAndroid implementation domains. It exists to prevent a module from being locally correct while being globally incompatible.

The contract is intentionally verbose. A developer investigating a failure should be able to answer the following without guessing:

- Which module owns the operation?
- What exact type and unit crosses the boundary?
- Which thread is allowed to perform the work?
- What lifecycle state is required?
- What constitutes success?
- What constitutes a warning, recoverable error, or fatal error?
- Which subsystem is allowed to recover?
- Which subsystem must stop?
- What information must remain available after failure?
- Which changes require a contract-version change?

The machine-readable source of truth is `contracts/bci-core-v1.json`. This document explains the same rules for humans.

## 1. Runtime language boundaries

### Kotlin — Android orchestration

Kotlin owns Android-specific semantics: Activity/service lifecycle, permissions, UI state, foreground microphone service, recording orchestration, JNI declarations, broadcast-client control, rolling-transcription scheduling, and robust error-handling coordination.

Kotlin must **not** execute the real-time microphone callback or implement native Whisper inference. It may drain PCM produced by native audio and may request native inference through JNI.

### C++17 + Oboe — real-time/native boundary

C++ owns Oboe microphone capture, the native PCM ring, native audio telemetry, resampling into Whisper's 16 kHz input domain, whisper.cpp/GGML inference, and native quality assessment.

The Oboe callback is a hard real-time boundary. It may copy PCM into preallocated memory and update atomic/lock-free telemetry. It must not call the JVM, run Whisper, perform network/filesystem I/O, perform unbounded allocation, or wait on other threads.

### TypeScript + Bun — gateway/public player

TypeScript owns authenticated WebSocket ingestion, PCM→MP3 gateway processing, listener fanout, caption history, SSE caption delivery, and the public browser player.

The gateway does not own microphone capture or on-device inference.

## 2. Audio contract

The canonical capture format is:

| Property | Contract |
|---|---|
| Sample rate | **44,100 Hz** |
| Channels | **1 (mono)** |
| Sample format | **signed PCM16 little-endian** |
| Native callback owner | **C++/Oboe** |
| Kotlin drain block | **2,048 frames** |
| Kotlin native-read timeout | **250 ms** |
| Local history ring | **15 seconds** |

One frame means one signed 16-bit mono sample.

The Android detector, event recorder, live publisher, and rolling live-transcription input all consume the same 44.1 kHz PCM contract unless a boundary explicitly transforms it.

### Whisper boundary

whisper.cpp consumes 16 kHz float audio. Therefore every live/offline PCM16 path entering Whisper must cross the explicit native resampling boundary:

```text
44.1 kHz PCM16 mono
        ↓
JNI
        ↓
Native resamplePcm16To16k
        ↓
16 kHz float32
        ↓
whisper.cpp
```

No module may silently reinterpret 44.1 kHz samples as 16 kHz samples.

## 3. Baseline contract

The baseline requires **60.0 seconds** of valid captured audio.

Canonical startup progression:

```text
STOPPED
  ↓
STARTING_AUDIO
  ↓
AUDIO_READY
  ↓
BASELINE_RESET
  ↓
BASELINING
  ↓
MONITORING
```

Baseline progress is measured in seconds and must be monotonic during a healthy session.

Two explicit stall conditions exist:

1. **Capture stall:** no increase in captured PCM frames for 1,500 ms while monitoring is supposed to be running.
2. **Baseline stall:** PCM frames continue to arrive, but baseline progress does not advance for 3,000 ms.

Both are fatal for that monitoring session because continuing would leave the UI appearing active while the signal pipeline is no longer making valid progress.

A fatal baseline/capture stall must stop monitoring safely. It must **not** terminate the Android process.

## 4. JNI contract

Both Kotlin native surfaces load exactly:

```text
libbciwhisper_whisper.so
```

The release ABI is currently:

```text
arm64-v8a
```

Native compilation uses C++17 and `c++_shared`.

Every Kotlin `external fun` must have exactly one JNI export whose argument order and JNI types agree with Kotlin.

Examples:

| Kotlin | JNI |
|---|---|
| `Unit` | `void` |
| `Boolean` | `jboolean` |
| `Int` | `jint` |
| `Long` | `jlong` |
| `String` | `jstring` |
| `ShortArray` | `jshortArray` |
| `FloatArray` | `jfloatArray` |
| `DoubleArray` | `jdoubleArray` |

A JNI mismatch is a release-blocking compatibility failure even if Gradle compilation succeeds.

## 5. OpenGL ES contract

The Android manifest requires OpenGL ES 2.0, the spectrum surface requests an ES2 context, and the renderer uses `GLES20`.

The renderer executes on the GL render thread. The Oboe callback must never wait for rendering.

When the Activity pauses or the monitor surface is detached, rendering must pause/release its active scheduling path.

## 6. Broadcast contract

Canonical public-broadcast identity:

```text
protocol     bci-broadcast-v1
stream       bciandroid-main
publisher    bciwhisper-android
input codec  pcm_s16le
input rate   44100 Hz
channels     1
output       mp3
bitrate      128 kbps
```

Android may not enter `LIVE` merely because a response contains the words `hello_ack`.

The response must parse structurally and must agree on:

- protocol,
- stream ID,
- input codec,
- output codec.

A mismatch is a protocol failure and the connection is rejected.

## 7. Live transcription contract

Android sends finalized rolling caption segments over the existing authenticated publisher WebSocket.

Required semantic fields:

- `sequence`: monotonic segment number;
- `startMs`: milliseconds from stream start;
- `endMs`: milliseconds from stream start;
- `speaker`: neutral best-effort label or null;
- `text`: caption text, maximum 1,000 characters;
- `decodeMs`: Whisper decode duration in milliseconds;
- `final`: whether the segment is final.

The gateway converts these messages into SSE `transcript` events.

Transcript traffic is lower priority than audio. Under network backpressure, caption messages are discarded before live PCM.

## 8. Robust error handling

Robust error handling is a runtime subsystem, not simply a try/catch statement.

### Core invariants

1. The robust error handler runs outside the real-time Oboe callback.
2. It performs a health pass every **250 ms** while monitoring.
3. It never performs heavy DSP or inference.
4. It preserves the **first ERROR/FATAL fault** as the session root cause.
5. It preserves the **most recent fault** separately.
6. Repeated identical faults are coalesced and counted.
7. The handler itself is enclosed by an exception boundary and is never allowed to terminate the process.
8. Fatal audio-path faults stop monitoring safely.
9. Live-transcription or network-broadcast errors do not terminate healthy local capture.
10. Recovery operations must be safe to execute more than once.

### Severity

**INFO** means an observation is informational and does not degrade runtime health.

**WARNING** means degradation occurred but the primary runtime path is allowed to continue. Examples include an Oboe xRun increment or a dropped lower-priority caption.

**ERROR** means a subsystem has failed, but the primary local audio path can still remain valid. Examples include live-caption inference failure or broadcast failure.

**FATAL** means a primary invariant has failed. Examples include native audio unexpectedly inactive, sample-rate mismatch, capture stall, baseline stall, or an uncaught exception at the capture-worker boundary.

### Stable error codes

#### `BCI_E_MONITOR_START`

Monitoring could not complete startup.

**Owner:** Android audio startup  
**Severity:** FATAL  
**Required behavior:** close native capture if necessary, leave diagnostics available, permit an explicit retry.

#### `BCI_E_CAPTURE_LOOP`

An exception crossed the operational boundary of the capture worker.

**Owner:** Android capture orchestration  
**Severity:** FATAL  
**Required behavior:** catch the exception before it can kill the process, preserve its type/message, stop monitoring safely.

This code specifically protects against the class of failure where pressing Start Baseline appears to freeze and then the application process closes.

#### `BCI_E_AUDIO_INACTIVE`

Kotlin considers monitoring active but the native Oboe engine reports no active stream.

**Severity:** FATAL.

#### `BCI_E_NATIVE_PROBE`

A JNI/native health probe itself failed.

**Severity:** FATAL because the native boundary can no longer be trusted.

#### `BCI_E_SAMPLE_RATE`

The native stream does not report the contractual 44.1 kHz rate.

**Severity:** FATAL. Mismatched-rate PCM must not be passed into downstream detector/publisher logic.

#### `BCI_E_CAPTURE_STALL`

Captured frame count failed to progress for 1,500 ms.

**Severity:** FATAL.

#### `BCI_E_BASELINE_STALL`

PCM is arriving but baseline progress has stopped for 3,000 ms.

**Severity:** FATAL.

#### `BCI_E_LIVE_TRANSCRIPTION`

The rolling caption subsystem failed.

**Severity:** ERROR, non-fatal to local/live audio.

#### `BCI_E_BROADCAST`

The publisher entered ERROR state.

**Severity:** ERROR, non-fatal to local monitoring.

#### `BCI_E_ERROR_HANDLER`

The robust error handler encountered an internal exception.

**Severity:** ERROR. The exception is caught internally; the handler may report degraded health but cannot propagate the exception into the process.

#### `BCI_W_AUDIO_XRUN`

The Oboe xRun counter increased.

**Severity:** WARNING.

#### `BCI_W_NATIVE_RING_DROP`

The native PCM ring dropped incoming frames.

**Severity:** WARNING.

#### `BCI_W_BROADCAST_AUDIO_DROP`

The network publisher dropped audio due to queue pressure.

**Severity:** WARNING.

#### `BCI_W_BROADCAST_TRANSCRIPT_DROP`

A lower-priority caption was discarded under network pressure.

**Severity:** WARNING.

## 9. Root cause vs. secondary symptoms

The first ERROR/FATAL observation is retained as `rootFault`.

Later failures are not allowed to overwrite that historical root cause. They are retained as `lastFault` and in bounded recent-fault history.

Example:

```text
BCI_E_AUDIO_INACTIVE        ← root
        ↓
BCI_E_CAPTURE_STALL         ← secondary
        ↓
BCI_E_BROADCAST             ← secondary consequence
```

The UI/logging layer should present the root cause first rather than implying that a downstream broadcast error caused the native-audio failure.

## 10. Units

Every boundary must use explicit units.

| Quantity | Unit |
|---|---|
| public timestamps | milliseconds |
| Whisper native segment offsets | 10 ms ticks |
| baseline duration | seconds |
| audio levels | dBFS |
| frequency | Hz |
| capture quantities | frames/samples as explicitly named |

Silent unit conversion is prohibited.

## 11. Compatibility and versioning

A new BCI Core Contract version is required when a change modifies the meaning of:

- a required protocol field,
- a field unit,
- the PCM representation,
- JNI method signatures,
- lifecycle/state semantics,
- error-code semantics,
- ownership of a real-time boundary.

Compatible implementation changes may remain on `bci-core-v1` when semantics remain unchanged.

## 12. CI requirements

A release is blocked unless CI verifies:

1. Kotlin `external` declarations and JNI exports agree.
2. Required native libraries exist in the final APK.
3. ELF dependencies resolve to libraries packaged for the release ABI.
4. 44.1 kHz PCM16 mono audio contracts agree across Kotlin/C++/gateway.
5. Whisper crosses the explicit 16 kHz resampling boundary.
6. OpenGL manifest/context/API versions agree.
7. Android and gateway broadcast identities/schema agree.
8. Gateway CI and production Docker use the same Bun runtime.
9. The robust error handler remains present.
10. The capture loop retains an outer exception boundary.

The objective is not merely a successful compile. The objective is a demonstrably compatible runtime.
