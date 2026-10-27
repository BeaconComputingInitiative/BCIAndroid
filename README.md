<!-- BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026 | Chance Joaquin Boone -->
<!-- SHA-256: see docs/HYGIENE_SHA256SUMS from the repository root -->

# BCIAndroid

Native Android audio monitoring, retained recordings, offline transcription and authenticated live publishing. This is the canonical mobile repository; the desktop project is [BCIWhisper](https://github.com/BeaconComputingInitiative/BCIWhisper).

**Developer:** Chance Joaquin Boone · **Developer Seal:** CJB-DEV-2026  
**Current source:** `6.0-android-alpha20` · ARM64 · Android 9 / API 28 minimum

## Download and status

[Alpha 20 debug APK and SHA-256](https://github.com/BeaconComputingInitiative/BCIAndroid/releases/tag/android-v6.0-alpha20)

Alpha 20 is an experimental debug build. GitHub currently marks its release as a regular release; that label does not establish production or physical-device acceptance. Alpha 15 and 16 remain historical downloads. Alpha 17–19 have tags without published release entries.

## Current architecture

| Component | Ownership |
| --- | --- |
| C++ / Oboe | PCM16 capture callback and native ring; no Kotlin work on the callback |
| Kotlin foreground service | Capture supervision, baseline, spectral lock, retained WAV recording and runtime health |
| OpenGL ES 2 | Spectrum / waterfall rendering |
| JNI / whisper.cpp | Persistent offline decoder and 16 kHz inference conversion |
| Kotlin transcription workers | Manual transcription, rolling live captions and overlap deduplication |
| `gateway/` | Bun / TypeScript WSS publisher, FFmpeg MP3 output and public player |
| `contracts/` | Machine-readable and human-readable cross-language contracts |

The capture contract is 44.1 kHz mono PCM16. RAW audio remains separate from reconstructed/mastered derivatives. Recording retention requires 15 seconds of confirmed lock. Live operation is operator initiated and requires a publisher credential.

## Build from source

The repository has no Gradle wrapper. Use the toolchain pinned by [Android CI](.github/workflows/build-android.yml): JDK 17, Gradle 9.5.0, SDK 36, Build Tools 36.0.0, NDK 25.2.9519653 and CMake 3.22.1.

```bash
git submodule update --init --recursive
gradle :app:testDebugUnitTest :app:assembleDebug
```

Before assembling, provide the speech model as described in the CI model-download step (`app/src/main/assets/models/ggml-tiny.en.bin`). CI provisions it separately. Output: `app/build/outputs/apk/debug/app-debug.apk`. Release signing uses the separate signing workflow and environment-provided credentials.

## Documentation and verification

- [Core contract](contracts/BCI_CORE_CONTRACT_V1.md)
- [Gateway setup and tests](gateway/README.md)
- [Historical Alpha 16 notes](docs/history/alpha16-guide.md)
- [Commit audit and consolidation decisions](docs/PORTFOLIO_AUDIT.md)
- [Maintenance rules](docs/REPOSITORY_HYGIENE.md)

Physical microphone/Bluetooth lifecycle, mobile-network streaming, thermal behavior and signing identity still need device-level evidence. Mobile mastering does not establish desktop -14 LUFS / -1 dBTP parity. Release if and only if the exact source revision passes its documented acceptance gates.
